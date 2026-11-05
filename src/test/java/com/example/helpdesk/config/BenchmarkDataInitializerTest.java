package com.example.helpdesk.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BenchmarkDataInitializerTest {

    @Test
    void doesNotRegisterBenchmarkSeederWhenProductionProfileIsAlsoActive() {
        new ApplicationContextRunner()
                .withUserConfiguration(BenchmarkDataInitializer.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", "benchmark"))
                .run(context -> assertFalse(context.containsBean("seedBenchmarkData")));
    }
}
