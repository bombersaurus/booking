package com.nahid.booking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Releases many HTTP requests at the same instant, then inspects the database.
 * A race does not appear on every attempt, so each scenario repeats for several
 * rounds against a freshly reset, disposable database and reports what it saw.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyIntegrationTest {
    // Override for a longer soak, for example -Drace.rounds=100.
    static final int ROUNDS = Integer.getInteger("race.rounds", 20);

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
    final HttpClient http = HttpClient.newHttpClient();
    ExecutorService pool;

    @BeforeEach
    void startPool() { pool = Executors.newCachedThreadPool(); }

    @AfterEach
    void stopPool() { pool.shutdownNow(); }

    @Test
    void capacityIsNeverExceeded() throws Exception {
        RaceReport report = new RaceReport("Capacity: 25 members book one 5 seat class at once");
        for (int round = 1; round <= ROUNDS; round++) {
            TestDatabase.reset(jdbc);
            long session = TestDatabase.addClass(jdbc, "Spin", 5, 1);
            List<HttpRequest> requests = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                requests.add(book(TestDatabase.addMember(jdbc, "member" + i + "@example.com", 5), session));
            }
            report.record(round, race(requests));
            long confirmed = count("SELECT count(*) FROM bookings WHERE class_session_id = ? AND status = 'CONFIRMED'", session);
            report.violationIf(round, confirmed > 5, confirmed + " bookings confirmed for 5 seats");
            report.checkLedger(round, jdbc);
        }
        report.printAndAssertClean();
    }

    @Test
    void creditsAreNeverOverspent() throws Exception {
        RaceReport report = new RaceReport("Double spend: one member with 5 credits books 15 one credit classes at once");
        for (int round = 1; round <= ROUNDS; round++) {
            TestDatabase.reset(jdbc);
            long member = TestDatabase.addMember(jdbc, "member@example.com", 5);
            List<HttpRequest> requests = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                requests.add(book(member, TestDatabase.addClass(jdbc, "Class " + i, 10, 1)));
            }
            report.record(round, race(requests));
            long confirmed = count("SELECT count(*) FROM bookings WHERE user_id = ? AND status = 'CONFIRMED'", member);
            report.violationIf(round, confirmed > 5, confirmed + " bookings paid for with 5 credits");
            report.checkLedger(round, jdbc);
        }
        report.printAndAssertClean();
    }

    @Test
    void aBookingIsRefundedOnlyOnce() throws Exception {
        RaceReport report = new RaceReport("Double refund: 10 cancellations of one booking at once");
        for (int round = 1; round <= ROUNDS; round++) {
            TestDatabase.reset(jdbc);
            long member = TestDatabase.addMember(jdbc, "member@example.com", 5);
            HttpResponse<String> created = http.send(book(member, TestDatabase.addClass(jdbc, "Yoga", 10, 2)),
                    HttpResponse.BodyHandlers.ofString());
            long booking = json.readTree(created.body()).get("id").asLong();
            HttpRequest cancel = request("/api/v1/bookings/" + booking).DELETE().build();
            report.record(round, race(Collections.nCopies(10, cancel)));
            long refunds = count("SELECT count(*) FROM credit_transactions WHERE booking_id = ? AND kind = 'REFUND'", booking);
            report.violationIf(round, refunds != 1, refunds + " refunds for one booking");
            report.checkLedger(round, jdbc);
        }
        report.printAndAssertClean();
    }

    /** Starts every request together behind a gate and returns each status and latency. */
    private Round race(List<HttpRequest> requests) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Response>> futures = new ArrayList<>();
        for (HttpRequest request : requests) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                gate.await();
                long start = System.nanoTime();
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                return new Response(response.statusCode(), System.nanoTime() - start);
            }));
        }
        ready.await();
        long start = System.nanoTime();
        gate.countDown();
        List<Response> responses = new ArrayList<>();
        for (Future<Response> future : futures) {
            responses.add(future.get(60, TimeUnit.SECONDS));
        }
        return new Round(responses, System.nanoTime() - start);
    }

    private HttpRequest book(long userId, long sessionId) {
        return request("/api/v1/bookings").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"userId\":" + userId + ",\"classSessionId\":" + sessionId + "}"))
                .build();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    record Response(int status, long nanos) {}

    record Round(List<Response> responses, long wallNanos) {}

    /** Collects outcomes across rounds so one run gives comparable before and after numbers. */
    static final class RaceReport {
        private final String scenario;
        private final Map<Integer, Integer> statuses = new TreeMap<>();
        private final List<Long> latencies = new ArrayList<>();
        private final Set<Integer> violatingRounds = new TreeSet<>();
        private final List<String> examples = new ArrayList<>();
        private long wallNanos;
        private int rounds;

        RaceReport(String scenario) { this.scenario = scenario; }

        void record(int round, Round result) {
            rounds++;
            wallNanos += result.wallNanos();
            for (Response response : result.responses()) {
                statuses.merge(response.status(), 1, Integer::sum);
                latencies.add(response.nanos());
                violationIf(round, response.status() >= 500, "server error " + response.status());
            }
        }

        void violationIf(int round, boolean violated, String description) {
            if (!violated) {
                return;
            }
            violatingRounds.add(round);
            String example = "round " + round + ": " + description;
            if (examples.size() < 6 && !examples.contains(example)) {
                examples.add(example);
            }
        }

        void checkLedger(int round, JdbcTemplate jdbc) {
            long drifted = TestDatabase.driftedAccounts(jdbc);
            violationIf(round, drifted > 0, drifted + " account balances differ from the ledger (lost update)");
            long unbalanced = TestDatabase.unbalancedTransactions(jdbc);
            violationIf(round, unbalanced > 0, unbalanced + " unbalanced ledger transactions");
        }

        void printAndAssertClean() {
            List<Long> sorted = latencies.stream().sorted().toList();
            double seconds = wallNanos / 1e9;
            System.out.printf("%n[race] %s%n", scenario);
            System.out.printf("[race]   rounds with a violation: %d of %d%n", violatingRounds.size(), rounds);
            System.out.printf("[race]   responses: %s%n", statuses);
            System.out.printf("[race]   latency ms: p50 %.1f, p95 %.1f, max %.1f; throughput %.0f requests/s%n",
                    percentile(sorted, 50), percentile(sorted, 95), percentile(sorted, 100), sorted.size() / seconds);
            examples.forEach(example -> System.out.printf("[race]   %s%n", example));
            assertThat(examples).as(scenario).isEmpty();
        }

        private static double percentile(List<Long> sorted, int percent) {
            int index = Math.max(0, (int) Math.ceil(percent / 100.0 * sorted.size()) - 1);
            return sorted.get(index) / 1e6;
        }
    }
}
