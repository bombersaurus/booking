package com.nahid.booking.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class AppConfiguration {
    // PostgreSQL stores microseconds, so ticking at that precision keeps
    // responses identical to what is later read back.
    @Bean
    Clock clock() { return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000)); }

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("Booking workspace").version("2.0")
                .description("Explore classes, reserve a place with credits and manage bookings. Choose an operation below to try it."));
    }
}
