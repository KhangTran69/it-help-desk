package com.example.helpdesk.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class LoginController {
    @GetMapping("/login")
    String login(@org.springframework.web.bind.annotation.RequestParam(required = false) String throttled,
                 @org.springframework.web.bind.annotation.RequestParam(required = false) String disabled, Model model) {
        if (throttled != null) model.addAttribute("accountMessage", "Too many failed attempts. Try again in 10 minutes.");
        if (disabled != null) model.addAttribute("accountMessage", "This account is disabled. Contact a technician for help.");
        return "login";
    }

    @GetMapping("/access-denied")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    String accessDenied(Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("error", "Access Denied");
        model.addAttribute("message", "Your account is not allowed to access this page.");
        return "error";
    }
}
