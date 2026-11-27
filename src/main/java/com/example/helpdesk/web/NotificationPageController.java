package com.example.helpdesk.web;

import com.example.helpdesk.repo.TicketNotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NotificationPageController {

    private final TicketNotificationRepository notifications;

    public NotificationPageController(TicketNotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/notifications")
    String myNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        model.addAttribute(
                "isTechnician", authentication.getAuthorities().stream().anyMatch(authority -> "ROLE_TECHNICIAN"
                        .equals(authority.getAuthority())));
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(1, Math.min(size, 50)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute(
                "notifications",
                notifications.findByRecipientUsernameOrderByCreatedAtDesc(authentication.getName(), pageable));
        model.addAttribute("size", pageable.getPageSize());
        return "notifications";
    }

    @PostMapping("/notifications/{id}/read")
    String markRead(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        notifications
                .findByIdAndRecipientUsername(id, authentication.getName())
                .filter(notification -> !notification.isRead())
                .ifPresent(notification -> {
                    notification.markRead();
                    notifications.save(notification);
                });
        redirect.addFlashAttribute("success", "Notification marked as read.");
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/read-all")
    String markAllRead(Authentication authentication, RedirectAttributes redirect) {
        int updated = notifications.markAllReadByUsername(authentication.getName());
        redirect.addFlashAttribute(
                "success",
                updated == 1 ? "1 notification marked as read." : updated + " notifications marked as read.");
        return "redirect:/notifications";
    }
}
