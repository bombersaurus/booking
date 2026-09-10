package com.nahid.booking;

import com.nahid.booking.booking.BookingService;
import com.nahid.booking.booking.CreateBookingRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingApiIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired BookingService bookingService;
    @Autowired PlatformTransactionManager transactionManager;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void resetFixtures() {
        // This connection belongs only to the disposable test container.
        jdbc.execute("""
                TRUNCATE ledger_entries, credit_transactions, credit_accounts, bookings, class_sessions, users
                RESTART IDENTITY CASCADE""");
        jdbc.update("INSERT INTO users (email) VALUES ('alice@example.com'), ('bob@example.com'), ('carol@example.com')");
        jdbc.update("""
                INSERT INTO class_sessions (name, starts_at, capacity, credit_cost) VALUES
                ('Spin', now() + interval '2 days', 10, 1),
                ('Yoga', now() + interval '3 days', 20, 1),
                ('HIIT', now() + interval '4 days', 8, 2)
                """);
        // Mirror migration V3: system accounts, one account per member and a 5 credit grant each.
        jdbc.update("INSERT INTO credit_accounts (kind) VALUES ('ISSUED'), ('RESERVED')");
        jdbc.update("INSERT INTO credit_accounts (user_id, kind) SELECT id, 'MEMBER' FROM users ORDER BY id");
        Long issued = jdbc.queryForObject("SELECT id FROM credit_accounts WHERE kind = 'ISSUED'", Long.class);
        for (Long member : jdbc.queryForList("SELECT id FROM credit_accounts WHERE kind = 'MEMBER' ORDER BY id", Long.class)) {
            Long grant = jdbc.queryForObject("INSERT INTO credit_transactions (kind) VALUES ('GRANT') RETURNING id", Long.class);
            jdbc.update("INSERT INTO ledger_entries (transaction_id, account_id, amount) VALUES (?, ?, -5), (?, ?, 5)",
                    grant, issued, grant, member);
            jdbc.update("UPDATE credit_accounts SET balance = balance - 5 WHERE id = ?", issued);
            jdbc.update("UPDATE credit_accounts SET balance = balance + 5 WHERE id = ?", member);
        }
    }

    @AfterEach
    void ledgerStaysConsistent() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT transaction_id FROM ledger_entries
                GROUP BY transaction_id HAVING sum(amount) <> 0) unbalanced""", Long.class))
                .as("transactions whose entries do not sum to zero").isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM credit_accounts a WHERE a.balance <>
                COALESCE((SELECT sum(e.amount) FROM ledger_entries e WHERE e.account_id = a.id), 0)""", Long.class))
                .as("accounts whose stored balance differs from their ledger entries").isZero();
    }

    @Test
    void listsClassesAndDemoUsers() throws Exception {
        HttpResponse<String> response = request("GET", "/api/v1/classes", null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode classes = json.readTree(response.body());
        assertThat(classes.size()).isEqualTo(3);
        assertThat(classes.get(0).get("name").asText()).isEqualTo("Spin");
        assertThat(classes.get(2).get("creditCost").asLong()).isEqualTo(2);
        assertThat(json.readTree(request("GET", "/api/v1/users", null).body()).size()).isEqualTo(3);
    }

    @Test
    void createsValidatedClassAndPersistsIt() throws Exception {
        HttpResponse<String> response = request("POST", "/api/v1/classes",
                classRequest("  Pilates  ", Instant.now().plus(7, ChronoUnit.DAYS), 2, 3));
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode created = json.readTree(response.body());
        assertThat(created.get("name").asText()).isEqualTo("Pilates");
        assertThat(response.headers().firstValue("Location")).hasValue("/api/v1/classes/" + created.get("id").asLong());
        assertThat(jdbc.queryForObject("SELECT capacity FROM class_sessions WHERE id = ?", Integer.class,
                created.get("id").asLong())).isEqualTo(2);
    }

    @Test
    void rejectsInvalidClassFieldsWithoutWriting() throws Exception {
        HttpResponse<String> response = request("POST", "/api/v1/classes",
                classRequest(" ", Instant.now().minus(1, ChronoUnit.DAYS), 0, -1));
        assertProblem(response, 400);
        assertThat(json.readTree(response.body()).get("errors").size()).isEqualTo(4);
        assertThat(count("class_sessions")).isEqualTo(3);
        assertProblem(request("POST", "/api/v1/classes", "{}"), 400);
    }

    @Test
    void booksCancelsAndRebooksThroughHttp() throws Exception {
        HttpResponse<String> created = book(1, 1);
        assertThat(created.statusCode()).isEqualTo(201);
        long id = json.readTree(created.body()).get("id").asLong();
        assertThat(json.readTree(created.body()).get("status").asText()).isEqualTo("CONFIRMED");
        assertProblem(book(1, 1), 409);
        assertThat(count("bookings")).isEqualTo(1);
        assertThat(request("DELETE", "/api/v1/bookings/" + id, null).statusCode()).isEqualTo(204);
        String cancelledAt = jdbc.queryForObject("SELECT cancelled_at::text FROM bookings WHERE id = ?", String.class, id);
        assertThat(cancelledAt).isNotBlank();
        assertThat(request("DELETE", "/api/v1/bookings/" + id, null).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT cancelled_at::text FROM bookings WHERE id = ?", String.class, id))
                .isEqualTo(cancelledAt);
        assertThat(book(1, 1).statusCode()).isEqualTo(201);
        JsonNode history = json.readTree(request("GET", "/api/v1/users/1/bookings", null).body());
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.get(0).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(history.get(1).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void rejectsThirdBookingForTwoSeatsAndReusesCancelledSeat() throws Exception {
        jdbc.update("UPDATE class_sessions SET capacity = 2 WHERE id = 1");
        HttpResponse<String> first = book(1, 1);
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(book(2, 1).statusCode()).isEqualTo(201);
        assertProblem(book(3, 1), 409);
        assertThat(count("bookings")).isEqualTo(2);
        long id = json.readTree(first.body()).get("id").asLong();
        assertThat(request("DELETE", "/api/v1/bookings/" + id, null).statusCode()).isEqualTo(204);
        assertThat(book(3, 1).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings WHERE status = 'CONFIRMED'", Long.class))
                .isEqualTo(2);
    }

    @Test
    void rejectsMissingUserSessionAndBooking() throws Exception {
        assertProblem(book(999, 1), 404);
        assertProblem(book(1, 999), 404);
        assertProblem(request("DELETE", "/api/v1/bookings/999", null), 404);
        assertProblem(request("GET", "/api/v1/users/999/bookings", null), 404);
        assertThat(count("bookings")).isZero();
    }

    @Test
    void rejectsInvalidIdsAndMalformedBodies() throws Exception {
        assertProblem(book(0, 1), 400);
        assertProblem(request("POST", "/api/v1/bookings", "{}"), 400);
        assertProblem(request("POST", "/api/v1/bookings", "{broken"), 400);
        assertProblem(request("POST", "/api/v1/bookings", "{\"userId\":\"abc\",\"classSessionId\":1}"), 400);
        assertProblem(request("DELETE", "/api/v1/bookings/-1", null), 400);
        assertProblem(request("GET", "/api/v1/users/0/bookings", null), 400);
        assertProblem(request("GET", "/api/v1/users/abc/bookings", null), 400);
        assertProblem(request("DELETE", "/api/v1/bookings/abc", null), 400);
        assertProblem(request("GET", "/api/v1/classes/abc", null), 400);
        assertThat(count("bookings")).isZero();
    }

    @Test
    void frameworkErrorsUseProblemDetails() throws Exception {
        assertProblem(request("GET", "/api/v1/nothing", null), 404);
        assertProblem(request("PUT", "/api/v1/classes", "{}"), 405);
        assertProblem(send("POST", "/api/v1/bookings", "x", "text/plain"), 415);
        assertThat(count("bookings")).isZero();
    }

    @Test
    void locationHeadersPointToReadableResources() throws Exception {
        HttpResponse<String> createdClass = request("POST", "/api/v1/classes",
                classRequest("Barre", Instant.now().plus(5, ChronoUnit.DAYS), 4, 1));
        HttpResponse<String> fetchedClass = request("GET", createdClass.headers().firstValue("Location").orElseThrow(), null);
        assertThat(fetchedClass.statusCode()).isEqualTo(200);
        assertThat(json.readTree(fetchedClass.body())).isEqualTo(json.readTree(createdClass.body()));

        HttpResponse<String> createdBooking = book(1, json.readTree(createdClass.body()).get("id").asLong());
        String bookingPath = createdBooking.headers().firstValue("Location").orElseThrow();
        HttpResponse<String> fetchedBooking = request("GET", bookingPath, null);
        assertThat(fetchedBooking.statusCode()).isEqualTo(200);
        assertThat(json.readTree(fetchedBooking.body())).isEqualTo(json.readTree(createdBooking.body()));

        assertThat(request("DELETE", bookingPath, null).statusCode()).isEqualTo(204);
        assertThat(json.readTree(request("GET", bookingPath, null).body()).get("status").asText()).isEqualTo("CANCELLED");
        assertProblem(request("GET", "/api/v1/classes/999", null), 404);
        assertProblem(request("GET", "/api/v1/bookings/999", null), 404);
    }

    @Test
    void rejectsPastAndCancelledSessions() throws Exception {
        jdbc.update("UPDATE class_sessions SET starts_at = now() - interval '1 minute' WHERE id = 1");
        assertProblem(book(1, 1), 409);
        jdbc.update("UPDATE class_sessions SET cancelled_at = now() WHERE id = 2");
        assertProblem(book(1, 2), 409);
        assertThat(count("bookings")).isZero();
    }

    @Test
    void databaseConstraintRejectsDuplicateEvenWithoutServiceCheck() throws Exception {
        assertThat(book(1, 1).statusCode()).isEqualTo(201);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO bookings (user_id, class_session_id, status) VALUES (1, 1, 'CONFIRMED')
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("bookings")).isEqualTo(1);
    }

    @Test
    void unexpectedFailureRollsBackBookingAndCredits() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            bookingService.create(new CreateBookingRequest(1L, 1L));
            throw new IllegalStateException("Simulated failure after insert");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Simulated failure after insert");
        assertThat(count("bookings")).isZero();
        assertThat(balance(1)).isEqualTo(5);
        assertThat(reservedBalance()).isZero();
        assertThat(count("ledger_entries")).isEqualTo(6);
    }

    @Test
    void bookingReservesCreditsAndCancellationRefundsThemOnce() throws Exception {
        HttpResponse<String> created = book(1, 3);
        assertThat(created.statusCode()).isEqualTo(201);
        long bookingId = json.readTree(created.body()).get("id").asLong();
        assertThat(balance(1)).isEqualTo(3);
        assertThat(reservedBalance()).isEqualTo(2);

        JsonNode statement = json.readTree(request("GET", "/api/v1/users/1/credits", null).body());
        assertThat(statement.get("balance").asLong()).isEqualTo(3);
        JsonNode reservation = statement.get("entries").get(0);
        assertThat(reservation.get("kind").asText()).isEqualTo("RESERVATION");
        assertThat(reservation.get("amount").asLong()).isEqualTo(-2);
        assertThat(reservation.get("bookingId").asLong()).isEqualTo(bookingId);
        assertThat(statement.get("entries").get(1).get("kind").asText()).isEqualTo("GRANT");

        assertThat(request("DELETE", "/api/v1/bookings/" + bookingId, null).statusCode()).isEqualTo(204);
        assertThat(request("DELETE", "/api/v1/bookings/" + bookingId, null).statusCode()).isEqualTo(204);
        assertThat(balance(1)).isEqualTo(5);
        assertThat(reservedBalance()).isZero();
        JsonNode refund = json.readTree(request("GET", "/api/v1/users/1/credits", null).body()).get("entries").get(0);
        assertThat(refund.get("kind").asText()).isEqualTo("REFUND");
        assertThat(refund.get("amount").asLong()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_transactions WHERE kind = 'REFUND'", Long.class))
                .isEqualTo(1);
        assertThat(balance(2)).isEqualTo(5);
    }

    @Test
    void refusesBookingWithoutEnoughCreditsAndLeavesNothingBehind() throws Exception {
        long expensive = json.readTree(request("POST", "/api/v1/classes",
                classRequest("Masterclass", Instant.now().plus(6, ChronoUnit.DAYS), 5, 6)).body()).get("id").asLong();
        assertProblem(book(1, expensive), 409);
        assertThat(count("bookings")).isZero();
        assertThat(count("ledger_entries")).isEqualTo(6);
        assertThat(balance(1)).isEqualTo(5);

        HttpResponse<String> topUp = request("POST", "/api/v1/users/1/credits", "{\"amount\":1}");
        assertThat(topUp.statusCode()).isEqualTo(200);
        assertThat(json.readTree(topUp.body()).get("balance").asLong()).isEqualTo(6);
        assertThat(json.readTree(topUp.body()).get("entries").get(0).get("kind").asText()).isEqualTo("TOP_UP");
        assertThat(book(1, expensive).statusCode()).isEqualTo(201);
        assertThat(balance(1)).isZero();
        assertProblem(book(1, 1), 409);
        assertThat(count("bookings")).isEqualTo(1);
    }

    @Test
    void rejectsInvalidTopUpsAndUnknownMembers() throws Exception {
        assertProblem(request("POST", "/api/v1/users/1/credits", "{\"amount\":0}"), 400);
        assertProblem(request("POST", "/api/v1/users/1/credits", "{\"amount\":101}"), 400);
        assertProblem(request("POST", "/api/v1/users/1/credits", "{}"), 400);
        assertProblem(request("POST", "/api/v1/users/999/credits", "{\"amount\":1}"), 404);
        assertProblem(request("GET", "/api/v1/users/999/credits", null), 404);
        assertProblem(request("GET", "/api/v1/users/abc/credits", null), 400);
        assertThat(balance(1)).isEqualTo(5);
        assertThat(count("ledger_entries")).isEqualTo(6);
    }

    @Test
    void cancellingBookingMadeBeforeCreditsRefundsNothing() throws Exception {
        Long id = jdbc.queryForObject("""
                INSERT INTO bookings (user_id, class_session_id, status) VALUES (1, 1, 'CONFIRMED') RETURNING id
                """, Long.class);
        assertThat(request("DELETE", "/api/v1/bookings/" + id, null).statusCode()).isEqualTo(204);
        assertThat(balance(1)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_transactions WHERE kind = 'REFUND'", Long.class))
                .isZero();
    }

    @Test
    void ledgerHistoryCannotBeChanged() {
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount = 50 WHERE id = 1"))
                .hasStackTraceContaining("cannot be updated or deleted");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE id = 1"))
                .hasStackTraceContaining("cannot be updated or deleted");
        assertThatThrownBy(() -> jdbc.update("UPDATE credit_transactions SET kind = 'TOP_UP' WHERE id = 1"))
                .hasStackTraceContaining("cannot be updated or deleted");
        assertThat(count("ledger_entries")).isEqualTo(6);
    }

    @Test
    void unbalancedLedgerTransactionIsRejectedAtCommit() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            Long id = jdbc.queryForObject("INSERT INTO credit_transactions (kind) VALUES ('TOP_UP') RETURNING id", Long.class);
            jdbc.update("""
                    INSERT INTO ledger_entries (transaction_id, account_id, amount)
                    VALUES (?, (SELECT id FROM credit_accounts WHERE user_id = 1), 50)""", id);
        })).hasStackTraceContaining("does not balance");
        assertThat(count("credit_transactions")).isEqualTo(3);
        assertThat(count("ledger_entries")).isEqualTo(6);
    }

    @Test
    void servesSwaggerAndOpenApi() throws Exception {
        HttpResponse<String> document = request("GET", "/v3/api-docs", null);
        assertThat(document.statusCode()).isEqualTo(200);
        JsonNode paths = json.readTree(document.body()).get("paths");
        assertThat(paths.has("/api/v1/classes")).isTrue();
        assertThat(paths.has("/api/v1/bookings")).isTrue();
        assertThat(paths.has("/api/v1/bookings/{id}")).isTrue();
        assertThat(paths.has("/api/v1/classes/{id}")).isTrue();
        assertThat(paths.has("/api/v1/users/{userId}/credits")).isTrue();
        HttpResponse<String> swagger = request("GET", "/swagger-ui/index.html", null);
        assertThat(swagger.statusCode()).isEqualTo(200);
        assertThat(swagger.body()).contains("Ledger | Booking workspace", "/docs/ledger.css");
    }

    private HttpResponse<String> book(long userId, long sessionId) throws Exception {
        return request("POST", "/api/v1/bookings",
                "{\"userId\":" + userId + ",\"classSessionId\":" + sessionId + "}");
    }

    private String classRequest(String name, Instant startsAt, int capacity, long cost) {
        return "{\"name\":\"" + name + "\",\"startsAt\":\"" + startsAt
                + "\",\"capacity\":" + capacity + ",\"creditCost\":" + cost + "}";
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return send(method, path, body, "application/json");
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", contentType);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertProblem(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(expectedStatus);
        assertThat(problem.has("detail")).isTrue();
        assertThat(problem.has("trace")).isFalse();
        assertThat(response.body()).doesNotContain("org.hibernate", "java.lang", "INSERT INTO");
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private long balance(long userId) {
        return jdbc.queryForObject("SELECT balance FROM credit_accounts WHERE user_id = ?", Long.class, userId);
    }

    private long reservedBalance() {
        return jdbc.queryForObject("SELECT balance FROM credit_accounts WHERE kind = 'RESERVED'", Long.class);
    }
}
