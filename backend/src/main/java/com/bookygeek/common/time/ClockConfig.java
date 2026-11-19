package com.bookygeek.common.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The single injectable time source (UTC). Business code asks this {@link Clock} for "now" so that delivery dates, the
 * 48-hour cancellation window and error timestamps can be tested with a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
