package com.example.helpdesk.web;

import com.example.helpdesk.model.Category;
import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketAttachment;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.TicketAttachmentRepository;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.security.JwtUtil;
import jakarta.persistence.EntityManager;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:helpdesk_attachment_metadata;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TicketAttachmentMetadataIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private TicketRepository tickets;
    @Autowired private TicketAttachmentRepository attachments;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtUtil jwt;
    @Autowired private AttachmentSelects attachmentSelects;

    @Test
    void listingAttachmentsReadsMetadataWithoutSelectingContent() throws Exception {
        User reporter = users.save(new User("attachment-reporter", "unused", "Attachment Reporter", Role.EMPLOYEE));
        User technician = users.save(new User("attachment-tech", "unused", "Attachment Tech", Role.TECHNICIAN));
        Ticket ticket = new Ticket();
        ticket.setTitle("Attachment list fixture");
        ticket.setDescription("Check metadata for two synthetic files");
        ticket.setCategory(Category.HARDWARE);
        ticket.setPriority(Priority.LOW);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setReporter(reporter);
        ticket = tickets.save(ticket);

        byte[] firstFileBytes = {1, 2, 3};
        saveAttachment(ticket, technician, "later.pdf", LocalDateTime.of(2026, 1, 2, 8, 0), new byte[] {4});
        TicketAttachment first = saveAttachment(ticket, reporter, "earlier.pdf",
                LocalDateTime.of(2026, 1, 1, 8, 0), firstFileBytes);
        entityManager.flush();
        entityManager.clear();
        attachmentSelects.statements.clear();

        checkAttachmentList("/api/tickets/{id}/attachments", ticket, technician, first);
        checkAttachmentList("/api/tickets/my/{id}/attachments", ticket, reporter, first);
        String technicianPath = "/tickets/" + ticket.getId();
        String reporterPath = "/my-tickets/" + ticket.getId();
        String downloadSuffix = "/attachments/" + first.getId() + "/download";
        mockMvc.perform(get(technicianPath).with(user(technician.getUsername()).roles("TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(technicianPath + downloadSuffix)))
                .andExpect(content().string(containsString("application/pdf · 3 bytes · Attachment Reporter")));
        mockMvc.perform(get(reporterPath).with(user(reporter.getUsername()).roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(reporterPath + downloadSuffix)))
                .andExpect(content().string(containsString("earlier.pdf")));

        assertThat(attachmentSelects.statements).hasSize(4).allSatisfy(sql ->
                assertThat(sql).doesNotContainPattern("\\.content\\b"));

        mockMvc.perform(get(reporterPath + downloadSuffix).with(user(reporter.getUsername()).roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(firstFileBytes));

        MockMultipartFile upload = new MockMultipartFile("file", "upload.pdf", "application/pdf", firstFileBytes);
        mockMvc.perform(multipart("/api/tickets/{id}/attachments", ticket.getId()).file(upload)
                        .header("Authorization", "Bearer " + jwt.generateToken(technician.getUsername())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("upload.pdf"))
                .andExpect(jsonPath("$.uploadedBy").value("attachment-tech"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    private TicketAttachment saveAttachment(Ticket ticket, User uploader, String filename,
                                            LocalDateTime uploadedAt, byte[] body) {
        TicketAttachment attachment = new TicketAttachment(ticket, filename, "application/pdf", body.length, uploader, body);
        ReflectionTestUtils.setField(attachment, "uploadedAt", uploadedAt);
        return attachments.save(attachment);
    }

    private void checkAttachmentList(String url, Ticket ticket, User viewer, TicketAttachment first) throws Exception {
        mockMvc.perform(get(url, ticket.getId()).header("Authorization", "Bearer " + jwt.generateToken(viewer.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId().intValue()))
                .andExpect(jsonPath("$[0].ticketId").value(ticket.getId().intValue()))
                .andExpect(jsonPath("$[0].filename").value("earlier.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[0].sizeBytes").value(3))
                .andExpect(jsonPath("$[0].uploadedBy").value("attachment-reporter"))
                .andExpect(jsonPath("$[0].uploadedAt").value("2026-01-01T08:00:00Z"))
                .andExpect(jsonPath("$[0].content").doesNotExist())
                .andExpect(jsonPath("$[1].filename").value("later.pdf"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class QueryCaptureConfiguration {
        @Bean
        AttachmentSelects attachmentSelects() {
            return new AttachmentSelects();
        }

        @Bean
        HibernatePropertiesCustomizer captureAttachmentSelects(AttachmentSelects selects) {
            return properties -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, selects);
        }
    }

    static class AttachmentSelects implements StatementInspector {
        private final List<String> statements = new ArrayList<>();

        @Override
        public String inspect(String sql) {
            if (sql.startsWith("select ") && sql.contains(" from ticket_attachments ")) {
                statements.add(sql);
            }
            return sql;
        }
    }
}
