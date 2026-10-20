package com.example.helpdesk.config;

import com.example.helpdesk.repo.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository repo) {
        return username -> repo.findByUsername(username)
                .map(u -> User.withUsername(u.getUsername())
                        .password(u.getPassword())
                        .roles(u.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(request -> "GET".equals(request.getMethod()));
        return http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/css/**", "/login", "/register", "/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // The dashboard and asset administration are technician-only areas.
                        .requestMatchers("/assets", "/assets/**").hasRole("TECHNICIAN")
                        // Employee tracking pages are scoped again to the authenticated owner in TicketService.
                        .requestMatchers("/my-tickets", "/my-tickets/**").hasRole("EMPLOYEE")
                        // Employees may submit tickets, but cannot browse or manage the queue.
                        .requestMatchers(HttpMethod.GET, "/tickets/new").authenticated()
                        .requestMatchers(HttpMethod.POST, "/tickets").authenticated()
                        .requestMatchers("/tickets", "/tickets/**").hasRole("TECHNICIAN")
                        .anyRequest().authenticated()
                )
                .formLogin(f -> f
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(l -> l
                        .logoutSuccessUrl("/login?logout")
                )
                // Never replay an unauthenticated state-changing request after login.
                .requestCache(cache -> cache
                        .requestCache(requestCache)
                )
                .exceptionHandling(e -> e.accessDeniedPage("/access-denied"))
                .build();
    }
}
