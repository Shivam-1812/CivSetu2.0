package com.civsetu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CivSetuApplication {

    public static void main(String[] args) {
        SpringApplication.run(CivSetuApplication.class, args);
    }

}
