package com.example.helpdesk.api;

import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.security.JwtUtil;
import com.example.helpdesk.security.LoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "JWT Authentication endpoints")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final LoginAttemptService loginAttempts;

    public AuthController(AuthenticationManager authenticationManager,
                         JwtUtil jwtUtil,
                         UserRepository userRepository, LoginAttemptService loginAttempts) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.loginAttempts = loginAttempts;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and get JWT token")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (loginAttempts.isBlocked(loginRequest.getUsername(), remoteAddress)) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many failed attempts. Try again in 10 minutes."));
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            String token = jwtUtil.generateToken(loginRequest.getUsername());
            loginAttempts.recordSuccess(loginRequest.getUsername(), remoteAddress);
            User user = userRepository.findByUsername(loginRequest.getUsername()).orElse(null);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("type", "Bearer");
            response.put("username", loginRequest.getUsername());
            if (user != null) {
                response.put("role", user.getRole().name());
                response.put("fullName", user.getFullName());
            }

            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            loginAttempts.recordFailure(loginRequest.getUsername(), remoteAddress);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    public static class LoginRequest {
        @NotBlank
        @Size(max = 80)
        private String username;
        @NotBlank
        @Size(max = 200)
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
