package com.example.helpdesk.config;

import com.example.helpdesk.security.JwtAuthenticationFilter;
import com.example.helpdesk.security.LoginAttemptService;
import com.example.helpdesk.security.LoginThrottleFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterServletRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<LoginThrottleFilter> loginThrottleServletRegistration(LoginThrottleFilter filter) {
        FilterRegistrationBean<LoginThrottleFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    LoginThrottleFilter loginThrottleFilter(LoginAttemptService attempts) {
        return new LoginThrottleFilter(attempts);
    }

    @Bean
    com.example.helpdesk.security.EnabledAccountFilter enabledAccountFilter(com.example.helpdesk.repo.UserRepository users) {
        return new com.example.helpdesk.security.EnabledAccountFilter(users);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        return http
                .securityMatcher("/api/**")
                // This chain never reads or creates an HTTP session; only a bearer token can authenticate it.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, cause) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"A valid bearer token is required.\"}");
                        })
                        .accessDeniedHandler((request, response, cause) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"You are not allowed to perform this action.\"}");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain browserFilterChain(HttpSecurity http, LoginThrottleFilter throttleFilter,
                                                   LoginAttemptService attempts,
                                                   com.example.helpdesk.security.EnabledAccountFilter enabledAccountFilter) throws Exception {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(request -> "GET".equals(request.getMethod()));
        return http
                // All browser state-changing requests, including logout, require CSRF tokens.
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/css/**", "/login", "/register", "/error",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/assets", "/assets/**").hasRole("TECHNICIAN")
                        .requestMatchers("/users", "/users/**").hasRole("TECHNICIAN")
                        .requestMatchers("/my-tickets", "/my-tickets/**").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.GET, "/tickets/new").authenticated()
                        .requestMatchers(HttpMethod.POST, "/tickets").authenticated()
                        .requestMatchers("/tickets", "/tickets/**").hasRole("TECHNICIAN")
                        .anyRequest().authenticated()
                )
                .formLogin(f -> f
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            attempts.recordSuccess(authentication.getName(), request.getRemoteAddr());
                            response.sendRedirect("/");
                        })
                        .failureHandler((request, response, exception) -> {
                            attempts.recordFailure(request.getParameter("username"), request.getRemoteAddr());
                            response.sendRedirect("/login?error");
                        })
                        .permitAll()
                )
                .logout(l -> l.logoutSuccessUrl("/login?logout"))
                // Do not replay an unauthenticated state-changing form request after sign-in.
                .requestCache(cache -> cache.requestCache(requestCache))
                .addFilterBefore(throttleFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(enabledAccountFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self';"))
                        .frameOptions(frame -> frame.sameOrigin())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .xssProtection(xss -> xss.disable())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(permissions -> permissions.policy("geolocation=(), microphone=(), camera=()"))
                )
                .exceptionHandling(e -> e.accessDeniedPage("/access-denied"))
                .build();
    }
}
