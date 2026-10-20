package com.example.helpdesk.integration;

import com.example.helpdesk.model.*;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
        assertEquals(3, jdbc.queryForObject("SELECT MAX(version) FROM flyway_schema_history WHERE success = TRUE", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tickets' AND column_name = 'version'", Integer.class) > 0);

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

        assertEquals(1, ticketService.count(TicketStatus.OPEN));
        assertTrue(Boolean.TRUE.equals(redis.hasKey("ticketCounts::OPEN")));
        ticketService.updateStatus(ticket.getId(), TicketStatus.IN_PROGRESS, null, technician.getUsername());
        assertEquals(0, ticketService.count(TicketStatus.OPEN));
        assertEquals(1, ticketService.count(TicketStatus.IN_PROGRESS));
    }
}
