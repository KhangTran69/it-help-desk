package com.example.helpdesk.web;

import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.AssetRepository;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.service.TicketService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final TicketService tickets;
    private final AssetRepository assets;
    private final UserRepository users;

    public HomeController(TicketService tickets, AssetRepository assets, UserRepository users) {
        this.tickets = tickets;
        this.assets = assets;
        this.users = users;
    }

    @GetMapping("/")
    String dashboard(Authentication authentication, Model model) {
        boolean technician = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_TECHNICIAN".equals(authority.getAuthority()));
        if (!technician) return "redirect:/tickets/new";
        User current = users.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("open", tickets.count(TicketStatus.OPEN));
        model.addAttribute("progress", tickets.count(TicketStatus.IN_PROGRESS));
        model.addAttribute("resolvedClosed", tickets.count(TicketStatus.RESOLVED) + tickets.count(TicketStatus.CLOSED));
        model.addAttribute("assets", assets.count());
        model.addAttribute("unassigned", tickets.countUnassigned());
        model.addAttribute("highPriority", tickets.countHighPriority());
        model.addAttribute("myAssigned", tickets.countAssignedTo(current));
        model.addAttribute("overdue", tickets.countOverdue());
        model.addAttribute("recentTickets", tickets.recent(10));
        model.addAttribute("isTechnician", true);
        model.addAttribute("currentUser", current);
        return "dashboard";
    }
}
