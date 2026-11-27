package com.example.helpdesk.service;

import com.example.helpdesk.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Optional outbound email for notifications. Delivery is a best-effort side
 * effect of an in-app notification: no SMTP host configured, a recipient
 * without an email address, or a provider failure never interrupts the
 * ticket action that triggered it.
 */
@Component
public class MailNotifier {

    private static final Logger log = LoggerFactory.getLogger(MailNotifier.class);

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final boolean enabled;
    private final String from;

    public MailNotifier(
            ObjectProvider<JavaMailSender> mailSenders,
            @Value("${helpdesk.mail.enabled:false}") boolean enabled,
            @Value("${helpdesk.mail.from:no-reply@it-helpdesk.local}") String from) {
        this.mailSenders = mailSenders;
        this.enabled = enabled;
        this.from = from;
    }

    public void send(User recipient, String subject, String body) {
        if (!enabled
                || recipient == null
                || recipient.getEmail() == null
                || recipient.getEmail().isBlank()) {
            return;
        }
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null) {
            log.debug("Mail enabled but no JavaMailSender configured; skipping email to {}", recipient.getUsername());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipient.getEmail());
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("Notification email sent to {}", recipient.getUsername());
        } catch (RuntimeException ex) {
            log.warn("Could not deliver notification email to {}: {}", recipient.getUsername(), ex.getMessage());
        }
    }
}
