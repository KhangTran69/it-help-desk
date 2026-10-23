package com.example.helpdesk.config;

import com.example.helpdesk.model.*;
import com.example.helpdesk.repo.AssetRepository;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.service.SlaPolicy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Configuration
@Profile("benchmark")
public class BenchmarkDataInitializer {
    @Bean
    CommandLineRunner seedBenchmarkData(UserRepository users, TicketRepository tickets, AssetRepository assets,
                                        PasswordEncoder encoder, SlaPolicy slaPolicy) {
        return args -> {
            if (tickets.count() > 0 || users.count() > 0) return;
            User employee = users.save(new User("bench-employee", encoder.encode("benchmark-employee-123"), "Benchmark Employee", Role.EMPLOYEE));
            User technician = users.save(new User("bench-technician", encoder.encode("benchmark-tech-123"), "Benchmark Technician", Role.TECHNICIAN));
            List<Ticket> ticketRows = new ArrayList<>(1000);
            for (int i = 0; i < 1000; i++) {
                Ticket ticket = new Ticket();
                ticket.setTitle("Benchmark request " + (i + 1));
                ticket.setDescription("Synthetic benchmark record for local pagination and filtering checks. Batch " + (i % 20));
                ticket.setCategory(Category.values()[i % Category.values().length]);
                ticket.setPriority(Priority.values()[i % Priority.values().length]);
                TicketStatus status = TicketStatus.values()[i % TicketStatus.values().length];
                ticket.setStatus(TicketStatus.OPEN);
                ticket.setReporter(employee);
                if (i % 3 != 0) ticket.setTechnician(technician);
                ticket.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(i % 90));
                ticket.setUpdatedAt(ticket.getCreatedAt());
                slaPolicy.start(ticket);
                if (status != TicketStatus.OPEN) {
                    slaPolicy.statusChanged(ticket, TicketStatus.OPEN, status);
                    ticket.setStatus(status);
                    if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) ticket.setResolutionNotes("Synthetic benchmark resolution");
                }
                ticketRows.add(ticket);
            }
            tickets.saveAll(ticketRows);
            List<Asset> assetRows = new ArrayList<>(250);
            for (int i = 0; i < 250; i++) {
                Asset asset = new Asset();
                asset.setAssetTag("BENCH-" + String.format("%04d", i + 1));
                asset.setName("Benchmark equipment " + (i + 1));
                asset.setCategory(List.of("Laptop", "Monitor", "Mobile", "Network").get(i % 4));
                asset.setStatus(i % 4 == 0 ? AssetStatus.ASSIGNED : AssetStatus.AVAILABLE);
                if (asset.getStatus() == AssetStatus.ASSIGNED) asset.setAssignedUser(employee);
                assetRows.add(asset);
            }
            assets.saveAll(assetRows);
        };
    }
}
