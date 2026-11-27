package com.example.helpdesk.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerRefreshTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private void seedUser(String username, boolean enabled) {
        User user = new User(username, encoder.encode("refresh-pass-123"), "Refresh " + username, Role.EMPLOYEE);
        user.setEnabled(enabled);
        users.save(user);
    }

    @Test
    void refreshTokenExchangesForAFreshAccessAndRotatedRefreshToken() throws Exception {
        seedUser("refresh-user", true);
        String refreshToken = jwtUtil.generateRefreshToken("refresh-user");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("refresh-user"))
                .andExpect(jsonPath("$.type").value("Bearer"));
    }

    @Test
    void accessTokenCannotBeUsedAsARefreshToken() throws Exception {
        seedUser("refresh-user-2", true);
        String accessToken = jwtUtil.generateToken("refresh-user-2");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageRefreshTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"not-a-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotRefresh() throws Exception {
        seedUser("disabled-user", false);
        String refreshToken = jwtUtil.generateRefreshToken("disabled-user");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Account is unavailable."));
    }

    @Test
    void refreshTokenDoesNotAuthenticateApiEndpointsDirectly() throws Exception {
        seedUser("refresh-user-3", true);
        String refreshToken = jwtUtil.generateRefreshToken("refresh-user-3");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/tickets")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }
}
