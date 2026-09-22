package com.nahid.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingApiTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void databaseSettings(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE bookings, class_sessions, users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users (email) VALUES ('alice@example.com'), ('bob@example.com'), ('carol@example.com')");
        jdbc.update("""
                INSERT INTO class_sessions (name, starts_at, capacity) VALUES
                ('Spin', now() + interval '2 days', 10),
                ('HIIT', now() + interval '3 days', 2)
                """);
    }

    @Test
    void listsClassesInStartOrder() throws Exception {
        HttpResponse<String> response = get("/api/v1/classes");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode classes = json.readTree(response.body());
        assertThat(classes.size()).isEqualTo(2);
        assertThat(classes.get(0).get("name").asText()).isEqualTo("Spin");
    }

    @Test
    void booksCancelsAndBooksAgain() throws Exception {
        HttpResponse<String> booked = book(1, 1);
        assertThat(booked.statusCode()).isEqualTo(201);
        long bookingId = json.readTree(booked.body()).get("id").asLong();

        assertThat(book(1, 1).statusCode()).isEqualTo(409);

        assertThat(cancel(bookingId).statusCode()).isEqualTo(204);
        assertThat(book(1, 1).statusCode()).isEqualTo(201);

        JsonNode history = json.readTree(get("/api/v1/users/1/bookings").body());
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.get(0).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(history.get(1).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void refusesBookingWhenClassIsFull() throws Exception {
        long firstBooking = json.readTree(book(1, 2).body()).get("id").asLong();
        assertThat(book(2, 2).statusCode()).isEqualTo(201);

        HttpResponse<String> third = book(3, 2);
        assertThat(third.statusCode()).isEqualTo(409);
        assertThat(third.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");

        cancel(firstBooking);
        assertThat(book(3, 2).statusCode()).isEqualTo(201);
    }

    @Test
    void refusesBookingForClassThatHasStarted() throws Exception {
        jdbc.update("UPDATE class_sessions SET starts_at = now() - interval '1 minute' WHERE id = 1");

        assertThat(book(1, 1).statusCode()).isEqualTo(409);
        assertThat(bookingCount()).isZero();
    }

    @Test
    void returnsNotFoundForMissingRecords() throws Exception {
        assertThat(book(99, 1).statusCode()).isEqualTo(404);
        assertThat(book(1, 99).statusCode()).isEqualTo(404);
        assertThat(cancel(99).statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/users/99/bookings").statusCode()).isEqualTo(404);
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        assertThat(post("/api/v1/bookings", "{}").statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/bookings", "{not json").statusCode()).isEqualTo(400);
        assertThat(book(0, 1).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/users/abc/bookings").statusCode()).isEqualTo(400);
        assertThat(bookingCount()).isZero();
    }

    @Test
    void databaseAlsoBlocksDuplicateBookings() throws Exception {
        book(1, 1);

        // Skips the service and inserts straight into the table.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO bookings (user_id, class_session_id, status) VALUES (1, 1, 'CONFIRMED')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private HttpResponse<String> book(long userId, long classId) throws Exception {
        return post("/api/v1/bookings", "{\"userId\": " + userId + ", \"classSessionId\": " + classId + "}");
    }

    private HttpResponse<String> cancel(long bookingId) throws Exception {
        return send(HttpRequest.newBuilder(url("/api/v1/bookings/" + bookingId)).DELETE());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(url(path)).GET());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return send(HttpRequest.newBuilder(url(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI url(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private long bookingCount() {
        return jdbc.queryForObject("SELECT count(*) FROM bookings", Long.class);
    }
}
