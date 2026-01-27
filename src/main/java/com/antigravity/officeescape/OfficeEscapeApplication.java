package com.antigravity.officeescape;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OfficeEscapeApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfficeEscapeApplication.class, args);
    }

}
