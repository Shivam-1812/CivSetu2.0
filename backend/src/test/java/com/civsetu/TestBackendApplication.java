package com.civsetu;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app against a throwaway PostGIS container instead of compose.yaml.
 */
public class TestBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(BackendApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("test")
                .run(args);
    }

}
