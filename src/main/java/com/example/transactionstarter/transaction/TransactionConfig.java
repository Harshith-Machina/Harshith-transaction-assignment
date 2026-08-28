package com.example.transactionstarter.transaction;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the transaction feature: binds {@link TransactionProperties} and
 * exposes a {@link Clock} so time can be controlled in tests.
 */
@Configuration
@EnableConfigurationProperties(TransactionProperties.class)
public class TransactionConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
