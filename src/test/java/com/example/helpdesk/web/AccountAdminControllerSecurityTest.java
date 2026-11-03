package com.example.helpdesk.web;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        employee = userRepository.save(new User("employee", passwordEncoder.encode("password123"),
                "Test Employee", Role.EMPLOYEE));
        technician = userRepository.save(new User("technician", passwordEncoder.encode("password123"),
                "Test Technician", Role.TECHNICIAN));
    }

    @Test
    @WithMockUser(username = "technician", roles = "TECHNICIAN")
    void technicianCanDisableAndReenableAnotherAccount() throws Exception {
        mockMvc.perform(post("/users/{id}/enabled", employee.getId()).with(csrf()).param("enabled", "false"))
                .andExpect(status().is3xxRedirection());

        assertFalse(userRepository.findById(employee.getId()).orElseThrow().isEnabled());

        mockMvc.perform(post("/users/{id}/enabled", employee.getId()).with(csrf()).param("enabled", "true"))
                .andExpect(status().is3xxRedirection());

        assertTrue(userRepository.findById(employee.getId()).orElseThrow().isEnabled());
    }

    @Test
    @WithMockUser(username = "employee", roles = "EMPLOYEE")
    void employeeCannotChangeAnotherAccountsEnabledState() throws Exception {
        mockMvc.perform(post("/users/{id}/enabled", technician.getId()).with(csrf()).param("enabled", "false"))
                .andExpect(status().isForbidden());

        assertTrue(userRepository.findById(technician.getId()).orElseThrow().isEnabled());
    }

    @Test
    @WithMockUser(username = "technician", roles = "TECHNICIAN")
    void technicianCannotDisableTheirOwnAccount() throws Exception {
        mockMvc.perform(post("/users/{id}/enabled", technician.getId()).with(csrf()).param("enabled", "false"))
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
}
