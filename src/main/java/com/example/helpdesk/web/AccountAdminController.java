package com.example.helpdesk.web;

import com.example.helpdesk.exception.ResourceNotFoundException;
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
    String setEnabled(@PathVariable Long id, @RequestParam boolean enabled, Authentication authentication,
                      RedirectAttributes redirect) {
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!enabled && user.getUsername().equals(authentication.getName())) {
            throw new AccessDeniedException("You cannot disable your own account.");
        }
        user.setEnabled(enabled);
        users.save(user);
        redirect.addFlashAttribute("success", "Account " + (enabled ? "enabled" : "disabled") + ".");
        return "redirect:/users";
    }
}
