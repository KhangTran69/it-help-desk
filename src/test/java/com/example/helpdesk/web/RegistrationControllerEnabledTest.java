package com.example.helpdesk.web;

import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "helpdesk.registration-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationControllerEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void enabledRegistrationRendersForm() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create employee account")));
    }

    @Test
    void enabledRegistrationPersistsEmployeeWithEncodedPassword() throws Exception {
        String rawPassword = "employee-password-123";

        mockMvc.perform(post("/register").with(csrf())
                        .param("username", "new-employee")
                        .param("fullName", "New Employee")
                        .param("password", rawPassword)
                        .param("confirmPassword", rawPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        User created = userRepository.findByUsername("new-employee").orElseThrow();
        assertEquals(Role.EMPLOYEE, created.getRole());
        assertTrue(passwordEncoder.matches(rawPassword, created.getPassword()));
    }
}
