package com.civsetu;

import org.springframework.boot.SpringApplication;

import com.civsetu.support.TestcontainersConfiguration;

/**
 * Runs the app against a throwaway PostGIS container instead of compose.yaml.
 */
public class TestCivSetuApplication {

    public static void main(String[] args) {
        SpringApplication.from(CivSetuApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("test")
                .run(args);
    }

}
