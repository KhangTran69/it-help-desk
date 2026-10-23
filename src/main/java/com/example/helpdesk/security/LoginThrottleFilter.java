package com.example.helpdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Blocks browser form attempts after the shared login throttle reaches its limit. */
public class LoginThrottleFilter extends OncePerRequestFilter {
    private final LoginAttemptService attempts;

    public LoginThrottleFilter(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String loginPath = request.getContextPath() + "/login";
        if ("POST".equals(request.getMethod()) && loginPath.equals(request.getRequestURI())
                && attempts.isBlocked(request.getParameter("username"), request.getRemoteAddr())) {
            response.sendRedirect("/login?throttled");
            return;
        }
        chain.doFilter(request, response);
    }
}
