package com.example.helpdesk;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class HelpdeskApplication {
    public static void main(String[] args) {
        SpringApplication.run(HelpdeskApplication.class, args);
    }
}
