package com.nahid.booking;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 25 users try to book a 5 place class at the same moment.
 * A race does not happen every time, so the test repeats it several times.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentBookingTest {

    static final int ROUNDS = 10;
    static final int USERS = 25;
    static final int PLACES = 5;

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

    HttpClient http = HttpClient.newHttpClient();

    @Test
    void classIsNeverOverbooked() throws Exception {
        List<String> problems = new ArrayList<>();

        for (int round = 1; round <= ROUNDS; round++) {
            long classId = setUpClassAndUsers();

            bookAllAtOnce(classId);

            long confirmed = jdbc.queryForObject(
                    "SELECT count(*) FROM bookings WHERE status = 'CONFIRMED'", Long.class);
            if (confirmed != PLACES) {
                problems.add("round " + round + ": " + confirmed + " bookings for " + PLACES + " places");
            }
        }

        assertThat(problems).as("rounds where the class was overbooked").isEmpty();
    }

    private long setUpClassAndUsers() {
        jdbc.execute("TRUNCATE bookings, class_sessions, users RESTART IDENTITY CASCADE");
        for (int i = 1; i <= USERS; i++) {
            jdbc.update("INSERT INTO users (email) VALUES (?)", "user" + i + "@example.com");
        }
        return jdbc.queryForObject("""
                INSERT INTO class_sessions (name, starts_at, capacity)
                VALUES ('Spin', now() + interval '1 day', ?) RETURNING id
                """, Long.class, PLACES);
    }

    private void bookAllAtOnce(long classId) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(USERS);
        CountDownLatch ready = new CountDownLatch(USERS);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<HttpResponse<Void>>> responses = new ArrayList<>();

        for (int userId = 1; userId <= USERS; userId++) {
            HttpRequest request = bookingRequest(userId, classId);
            responses.add(threads.submit(() -> {
                ready.countDown();
                startSignal.await(); // every thread waits here until the start signal
                return http.send(request, HttpResponse.BodyHandlers.discarding());
            }));
        }

        ready.await();
        startSignal.countDown(); // go

        for (Future<HttpResponse<Void>> response : responses) {
            int status = response.get().statusCode();
            assertThat(status).as("each request is either booked or refused as full").isIn(201, 409);
        }
        threads.shutdown();
    }

    private HttpRequest bookingRequest(long userId, long classId) {
        String body = "{\"userId\": " + userId + ", \"classSessionId\": " + classId + "}";
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/bookings"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
