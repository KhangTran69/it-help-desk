package com.example.helpdesk.web;

import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class CurrentUserModelAdvice {
    private final UserRepository users;

    public CurrentUserModelAdvice(UserRepository users) {
        this.users = users;
    }

    @ModelAttribute("currentUser")
    public User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return users.findByUsername(authentication.getName()).orElse(null);
    }

    @ModelAttribute("isTechnician")
    public boolean isTechnician(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_TECHNICIAN".equals(authority.getAuthority()));
    }
}
