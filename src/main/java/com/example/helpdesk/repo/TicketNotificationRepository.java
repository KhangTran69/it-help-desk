package com.example.helpdesk.repo;

import com.example.helpdesk.model.TicketNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketNotificationRepository extends JpaRepository<TicketNotification, Long> {
    List<TicketNotification> findByRecipientUsernameOrderByCreatedAtDesc(String username);

    long countByRecipientUsernameAndReadFalse(String username);

    List<TicketNotification> findByRecipientUsernameAndReadFalse(String username);
}
