package com.example.helpdesk.web;

import com.example.helpdesk.dto.TicketCreateForm;
import com.example.helpdesk.dto.TicketEditForm;
import com.example.helpdesk.model.*;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.service.TicketAttachmentService;
import com.example.helpdesk.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

@Controller
public class TicketPageController {
    private final TicketService tickets;
    private final UserRepository users;
    private final TicketAttachmentService attachments;

    public TicketPageController(TicketService tickets, UserRepository users, TicketAttachmentService attachments) {
        this.tickets = tickets;
        this.users = users;
        this.attachments = attachments;
    }

    @GetMapping("/tickets")
    String ticketList(@RequestParam(required = false) String q,
                      @RequestParam(required = false) TicketStatus status,
                      @RequestParam(required = false) String statusGroup,
                      @RequestParam(required = false) Priority priority,
                      @RequestParam(required = false) Category category,
                      @RequestParam(required = false) Long technicianId,
                      @RequestParam(required = false) Boolean unassigned,
                      @RequestParam(defaultValue = "false") boolean overdue,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "20") int size,
                      @RequestParam(defaultValue = "updatedAt") String sort,
                      @RequestParam(defaultValue = "desc") String dir,
                      Model model) {
        Pageable pageable = pageable(page, size, sort, dir,
                Set.of("updatedAt", "createdAt", "priority", "status", "title"), "updatedAt");
        boolean resolvedClosed = "resolved-closed".equals(statusGroup);
        if (statusGroup != null && !resolvedClosed) throw new IllegalArgumentException("Unknown ticket status group");
        model.addAttribute("tickets", tickets.searchPage(status, priority, category, technicianId, unassigned,
                q, resolvedClosed, overdue, pageable));
        model.addAttribute("statusGroup", resolvedClosed ? statusGroup : null);
        model.addAttribute("q", q);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedTechnicianId", technicianId);
        model.addAttribute("unassigned", unassigned);
        model.addAttribute("overdue", overdue);
        model.addAttribute("sort", pageable.getSort().stream().findFirst().map(Sort.Order::getProperty).orElse("updatedAt"));
        model.addAttribute("dir", pageable.getSort().stream().findFirst().map(o -> o.getDirection().name().toLowerCase()).orElse("desc"));
        model.addAttribute("size", pageable.getPageSize());
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("categories", Category.values());
        model.addAttribute("technicians", users.findByRole(Role.TECHNICIAN));
        return "tickets";
    }

    @GetMapping("/tickets/new")
    String newTicket(Authentication authentication, Model model) {
        model.addAttribute("form", new TicketCreateForm());
        populateTicketForm(model);
        model.addAttribute("isTechnician", isTechnician(authentication));
        return "ticket-form";
    }

    @PostMapping("/tickets")
    String createTicket(@Valid @ModelAttribute("form") TicketCreateForm form, BindingResult result,
                        Authentication authentication, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            populateTicketForm(model);
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
    String myTickets(Authentication authentication, @RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "10") int size, Model model) {
        model.addAttribute("tickets", tickets.findMyTickets(authentication.getName(),
                pageable(page, size, "updatedAt", "desc", Set.of("updatedAt"), "updatedAt")));
        return "my-tickets";
    }

    @GetMapping("/my-tickets/{id}")
    String myTicketProgress(@PathVariable Long id, Authentication authentication, Model model) {
        Ticket ticket = tickets.getMyTicket(id, authentication.getName());
        model.addAttribute("ticket", ticket);
        model.addAttribute("history", tickets.progressHistory(id, authentication.getName()));
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("currentStep", ticket.getStatus().ordinal());
        model.addAttribute("comments", tickets.publicComments(id, authentication.getName()));
        model.addAttribute("attachments", attachments.listForTicket(id));
        return "my-ticket-progress";
    }

    @PostMapping("/my-tickets/{id}/comments")
    String employeeComment(@PathVariable Long id, @RequestParam String content, Authentication authentication,
                           RedirectAttributes redirect) {
        tickets.addEmployeeComment(id, authentication.getName(), content);
        redirect.addFlashAttribute("success", "Your comment was added.");
        return "redirect:/my-tickets/" + id;
    }

    @GetMapping("/tickets/{id}")
    String ticketDetail(@PathVariable Long id, Model model) {
        Ticket ticket = tickets.get(id);
        model.addAttribute("ticket", ticket);
        model.addAttribute("history", tickets.history(id));
        model.addAttribute("comments", tickets.allComments(id));
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("technicians", users.findByRole(Role.TECHNICIAN));
        model.addAttribute("version", ticket.getVersion());
        model.addAttribute("attachments", attachments.listForTicket(id));
        return "ticket-detail";
    }

    @PostMapping("/tickets/{id}/attachments")
    String uploadAttachment(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                            Authentication authentication, RedirectAttributes redirect) {
        attachments.store(id, file, authentication.getName());
        redirect.addFlashAttribute("success", "Attachment added.");
        return "redirect:/tickets/" + id;
    }

    @GetMapping("/tickets/{id}/attachments/{attachmentId}/download")
    ResponseEntity<Resource> downloadAttachment(@PathVariable Long id, @PathVariable Long attachmentId,
                                                Authentication authentication) {
        return attachmentResource(attachments.readableContent(id, attachmentId, authentication.getName()));
    }

    private ResponseEntity<Resource> attachmentResource(TicketAttachment attachment) {
        ByteArrayResource body = new ByteArrayResource(attachment.getContent());
        String contentDisposition = ContentDisposition.builder("attachment")
                .filename(attachment.getFilename())
                .build().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSizeBytes())
                .body(body);
    }

    @PostMapping("/tickets/{id}/comments")
    String technicianComment(@PathVariable Long id, @RequestParam String content,
                             @RequestParam(defaultValue = "false") boolean internalNote,
                             Authentication authentication, RedirectAttributes redirect) {
        tickets.addTechnicianComment(id, authentication.getName(), content, internalNote);
        redirect.addFlashAttribute("success", internalNote ? "Internal note added." : "Reply added.");
        return "redirect:/tickets/" + id;
    }

    @GetMapping("/tickets/{id}/edit")
    String editTicket(@PathVariable Long id, Model model) {
        Ticket ticket = tickets.get(id);
        model.addAttribute("ticket", ticket);
        model.addAttribute("form", TicketEditForm.fromTicket(ticket));
        populateTicketForm(model);
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("technicians", users.findByRole(Role.TECHNICIAN));
        return "ticket-edit";
    }

    @PostMapping("/tickets/{id}/edit")
    String saveTicket(@PathVariable Long id, @Valid @ModelAttribute("form") TicketEditForm form,
                      BindingResult result, Authentication authentication, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("ticket", tickets.get(id));
            populateTicketForm(model);
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
                        @RequestParam(required = false) String notes, @RequestParam Long version,
                        Authentication authentication, RedirectAttributes redirect) {
        tickets.updateStatus(id, status, notes, authentication.getName(), version);
        redirect.addFlashAttribute("success", "Ticket status updated.");
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/assign")
    String assignTicket(@PathVariable Long id, @RequestParam Long technicianId, @RequestParam Long version,
                        Authentication authentication, RedirectAttributes redirect) {
        tickets.assign(id, technicianId, authentication.getName(), version);
        redirect.addFlashAttribute("success", "Ticket assigned successfully.");
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/my-tickets/{id}/attachments")
    String uploadMyAttachment(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                              Authentication authentication, RedirectAttributes redirect) {
        attachments.store(id, file, authentication.getName());
        redirect.addFlashAttribute("success", "Attachment added.");
        return "redirect:/my-tickets/" + id;
    }

    @GetMapping("/my-tickets/{id}/attachments/{attachmentId}/download")
    ResponseEntity<Resource> downloadMyAttachment(@PathVariable Long id, @PathVariable Long attachmentId,
                                                  Authentication authentication) {
        return attachmentResource(attachments.readableContent(id, attachmentId, authentication.getName()));
    }

    private void populateTicketForm(Model model) {
        model.addAttribute("categories", Category.values());
        model.addAttribute("priorities", Priority.values());
    }

    private boolean isTechnician(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_TECHNICIAN".equals(authority.getAuthority()));
    }

    private Pageable pageable(int page, int size, String sort, String direction, Set<String> allowed, String fallback) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String safeSort = allowed.contains(sort) ? sort : fallback;
        Sort.Direction safeDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(safeDirection, safeSort));
    }
}
