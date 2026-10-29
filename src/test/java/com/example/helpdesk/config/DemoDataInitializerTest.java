package com.example.helpdesk.config;

import com.example.helpdesk.model.Category;
import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.repo.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles({"test", "demo"})
class DemoDataInitializerTest {
    private static final String DEMO_TICKET_TITLE = "Demo: Laptop cannot connect to Wi-Fi";

    @Autowired
    private TicketRepository tickets;

    @Autowired
    private ApplicationContext context;

    @Test
    void seedsOnePredictableUnassignedTicketAndDoesNotDuplicateIt() throws Exception {
        assertEquals(1, tickets.count());
        Ticket seeded = tickets.findAll().get(0);
        assertEquals(DEMO_TICKET_TITLE, seeded.getTitle());
        assertEquals("employee", seeded.getReporter().getUsername());
        assertEquals(Category.NETWORK, seeded.getCategory());
        assertEquals(Priority.MEDIUM, seeded.getPriority());
        assertEquals(TicketStatus.OPEN, seeded.getStatus());
        assertNull(seeded.getTechnician());
        assertNotNull(seeded.getSlaDueAt());

        context.getBean("seedDemoAccounts", CommandLineRunner.class).run();

        assertEquals(1, tickets.count());
        assertEquals(DEMO_TICKET_TITLE, tickets.findAll().get(0).getTitle());
    }

    @Test
    void doesNotRegisterDemoSeederWhenProductionProfileIsAlsoActive() {
        new ApplicationContextRunner()
                .withUserConfiguration(DataInitializer.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", "demo"))
                .run(context -> assertFalse(context.containsBean("seedDemoAccounts")));
    }
}
