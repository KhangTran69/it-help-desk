package com.example.helpdesk.config;

import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("demo")
public class DataInitializer {
    @Bean
    CommandLineRunner seedDemoAccounts(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            if (users.findByUsername("employee").isEmpty()) {
                users.save(new User("employee", encoder.encode("employee123"), "Demo Employee", Role.EMPLOYEE));
            }
            if (users.findByUsername("technician").isEmpty()) {
                users.save(new User("technician", encoder.encode("technician123"), "Demo Technician", Role.TECHNICIAN));
            }
        };
    }
}
