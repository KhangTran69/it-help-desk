package com.example.helpdesk.web;

import com.example.helpdesk.dto.AssetForm;
import com.example.helpdesk.dto.TicketCreateForm;
import com.example.helpdesk.dto.TicketEditForm;
import com.example.helpdesk.model.*;
import com.example.helpdesk.repo.AssetRepository;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.service.AssetService;
import com.example.helpdesk.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {
    private final TicketService tickets;
    private final AssetRepository assets;
    private final AssetService assetService;
    private final UserRepository users;

    public HomeController(TicketService tickets, AssetRepository assets, AssetService assetService, UserRepository users) {
        this.tickets = tickets;
        this.assets = assets;
        this.assetService = assetService;
        this.users = users;
    }

    @GetMapping("/")
    String dashboard(Authentication authentication, Model model) {
        if (!isTechnician(authentication)) return "redirect:/tickets/new";
        User technician = users.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("open", tickets.count(TicketStatus.OPEN));
        model.addAttribute("progress", tickets.count(TicketStatus.IN_PROGRESS));
        model.addAttribute("resolvedClosed", tickets.count(TicketStatus.RESOLVED) + tickets.count(TicketStatus.CLOSED));
        model.addAttribute("assets", assets.count());
        model.addAttribute("unassigned", tickets.countUnassigned());
        model.addAttribute("highPriority", tickets.countHighPriority());
        model.addAttribute("myAssigned", tickets.countAssignedTo(technician));
        model.addAttribute("recentTickets", tickets.recent(10));
        model.addAttribute("isTechnician", true);
        model.addAttribute("currentUser", technician);
        return "dashboard";
    }

    @GetMapping("/tickets")
    String ticketList(@RequestParam(required = false) String q,
                      @RequestParam(required = false) TicketStatus status,
                      @RequestParam(required = false) Priority priority,
                      @RequestParam(required = false) Category category,
                      @RequestParam(required = false) Long technicianId,
                      @RequestParam(required = false) Boolean unassigned,
                      Model model) {
        model.addAttribute("tickets", tickets.search(status, priority, category, technicianId, unassigned, q));
        model.addAttribute("q", q);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedTechnicianId", technicianId);
        model.addAttribute("unassigned", unassigned);
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("categories", Category.values());
        model.addAttribute("technicians", users.findByRole(Role.TECHNICIAN));
        return "tickets";
    }

    @GetMapping("/tickets/new")
    String newTicket(Authentication authentication, Model model) {
        model.addAttribute("form", new TicketCreateForm());
        model.addAttribute("categories", Category.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("isTechnician", isTechnician(authentication));
        return "ticket-form";
    }

    @PostMapping("/tickets")
    String createTicket(@Valid @ModelAttribute("form") TicketCreateForm form, BindingResult result,
                        Authentication authentication, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("categories", Category.values());
            model.addAttribute("priorities", Priority.values());
            return "ticket-form";
        }
        Ticket saved = tickets.create(form.toTicket(), authentication.getName());
        if (isTechnician(authentication)) {
            redirect.addFlashAttribute("success", "Ticket created successfully.");
            return "redirect:/tickets/" + saved.getId();
        }
        redirect.addFlashAttribute("success", "Your ticket was submitted successfully. Reference #" + saved.getId() + ".");
        redirect.addFlashAttribute("submittedTicketId", saved.getId());
        return "redirect:/tickets/new";
    }

    @GetMapping("/my-tickets")
    String myTickets(Authentication authentication, Model model) {
        model.addAttribute("tickets", tickets.findMyTickets(authentication.getName()));
        return "my-tickets";
    }

    @GetMapping("/my-tickets/{id}")
    String myTicketProgress(@PathVariable Long id, Authentication authentication, Model model) {
        Ticket ticket = tickets.getMyTicket(id, authentication.getName());
        model.addAttribute("ticket", ticket);
        model.addAttribute("history", tickets.progressHistory(id, authentication.getName()));
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("currentStep", ticket.getStatus().ordinal());
        return "my-ticket-progress";
    }

    @GetMapping("/tickets/{id}")
    String ticketDetail(@PathVariable Long id, Model model) {
        model.addAttribute("ticket", tickets.get(id));
        model.addAttribute("history", tickets.history(id));
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("technicians", users.findByRole(Role.TECHNICIAN));
        return "ticket-detail";
    }

    @GetMapping("/tickets/{id}/edit")
    String editTicket(@PathVariable Long id, Model model) {
        Ticket ticket = tickets.get(id);
        model.addAttribute("ticket", ticket);
        model.addAttribute("form", TicketEditForm.fromTicket(ticket));
        model.addAttribute("categories", Category.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("technicians", users.findByRole(Role.TECHNICIAN));
        return "ticket-edit";
    }

    @PostMapping("/tickets/{id}/edit")
    String saveTicket(@PathVariable Long id, @Valid @ModelAttribute("form") TicketEditForm form,
                      BindingResult result, Authentication authentication, Model model,
                      RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("ticket", tickets.get(id));
            model.addAttribute("categories", Category.values());
            model.addAttribute("priorities", Priority.values());
            model.addAttribute("statuses", TicketStatus.values());
            model.addAttribute("technicians", users.findByRole(Role.TECHNICIAN));
            return "ticket-edit";
        }
        tickets.updateTicket(id, form, authentication.getName());
        redirect.addFlashAttribute("success", "Ticket updated successfully.");
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/status")
    String updateStatus(@PathVariable Long id, @RequestParam TicketStatus status,
                        @RequestParam(required = false) String notes, Authentication authentication,
                        RedirectAttributes redirect) {
        tickets.updateStatus(id, status, notes, authentication.getName());
        redirect.addFlashAttribute("success", "Ticket status updated.");
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/assign")
    String assignTicket(@PathVariable Long id, @RequestParam Long technicianId, Authentication authentication,
                        RedirectAttributes redirect) {
        tickets.assign(id, technicianId, authentication.getName());
        redirect.addFlashAttribute("success", "Ticket assigned successfully.");
        return "redirect:/tickets/" + id;
    }

    @GetMapping("/assets")
    String assetList(@RequestParam(required = false) String q,
                     @RequestParam(required = false) AssetStatus status,
                     @RequestParam(required = false) String category, Model model) {
        model.addAttribute("assets", assetService.filter(q, status, category));
        model.addAttribute("q", q);
        model.addAttribute("statuses", AssetStatus.values());
        model.addAttribute("categories", assetService.categories());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("isTechnician", true);
        return "assets";
    }

    @GetMapping("/assets/new")
    String newAsset(Model model) {
        model.addAttribute("form", new AssetForm());
        populateAssetForm(model, false);
        return "asset-form";
    }

    @PostMapping("/assets")
    String createAsset(@Valid @ModelAttribute("form") AssetForm form, BindingResult result,
                       Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            populateAssetForm(model, false);
            return "asset-form";
        }
        try {
            assetService.create(form);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            populateAssetForm(model, false);
            return "asset-form";
        }
        redirect.addFlashAttribute("success", "Asset registered successfully.");
        return "redirect:/assets";
    }

    @GetMapping("/assets/{id}/edit")
    String editAsset(@PathVariable Long id, Model model) {
        Asset asset = assetService.get(id);
        model.addAttribute("asset", asset);
        model.addAttribute("form", AssetForm.fromAsset(asset));
        populateAssetForm(model, true);
        return "asset-form";
    }

    @PostMapping("/assets/{id}/edit")
    String updateAsset(@PathVariable Long id, @Valid @ModelAttribute("form") AssetForm form,
                       BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("asset", assetService.get(id));
            populateAssetForm(model, true);
            return "asset-form";
        }
        try {
            assetService.update(id, form);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("asset", assetService.get(id));
            model.addAttribute("error", ex.getMessage());
            populateAssetForm(model, true);
            return "asset-form";
        }
        redirect.addFlashAttribute("success", "Asset updated successfully.");
        return "redirect:/assets";
    }

    @PostMapping("/assets/{id}/unassign")
    String unassignAsset(@PathVariable Long id, RedirectAttributes redirect) {
        assetService.unassign(id);
        redirect.addFlashAttribute("success", "Asset unassigned and returned to inventory.");
        return "redirect:/assets";
    }

    private void populateAssetForm(Model model, boolean isEdit) {
        model.addAttribute("statuses", AssetStatus.values());
        model.addAttribute("users", users.findByRole(Role.EMPLOYEE));
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("isTechnician", true);
    }

    private boolean isTechnician(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_TECHNICIAN".equals(authority.getAuthority()));
    }
}
