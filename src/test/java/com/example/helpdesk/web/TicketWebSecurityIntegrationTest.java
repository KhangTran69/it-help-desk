package com.example.helpdesk.web;

import com.example.helpdesk.model.*;
import com.example.helpdesk.repo.AssetRepository;
import com.example.helpdesk.repo.TicketHistoryRepository;
import com.example.helpdesk.repo.TicketCommentRepository;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TicketWebSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TicketHistoryRepository historyRepository;

    @Autowired
    private TicketCommentRepository commentRepository;

    @Autowired
    private PasswordEncoder encoder;

    private User employeeAlice;
    private User employeeBob;
    private User techCharlie;
    private Ticket aliceOpenTicket;
    private Ticket bobOpenTicket;
    private Ticket aliceInProgressTicket;
    private Asset laptopAsset;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();

        employeeAlice = userRepository.save(new User("alice", encoder.encode("alice123"), "Alice Adams", Role.EMPLOYEE));
        employeeBob = userRepository.save(new User("bob", encoder.encode("bob123"), "Bob Baker", Role.EMPLOYEE));
        techCharlie = userRepository.save(new User("charlie", encoder.encode("charlie123"), "Charlie Tech", Role.TECHNICIAN));

        aliceOpenTicket = new Ticket();
        aliceOpenTicket.setTitle("Alice Open Ticket");
        aliceOpenTicket.setDescription("Need assistance with display cable");
        aliceOpenTicket.setCategory(Category.HARDWARE);
        aliceOpenTicket.setPriority(Priority.LOW);
        aliceOpenTicket.setStatus(TicketStatus.OPEN);
        aliceOpenTicket.setReporter(employeeAlice);
        aliceOpenTicket = ticketRepository.save(aliceOpenTicket);

        bobOpenTicket = new Ticket();
        bobOpenTicket.setTitle("Bob Open Ticket");
        bobOpenTicket.setDescription("VPN timeout error");
        bobOpenTicket.setCategory(Category.NETWORK);
        bobOpenTicket.setPriority(Priority.HIGH);
        bobOpenTicket.setStatus(TicketStatus.OPEN);
        bobOpenTicket.setReporter(employeeBob);
        bobOpenTicket = ticketRepository.save(bobOpenTicket);

        aliceInProgressTicket = new Ticket();
        aliceInProgressTicket.setTitle("Alice In-Progress Ticket");
        aliceInProgressTicket.setDescription("Work in progress");
        aliceInProgressTicket.setCategory(Category.SOFTWARE);
        aliceInProgressTicket.setPriority(Priority.MEDIUM);
        aliceInProgressTicket.setStatus(TicketStatus.IN_PROGRESS);
        aliceInProgressTicket.setReporter(employeeAlice);
        aliceInProgressTicket.setTechnician(techCharlie);
        aliceInProgressTicket = ticketRepository.save(aliceInProgressTicket);

        laptopAsset = new Asset();
        laptopAsset.setAssetTag("AST-TEST-01");
        laptopAsset.setName("ThinkPad T14");
        laptopAsset.setCategory("Laptop");
        laptopAsset.setStatus(AssetStatus.AVAILABLE);
        laptopAsset = assetRepository.save(laptopAsset);
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void apiRejectsInvalidPaginationAndUnknownSortFieldsAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/tickets").param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/json"));
        mockMvc.perform(get("/api/tickets").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tickets").param("sortBy", "reporter.password"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/assets").param("sortBy", "assignedUser.password"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void dashboardSummaryCardsNavigateToFilteredLists() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/tickets?status=OPEN")))
                .andExpect(content().string(containsString("/tickets?status=IN_PROGRESS")))
                .andExpect(content().string(containsString("/assets")));
    }

    @Test
    void unauthenticatedUserRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/tickets"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/assets"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(post("/tickets/new").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request()
                        .sessionAttributeDoesNotExist("SPRING_SECURITY_SAVED_REQUEST"));
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void unsupportedPostDoesNotBecomeInternalServerError() throws Exception {
        mockMvc.perform(post("/tickets/new").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().string(containsString("HTTP 405")))
                .andExpect(content().string(containsString("Action not available")));
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCannotBrowseTicketQueueOrDetails() throws Exception {
        mockMvc.perform(get("/tickets"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/tickets/" + aliceOpenTicket.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/tickets/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/assets\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/tickets\""))));
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCannotViewOtherEmployeesTicketDetail() throws Exception {
        mockMvc.perform(get("/tickets/" + bobOpenTicket.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCanTrackOnlyTheirOwnTickets() throws Exception {
        mockMvc.perform(get("/my-tickets"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice Open Ticket")))
                .andExpect(content().string(containsString("Alice In-Progress Ticket")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Bob Open Ticket"))))
                .andExpect(content().string(containsString("Last updated")));

        mockMvc.perform(get("/my-tickets/" + aliceInProgressTicket.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ticket progress")))
                .andExpect(content().string(containsString("IN PROGRESS")))
                .andExpect(content().string(containsString("aria-current=\"step\"")))
                .andExpect(content().string(containsString("Create Ticket")));
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCannotTrackAnotherEmployeesTicketByChangingId() throws Exception {
        mockMvc.perform(get("/my-tickets/" + bobOpenTicket.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Ticket not found")));
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCanCommentOnlyOnOwnTicketAndCannotCreateInternalNotes() throws Exception {
        mockMvc.perform(post("/my-tickets/" + aliceOpenTicket.getId() + "/comments")
                        .with(csrf()).param("content", "I am available after lunch.").param("internalNote", "true"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/my-tickets/" + aliceOpenTicket.getId()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("I am available after lunch.")));
        mockMvc.perform(post("/my-tickets/" + bobOpenTicket.getId() + "/comments")
                        .with(csrf()).param("content", "Unauthorized"))
                .andExpect(status().isNotFound());
        assertEquals(1, commentRepository.findByTicketAndInternalNoteFalseOrderByCreatedAtAsc(aliceOpenTicket).size());
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianInternalNotesAreNotShownToEmployee() throws Exception {
        mockMvc.perform(post("/tickets/" + aliceOpenTicket.getId() + "/comments")
                        .with(csrf()).param("content", "Check private asset inventory").param("internalNote", "true"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/tickets/" + aliceOpenTicket.getId()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Check private asset inventory")));
        mockMvc.perform(get("/my-tickets/" + aliceOpenTicket.getId()).with(userDetails("alice", "EMPLOYEE")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Check private asset inventory"))));
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianListsRenderPagedTicketAndAssetResults() throws Exception {
        mockMvc.perform(get("/tickets").param("size", "1").param("sort", "priority").param("dir", "asc"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("page 1 of")))
                .andExpect(content().string(containsString("Next →")));
        mockMvc.perform(get("/assets").param("size", "1").param("q", "AST"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("page 1 of 1")));
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianStatusUpdateIsVisibleInEmployeeProgress() throws Exception {
        var previousUpdatedAt = aliceOpenTicket.getUpdatedAt();
        mockMvc.perform(post("/tickets/" + aliceOpenTicket.getId() + "/status")
                        .with(csrf()).param("status", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection());

        Ticket updated = ticketRepository.findById(aliceOpenTicket.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(updated.getUpdatedAt().isAfter(previousUpdatedAt));

        mockMvc.perform(get("/my-tickets/" + aliceOpenTicket.getId()).with(userDetails("alice", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("IN PROGRESS")))
                .andExpect(content().string(containsString("Last updated")));
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianAssignmentRefreshesEmployeeVisibleLastUpdatedDate() throws Exception {
        var previousUpdatedAt = aliceOpenTicket.getUpdatedAt();
        mockMvc.perform(post("/tickets/" + aliceOpenTicket.getId() + "/assign")
                        .with(csrf()).param("technicianId", techCharlie.getId().toString()))
                .andExpect(status().is3xxRedirection());

        Ticket updated = ticketRepository.findById(aliceOpenTicket.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(updated.getUpdatedAt().isAfter(previousUpdatedAt));
        mockMvc.perform(get("/my-tickets/" + aliceOpenTicket.getId()).with(userDetails("alice", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Last updated")));
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCannotEditAnyTicket() throws Exception {
        mockMvc.perform(get("/tickets/" + aliceOpenTicket.getId() + "/edit"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/tickets/" + aliceOpenTicket.getId() + "/edit")
                        .with(csrf())
                        .param("title", "Alice Updated Title")
                        .param("description", "Updated detailed description of issue")
                        .param("category", "SOFTWARE")
                        .param("priority", "HIGH"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCannotEditOtherEmployeesTicket() throws Exception {
        mockMvc.perform(get("/tickets/" + bobOpenTicket.getId() + "/edit"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/tickets/" + bobOpenTicket.getId() + "/edit")
                        .with(csrf())
                        .param("title", "Hacked Title")
                        .param("description", "Hacked Desc")
                        .param("category", "OTHER")
                        .param("priority", "LOW"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCannotEditTicketThatIsNotOpen() throws Exception {
        mockMvc.perform(get("/tickets/" + aliceInProgressTicket.getId() + "/edit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCannotAccessTechnicianOnlyEndpoints() throws Exception {
        mockMvc.perform(get("/assets/new"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/assets/" + laptopAsset.getId() + "/edit"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/tickets/" + aliceOpenTicket.getId() + "/assign")
                        .with(csrf())
                        .param("technicianId", techCharlie.getId().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/tickets/" + aliceOpenTicket.getId() + "/status")
                        .with(csrf()).param("status", "RESOLVED").param("notes", "Completed"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/assets").with(csrf())
                        .param("assetTag", "AST-EMPLOYEE")
                        .param("name", "Unauthorized asset")
                        .param("category", "Laptop")
                        .param("status", "AVAILABLE"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianCanViewAnyTicketAndAssign() throws Exception {
        mockMvc.perform(get("/tickets/" + bobOpenTicket.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bob Open Ticket")));

        mockMvc.perform(post("/tickets/" + bobOpenTicket.getId() + "/assign")
                        .with(csrf())
                        .param("technicianId", techCharlie.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + bobOpenTicket.getId()))
                .andExpect(flash().attributeExists("success"));

        Ticket updated = ticketRepository.findById(bobOpenTicket.getId()).orElseThrow();
        assertNotNull(updated.getTechnician());
        assertEquals(techCharlie.getId(), updated.getTechnician().getId());
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianCanEditTicketAndChangeStatusWithNotes() throws Exception {
        mockMvc.perform(post("/tickets/" + aliceOpenTicket.getId() + "/edit")
                        .with(csrf())
                        .param("title", "Alice Cable Issue - Verified")
                        .param("description", "Replaced cable")
                        .param("category", "HARDWARE")
                        .param("priority", "MEDIUM")
                        .param("version", aliceOpenTicket.getVersion().toString())
                        .param("status", "RESOLVED")
                        .param("technicianId", techCharlie.getId().toString())
                        .param("resolutionNotes", "Replaced faulty DisplayPort cable with new 4K certified cable."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + aliceOpenTicket.getId()))
                .andExpect(flash().attributeExists("success"));

        Ticket updated = ticketRepository.findById(aliceOpenTicket.getId()).orElseThrow();
        assertEquals(TicketStatus.RESOLVED, updated.getStatus());
        assertEquals("Replaced faulty DisplayPort cable with new 4K certified cable.", updated.getResolutionNotes());
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianCannotResolveOrCloseWithoutResolutionNotes() throws Exception {
        mockMvc.perform(post("/tickets/" + aliceOpenTicket.getId() + "/status")
                        .with(csrf()).param("status", "RESOLVED"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Resolution notes are required")));
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianCanEditAsset() throws Exception {
        mockMvc.perform(get("/assets/" + laptopAsset.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit Equipment Asset")));

        mockMvc.perform(post("/assets/" + laptopAsset.getId() + "/edit")
                        .with(csrf())
                        .param("assetTag", "AST-TEST-01")
                        .param("name", "ThinkPad T14 Gen 3 (Repaired)")
                        .param("category", "Laptop")
                        .param("status", "AVAILABLE")
                        .param("notes", "Screen replacement complete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/assets"))
                .andExpect(flash().attributeExists("success"));

        Asset updated = assetRepository.findById(laptopAsset.getId()).orElseThrow();
        assertEquals("ThinkPad T14 Gen 3 (Repaired)", updated.getName());
        assertEquals("Screen replacement complete", updated.getNotes());
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void nonexistentTicketReturns404() throws Exception {
        mockMvc.perform(get("/tickets/99999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Resource Not Found")));
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeIsRedirectedFromAdminDashboardToTicketCreation() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/new"));
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianDashboardLoadsSuccessfully() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Operations Dashboard")))
                .andExpect(content().string(containsString("Unassigned Tickets")));
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCanAccessTicketCreationForm() throws Exception {
        mockMvc.perform(get("/tickets/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Submit a Support Ticket")));
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCanSubmitTicketAndReturnsToCreationFormWithSuccess() throws Exception {
        mockMvc.perform(post("/tickets").with(csrf())
                        .param("title", "Employee submitted ticket")
                        .param("description", "VPN fails when connecting from home")
                        .param("category", "NETWORK")
                        .param("priority", "HIGH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/new"))
                .andExpect(flash().attribute("success", containsString("submitted successfully")))
                .andExpect(flash().attributeExists("submittedTicketId"));

        Ticket created = ticketRepository.findAll().stream()
                .filter(ticket -> "Employee submitted ticket".equals(ticket.getTitle()))
                .findFirst().orElseThrow();
        assertEquals(employeeAlice.getId(), created.getReporter().getId());
        assertEquals(TicketStatus.OPEN, created.getStatus());
        assertEquals(Priority.HIGH, created.getPriority());

        mockMvc.perform(get("/tickets/new")
                        .flashAttr("success", "Your ticket was submitted successfully.")
                        .flashAttr("submittedTicketId", created.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("View ticket progress")))
                .andExpect(content().string(containsString("/my-tickets/" + created.getId())));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor userDetails(String username, String role) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(username)
                .roles(role);
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeTicketCreationValidatesRequiredFields() throws Exception {
        mockMvc.perform(post("/tickets").with(csrf())
                        .param("title", "Missing category")
                        .param("description", "Description")
                        .param("priority", "LOW"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Category is required")));
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianCanAccessAssetCreationForm() throws Exception {
        mockMvc.perform(get("/assets/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Register New Asset")));
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianCanCreateAsset() throws Exception {
        mockMvc.perform(post("/assets").with(csrf())
                        .param("assetTag", "AST-CREATED-02")
                        .param("name", "MacBook Pro 14")
                        .param("category", "Laptop")
                        .param("status", "AVAILABLE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/assets"))
                .andExpect(flash().attributeExists("success"));

        Asset created = assetRepository.findAll().stream()
                .filter(asset -> "AST-CREATED-02".equals(asset.getAssetTag()))
                .findFirst().orElseThrow();
        assertEquals("MacBook Pro 14", created.getName());
        assertEquals(AssetStatus.AVAILABLE, created.getStatus());
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeeCannotViewAssetsInventory() throws Exception {
        mockMvc.perform(get("/assets"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianCanFilterTicketsByStatusAndPriority() throws Exception {
        mockMvc.perform(get("/tickets")
                        .param("status", "OPEN")
                        .param("priority", "LOW")
                        .param("unassigned", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice Open Ticket")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Bob Open Ticket"))));
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void technicianCanFilterAssetsByCategoryAndStatus() throws Exception {
        mockMvc.perform(get("/assets")
                        .param("status", "AVAILABLE")
                        .param("category", "Laptop"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Asset inventory")));
    }
}
