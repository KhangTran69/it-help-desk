package com.example.helpdesk.integration;

import com.example.helpdesk.model.*;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("testcontainers")
class MySqlRedisContainersTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8"))
            .withDatabaseName("helpdesk_test").withUsername("helpdesk").withPassword("helpdesk_test");
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired TicketRepository tickets;
    @Autowired TicketService ticketService;
    @Autowired StringRedisTemplate redis;

    @Test
    void migrationsAndRedisDashboardCacheWorkWithRealServices() {
        assertEquals(6, jdbc.queryForObject("SELECT MAX(version) FROM flyway_schema_history WHERE success = TRUE", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tickets' AND column_name = 'version'", Integer.class) > 0);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'assets' AND column_name = 'version'", Integer.class) > 0);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tickets' AND column_name = 'sla_due_at'", Integer.class) > 0);
        redis.delete(java.util.List.of("ticketCounts::OPEN", "ticketCounts::IN_PROGRESS"));
        long openBefore = tickets.countByStatus(TicketStatus.OPEN);
        long inProgressBefore = tickets.countByStatus(TicketStatus.IN_PROGRESS);

        String suffix = Long.toString(System.nanoTime());
        User employee = users.save(new User("container-emp-" + suffix, "hash", "Container Employee", Role.EMPLOYEE));
        User technician = users.save(new User("container-tech-" + suffix, "hash", "Container Technician", Role.TECHNICIAN));
        Ticket ticket = new Ticket();
        ticket.setTitle("Container cache check");
        ticket.setDescription("Check cache invalidation against Redis");
        ticket.setCategory(Category.OTHER);
        ticket.setPriority(Priority.MEDIUM);
        ticket.setReporter(employee);
        ticket.setStatus(TicketStatus.OPEN);
        ticket = tickets.save(ticket);

        assertEquals(openBefore + 1, ticketService.count(TicketStatus.OPEN));
        assertTrue(Boolean.TRUE.equals(redis.hasKey("ticketCounts::OPEN")));
        ticketService.updateStatus(ticket.getId(), TicketStatus.IN_PROGRESS, null, technician.getUsername());
        assertEquals(openBefore, ticketService.count(TicketStatus.OPEN));
        assertEquals(inProgressBefore + 1, ticketService.count(TicketStatus.IN_PROGRESS));
    }

    @Test
    void resolvedClosedFilterAndStatusNarrowingWorkWithMysql() {
        String suffix = Long.toString(System.nanoTime());
        User employee = users.save(new User("filter-emp-" + suffix, "hash", "Filter Employee", Role.EMPLOYEE));
        for (TicketStatus status : new TicketStatus[]{TicketStatus.RESOLVED, TicketStatus.CLOSED, TicketStatus.OPEN}) {
            Ticket ticket = new Ticket();
            ticket.setTitle("Filter group " + status + " " + suffix);
            ticket.setDescription("Verify dashboard group query on MySQL");
            ticket.setCategory(Category.OTHER);
            ticket.setPriority(Priority.LOW);
            ticket.setReporter(employee);
            ticket.setStatus(status);
            if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
                ticket.setResolutionNotes("Completed for MySQL filter coverage");
            }
            tickets.save(ticket);
        }

        var combined = tickets.pageTickets(employee, null, null, null, null, null, null, true, false,
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC), PageRequest.of(0, 10));
        assertEquals(2, combined.getTotalElements());
        assertTrue(combined.getContent().stream().allMatch(ticket ->
                ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED));

        var resolvedOnly = tickets.pageTickets(employee, TicketStatus.RESOLVED, null, null, null, null, null,
                true, false, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC), PageRequest.of(0, 10));
        assertEquals(1, resolvedOnly.getTotalElements());
        assertEquals(TicketStatus.RESOLVED, resolvedOnly.getContent().get(0).getStatus());

        var incompatibleStatus = tickets.pageTickets(employee, TicketStatus.OPEN, null, null, null, null, null,
                true, false, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC), PageRequest.of(0, 10));
        assertEquals(0, incompatibleStatus.getTotalElements());
    }
}
