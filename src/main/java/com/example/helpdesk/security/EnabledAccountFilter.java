package com.example.helpdesk.security;

import com.example.helpdesk.repo.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rechecks browser accounts so disabling a user also ends already established sessions. */
public class EnabledAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public EnabledAccountFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && !"/logout".equals(request.getServletPath())) {
            boolean enabled = users.findByUsername(authentication.getName()).map(com.example.helpdesk.model.User::isEnabled).orElse(false);
            if (!enabled) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) request.getSession(false).invalidate();
                response.sendRedirect("/login?disabled");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
