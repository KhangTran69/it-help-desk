package com.example.helpdesk.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.model.Category;
import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TicketCsvExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TicketRepository ticketRepository;

    private User technician;
    private User employee;

    @BeforeEach
    void setUp() {
        employee = userRepository.save(
                new User("csv-employee", passwordEncoder.encode("password123"), "CSV, Employee", Role.EMPLOYEE));
        technician = userRepository.save(
                new User("csv-technician", passwordEncoder.encode("password123"), "CSV Tech", Role.TECHNICIAN));
        Ticket ticket = new Ticket();
        ticket.setTitle("CSV export probe, with comma");
        ticket.setDescription("Exercises CSV field escaping in the export.");
        ticket.setCategory(Category.OTHER);
        ticket.setPriority(Priority.LOW);
        ticket.setReporter(employee);
        ticketRepository.save(ticket);
    }

    @Test
    void technicianExportsTheQueueAsCsvWithEscapedFields() throws Exception {
        var response = mockMvc
                .perform(get("/tickets/export").with(user("csv-technician").roles("TECHNICIAN")))
                .andReturn()
                .getResponse();
        assertEquals(200, response.getStatus());
        String body = response.getContentAsString();

        assertTrue(
                body.startsWith(
                        "id,title,status,priority,category,reporter,technician,created_at,updated_at,sla_due_at,overdue\n"),
                "unexpected body: " + body.substring(0, Math.min(300, body.length())));
        assertTrue(
                body.contains("\"CSV, Employee\""),
                "Comma-containing names must be quoted-escaped: " + body.substring(0, Math.min(300, body.length())));
    }

    @Test
    void employeesCannotExportTheTechnicianQueue() throws Exception {
        int status = mockMvc
                .perform(get("/tickets/export").with(user("csv-employee").roles("EMPLOYEE")))
                .andReturn()
                .getResponse()
                .getStatus();
        assertEquals(403, status);
    }
}
