package com.example.helpdesk.web;

import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountAdminController {

    private final UserRepository users;

    public AccountAdminController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/users")
    String users(Model model) {
        model.addAttribute("users", users.findAll());
        return "users";
    }

    @PostMapping("/users/{id}/enabled")
    String setEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            Authentication authentication,
            RedirectAttributes redirect) {
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        boolean actorIsAdmin = hasRole(authentication, Role.ADMIN);
        if (!enabled && user.getUsername().equals(authentication.getName())) {
            throw new AccessDeniedException("You cannot disable your own account.");
        }
        if (!enabled && !actorIsAdmin && user.getRole() != Role.EMPLOYEE) {
            throw new AccessDeniedException("Only an administrator can disable another technician.");
        }
        if (!enabled
                && user.getRole() == Role.TECHNICIAN
                && user.isEnabled()
                && users.countByRoleAndEnabledTrue(Role.TECHNICIAN) <= 1) {
            throw new AccessDeniedException("At least one active technician must remain.");
        }
        user.setEnabled(enabled);
        users.save(user);
        redirect.addFlashAttribute("success", "Account " + (enabled ? "enabled" : "disabled") + ".");
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/role")
    String setRole(
            @PathVariable Long id,
            @RequestParam Role role,
            Authentication authentication,
            RedirectAttributes redirect) {
        if (!hasRole(authentication, Role.ADMIN)) {
            throw new AccessDeniedException("Only an administrator can change roles.");
        }
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (user.getUsername().equals(authentication.getName())) {
            throw new AccessDeniedException("You cannot change your own role.");
        }
        if (user.getRole() == Role.TECHNICIAN
                && role != Role.TECHNICIAN
                && user.isEnabled()
                && users.countByRoleAndEnabledTrue(Role.TECHNICIAN) <= 1) {
            throw new AccessDeniedException("At least one active technician must remain.");
        }
        user.setRole(role);
        users.save(user);
        redirect.addFlashAttribute("success", "Role updated to " + role.name() + ".");
        return "redirect:/users";
    }

    private boolean hasRole(Authentication authentication, Role role) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> ("ROLE_" + role.name()).equals(authority.getAuthority()));
    }
}
