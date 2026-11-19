package com.bookygeek;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BookygeekApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookygeekApplication.class, args);
    }
}
