package com.example.helpdesk.config;

import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.Category;
import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.service.SlaPolicy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("demo & !prod")
public class DataInitializer {
    @Bean
    CommandLineRunner seedDemoAccounts(UserRepository users, TicketRepository tickets,
                                       PasswordEncoder encoder, SlaPolicy slaPolicy) {
        return args -> {
            if (users.findByUsername("employee").isEmpty()) {
                users.save(new User("employee", encoder.encode("employee123"), "Demo Employee", Role.EMPLOYEE));
            }
            if (users.findByUsername("technician").isEmpty()) {
                users.save(new User("technician", encoder.encode("technician123"), "Demo Technician", Role.TECHNICIAN));
            }
            if (!tickets.existsByTitle("Demo: Laptop cannot connect to Wi-Fi")) {
                Ticket ticket = new Ticket();
                ticket.setTitle("Demo: Laptop cannot connect to Wi-Fi");
                ticket.setDescription("The laptop cannot connect to the office Wi-Fi network.");
                ticket.setCategory(Category.NETWORK);
                ticket.setPriority(Priority.MEDIUM);
                ticket.setStatus(TicketStatus.OPEN);
                ticket.setReporter(users.findByUsername("employee").orElseThrow());
                slaPolicy.start(ticket);
                tickets.save(ticket);
            }
        };
    }
}
