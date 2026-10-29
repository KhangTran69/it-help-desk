package com.example.helpdesk.web;

import com.example.helpdesk.repo.TicketNotificationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NotificationPageController {

    private final TicketNotificationRepository notifications;

    public NotificationPageController(TicketNotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/notifications")
    String myNotifications(Authentication authentication, Model model) {
        model.addAttribute("notifications",
                notifications.findByRecipientUsernameOrderByCreatedAtDesc(authentication.getName()));
        return "notifications";
    }

    @PostMapping("/notifications/{id}/read")
    String markRead(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        notifications.findByRecipientUsernameOrderByCreatedAtDesc(authentication.getName()).stream()
                .filter(notification -> notification.getId().equals(id) && !notification.isRead())
                .findFirst()
                .ifPresent(notification -> {
                    notification.markRead();
                    notifications.save(notification);
                });
        redirect.addFlashAttribute("success", "Notification marked as read.");
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/read-all")
    String markAllRead(Authentication authentication, RedirectAttributes redirect) {
        notifications.findByRecipientUsernameAndReadFalse(authentication.getName())
                .forEach(notification -> {
                    notification.markRead();
                    notifications.save(notification);
                });
        redirect.addFlashAttribute("success", "All notifications marked as read.");
        return "redirect:/notifications";
    }
}
