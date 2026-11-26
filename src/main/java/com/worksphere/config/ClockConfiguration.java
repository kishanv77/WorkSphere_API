package com.worksphere.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** A shared UTC clock (replaceable in tests) and scheduled housekeeping jobs. */
@Configuration
@EnableScheduling
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
