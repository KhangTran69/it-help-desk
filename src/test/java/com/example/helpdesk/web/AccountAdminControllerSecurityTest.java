package com.example.helpdesk.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User employee;
    private User technician;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        employee = userRepository.save(
                new User("employee", passwordEncoder.encode("password123"), "Test Employee", Role.EMPLOYEE));
        technician = userRepository.save(
                new User("technician", passwordEncoder.encode("password123"), "Test Technician", Role.TECHNICIAN));
        // The EnabledAccountFilter re-checks the acting account, so mock actors must exist in the repository.
        userRepository.save(new User("admin", passwordEncoder.encode("password123"), "Admin", Role.ADMIN));
    }

    @Test
    @WithMockUser(username = "technician", roles = "TECHNICIAN")
    void technicianCanDisableAndReenableAnotherAccount() throws Exception {
        mockMvc.perform(post("/users/{id}/enabled", employee.getId())
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection());

        assertFalse(userRepository.findById(employee.getId()).orElseThrow().isEnabled());

        mockMvc.perform(post("/users/{id}/enabled", employee.getId())
                        .with(csrf())
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection());

        assertTrue(userRepository.findById(employee.getId()).orElseThrow().isEnabled());
    }

    @Test
    @WithMockUser(username = "employee", roles = "EMPLOYEE")
    void employeeCannotChangeAnotherAccountsEnabledState() throws Exception {
        mockMvc.perform(post("/users/{id}/enabled", technician.getId())
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().isForbidden());

        assertTrue(userRepository.findById(technician.getId()).orElseThrow().isEnabled());
    }

    @Test
    @WithMockUser(username = "technician", roles = "TECHNICIAN")
    void technicianCannotDisableTheirOwnAccount() throws Exception {
        mockMvc.perform(post("/users/{id}/enabled", technician.getId())
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().isForbidden());

        assertTrue(userRepository.findById(technician.getId()).orElseThrow().isEnabled());
    }

    @Test
    @WithMockUser(username = "technician", roles = "TECHNICIAN")
    void accountStateCannotChangeWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/users/{id}/enabled", employee.getId()).param("enabled", "false"))
                .andExpect(status().isForbidden());

        assertTrue(userRepository.findById(employee.getId()).orElseThrow().isEnabled());
    }

    @Test
    @WithMockUser(username = "technician", roles = "TECHNICIAN")
    void technicianCannotDisableAnotherTechnicianWithoutAdminRole() throws Exception {
        User secondTechnician = userRepository.save(
                new User("tech2", passwordEncoder.encode("password123"), "Second Tech", Role.TECHNICIAN));

        mockMvc.perform(post("/users/{id}/enabled", secondTechnician.getId())
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().isForbidden());

        assertTrue(userRepository.findById(secondTechnician.getId()).orElseThrow().isEnabled());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN", "TECHNICIAN"})
    void adminCanDisableAnotherTechnicianButNotTheLastActiveOne() throws Exception {
        User secondTechnician = userRepository.save(
                new User("tech2", passwordEncoder.encode("password123"), "Second Tech", Role.TECHNICIAN));

        mockMvc.perform(post("/users/{id}/enabled", secondTechnician.getId())
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection());
        assertFalse(userRepository.findById(secondTechnician.getId()).orElseThrow().isEnabled());

        // technician is now the only active technician left; disabling them is blocked.
        mockMvc.perform(post("/users/{id}/enabled", technician.getId())
                        .with(csrf())
                        .param("enabled", "false"))
                .andExpect(status().isForbidden());
        assertTrue(userRepository.findById(technician.getId()).orElseThrow().isEnabled());
    }

    @Test
    @WithMockUser(username = "technician", roles = "TECHNICIAN")
    void roleChangesRequireAnAdministrator() throws Exception {
        mockMvc.perform(post("/users/{id}/role", employee.getId())
                        .with(csrf())
                        .param("role", "TECHNICIAN"))
                .andExpect(status().isForbidden());
        assertEquals(Role.EMPLOYEE, userRepository.findById(employee.getId()).orElseThrow().getRole());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN", "TECHNICIAN"})
    void adminCanPromoteAnEmployeeButNotThemselves() throws Exception {
        mockMvc.perform(post("/users/{id}/role", employee.getId())
                        .with(csrf())
                        .param("role", "TECHNICIAN"))
                .andExpect(status().is3xxRedirection());
        assertEquals(Role.TECHNICIAN, userRepository.findById(employee.getId()).orElseThrow().getRole());

        User admin = userRepository.findByUsername("admin").orElseThrow();
        mockMvc.perform(post("/users/{id}/role", admin.getId())
                        .with(csrf())
                        .param("role", "EMPLOYEE"))
                .andExpect(status().isForbidden());
        assertEquals(Role.ADMIN, userRepository.findById(admin.getId()).orElseThrow().getRole());
    }
}
