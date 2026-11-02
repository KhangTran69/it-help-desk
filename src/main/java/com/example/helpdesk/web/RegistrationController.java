package com.example.helpdesk.web;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RegistrationController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final boolean registrationEnabled;

    public RegistrationController(
            UserRepository users,
            PasswordEncoder encoder,
            @Value("${helpdesk.registration-enabled:false}") boolean registrationEnabled) {
        this.users = users;
        this.encoder = encoder;
        this.registrationEnabled = registrationEnabled;
    }

    @GetMapping("/register")
    String register(Model model) {
        if (!registrationEnabled) {
            return "redirect:/login?registrationDisabled";
        }
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    String create(
            @RequestParam String username,
            @RequestParam String fullName,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model) {
        if (!registrationEnabled) {
            return "redirect:/login?registrationDisabled";
        }

        String clean = username == null ? "" : username.trim();
        if (clean.length() < 3 || fullName == null || fullName.trim().isBlank()) {
            model.addAttribute("error", "Please provide a username with at least 3 characters and your full name.");
            return "register";
        }
        if (password == null || password.length() < 8) {
            model.addAttribute("error", "Password must be at least 8 characters.");
            return "register";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            return "register";
        }
        if (users.findByUsername(clean).isPresent()) {
            model.addAttribute("error", "That username is already in use.");
            return "register";
        }
        users.save(new User(clean, encoder.encode(password), fullName.trim(), Role.EMPLOYEE));
        return "redirect:/login?registered";
    }
}
