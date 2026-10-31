package com.example.helpdesk.web;

import com.example.helpdesk.model.*;
import com.example.helpdesk.repo.AssetRepository;
import com.example.helpdesk.repo.TicketHistoryRepository;
import com.example.helpdesk.repo.TicketCommentRepository;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired
    private JwtUtil jwtUtil;

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
    void disablingAccountRejectsExistingBrowserSessionAndJwt() throws Exception {
        var loginResult = mockMvc.perform(SecurityMockMvcRequestBuilders.formLogin()
                        .user("alice").password("alice123"))
                .andExpect(status().is3xxRedirection()).andReturn();
        org.springframework.mock.web.MockHttpSession session =
                (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);
        String token = jwtUtil.generateToken("alice");

        employeeAlice.setEnabled(false);
        userRepository.save(employeeAlice);

        mockMvc.perform(get("/my-tickets").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?disabled"));
        mockMvc.perform(get("/api/tickets/my").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"alice\",\"password\":\"alice123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserAndApiLoginRoutesThrottleRepeatedFailures() throws Exception {
        String apiUser = "api-throttle-check";
        String apiBody = "{\"username\":\"" + apiUser + "\",\"password\":\"wrong-password\"}";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login").contentType("application/json").content(apiBody))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(apiBody))
                .andExpect(status().isTooManyRequests());

        String browserUser = "browser-throttle-check";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/login").with(csrf()).param("username", browserUser).param("password", "wrong-password"))
                    .andExpect(status().is3xxRedirection());
        }
        mockMvc.perform(post("/login").with(csrf()).param("username", browserUser).param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?throttled"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeesCannotOpenAccountAdministration() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void overdueFilterShowsOnlyActiveTicketsPastTheirSla() throws Exception {
        aliceOpenTicket.setSlaTargetSeconds(3600);
        aliceOpenTicket.setSlaElapsedSeconds(3601);
        aliceOpenTicket.setSlaDueAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1));
        ticketRepository.save(aliceOpenTicket);

        mockMvc.perform(get("/tickets").param("overdue", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice Open Ticket")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Bob Open Ticket"))))
                .andExpect(content().string(containsString("Overdue SLA only")));
        assertEquals(1, ticketRepository.countOverdue(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)));
    }

    @Test
    void assetApiRejectsStaleVersion() throws Exception {
        String technicianToken = "Bearer " + jwtUtil.generateToken("charlie");
        mockMvc.perform(put("/api/assets/" + laptopAsset.getId())
                        .header("Authorization", technicianToken).contentType("application/json")
                        .content("{\"assetTag\":\"AST-TEST-01\",\"modelName\":\"ThinkPad T14\",\"category\":\"Laptop\",\"status\":\"AVAILABLE\",\"version\":-1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void browserSessionCookieCannotAuthenticateRestApiButBearerTokenCan() throws Exception {
        var login = SecurityMockMvcRequestBuilders.formLogin("/login")
                .user("username", "alice").password("password", "alice123");
        var session = mockMvc.perform(login).andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/api/tickets/my").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/tickets/my").header("Authorization", "Bearer " + jwtUtil.generateToken("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void apiValidatesJsonAndScopesEmployeeTicketDetailsAndComments() throws Exception {
        String employeeToken = "Bearer " + jwtUtil.generateToken("alice");
        String technicianToken = "Bearer " + jwtUtil.generateToken("charlie");

        mockMvc.perform(post("/api/tickets").header("Authorization", employeeToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/api/tickets").header("Authorization", employeeToken)
                        .contentType("application/json")
                        .content("{\"title\":\"Employee API ticket\",\"description\":\"A valid API ticket description\",\"category\":\"OTHER\",\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(get("/api/tickets").header("Authorization", employeeToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/assets").header("Authorization", employeeToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/assets").header("Authorization", technicianToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tickets/my/" + bobOpenTicket.getId()).header("Authorization", employeeToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/tickets/" + aliceOpenTicket.getId() + "/comments")
                        .header("Authorization", employeeToken).contentType("application/json")
                        .content("{\"content\":\"Please update me\",\"internalNote\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/tickets/" + aliceOpenTicket.getId() + "/comments")
                        .header("Authorization", employeeToken).contentType("application/json")
                        .content("{\"content\":\"Public status update request\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tickets/" + aliceOpenTicket.getId() + "/comments")
                        .header("Authorization", technicianToken).contentType("application/json")
                        .content("{\"content\":\"Internal troubleshooting detail\",\"internalNote\":true}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tickets/" + aliceOpenTicket.getId() + "/comments").header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Public status update request")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Internal troubleshooting detail"))));

        mockMvc.perform(get("/api/tickets/" + aliceOpenTicket.getId() + "/comments").header("Authorization", technicianToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Internal troubleshooting detail")));
    }

    @Test
    void apiRequiresCurrentTicketVersionForStatusUpdates() throws Exception {
        String technicianToken = "Bearer " + jwtUtil.generateToken("charlie");
        Long originalVersion = aliceOpenTicket.getVersion();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/tickets/" + aliceOpenTicket.getId() + "/status")
                        .header("Authorization", technicianToken)
                        .param("status", "IN_PROGRESS").param("version", originalVersion.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/tickets/" + aliceOpenTicket.getId() + "/status")
                        .header("Authorization", technicianToken)
                        .param("status", "RESOLVED").param("resolutionNotes", "Resolved")
                        .param("version", originalVersion.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void dashboardSummaryCardsNavigateToFilteredLists() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/tickets?status=OPEN")))
                .andExpect(content().string(containsString("/tickets?status=IN_PROGRESS")))
                .andExpect(content().string(containsString("/tickets?statusGroup=resolved-closed")))
                .andExpect(content().string(containsString("/assets")));
    }

    @Test
    @WithMockUser(username = "charlie", roles = "TECHNICIAN")
    void resolvedClosedDashboardFilterShowsBothStatusesAndItsCount() throws Exception {
        Ticket resolved = new Ticket();
        resolved.setTitle("Dashboard resolved example");
        resolved.setDescription("A resolved dashboard filter example");
        resolved.setCategory(Category.OTHER);
        resolved.setPriority(Priority.LOW);
        resolved.setStatus(TicketStatus.RESOLVED);
        resolved.setReporter(employeeAlice);
        resolved.setResolutionNotes("Resolved for dashboard coverage");
        ticketRepository.save(resolved);

        Ticket closed = new Ticket();
        closed.setTitle("Dashboard closed example");
        closed.setDescription("A closed dashboard filter example");
        closed.setCategory(Category.OTHER);
        closed.setPriority(Priority.LOW);
        closed.setStatus(TicketStatus.CLOSED);
        closed.setReporter(employeeBob);
        closed.setResolutionNotes("Closed for dashboard coverage");
        ticketRepository.save(closed);

        mockMvc.perform(get("/tickets").param("statusGroup", "resolved-closed"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 ticket(s)")))
                .andExpect(content().string(containsString("name=\"statusGroup\" value=\"resolved-closed\"")))
                .andExpect(content().string(containsString("Dashboard resolved example")))
                .andExpect(content().string(containsString("Dashboard closed example")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Alice Open Ticket"))));

        mockMvc.perform(get("/tickets").param("statusGroup", "resolved-closed").param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 ticket(s)")))
                .andExpect(content().string(containsString("Dashboard resolved example")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Dashboard closed example"))));

        mockMvc.perform(get("/tickets").param("statusGroup", "resolved-closed").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("0 ticket(s)")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Dashboard resolved example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Dashboard closed example"))));
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
    void employeeNavigationPersistsAcrossTicketAndNotificationPages() throws Exception {
        for (String path : java.util.List.of(
                "/my-tickets", "/my-tickets/" + aliceOpenTicket.getId(),
                "/tickets/new", "/notifications")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("href=\"/my-tickets\"")))
                    .andExpect(content().string(containsString("href=\"/tickets/new\"")))
                    .andExpect(content().string(containsString("href=\"/notifications\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/assets\""))));
        }
        mockMvc.perform(get("/notifications"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Back to dashboard"))));
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
                        .with(csrf()).param("status", "IN_PROGRESS")
                        .param("version", aliceOpenTicket.getVersion().toString()))
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
                        .with(csrf()).param("technicianId", techCharlie.getId().toString())
                        .param("version", aliceOpenTicket.getVersion().toString()))
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
                        .param("technicianId", techCharlie.getId().toString())
                        .param("version", bobOpenTicket.getVersion().toString()))
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
                        .with(csrf()).param("status", "RESOLVED")
                        .param("version", aliceOpenTicket.getVersion().toString()))
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
                        .param("version", laptopAsset.getVersion().toString())
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
