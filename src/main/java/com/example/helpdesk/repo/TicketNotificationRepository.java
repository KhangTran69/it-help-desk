package com.example.helpdesk.repo;

import com.example.helpdesk.model.TicketNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketNotificationRepository extends JpaRepository<TicketNotification, Long> {
    List<TicketNotification> findByRecipientUsernameOrderByCreatedAtDesc(String username);

    long countByRecipientUsernameAndReadFalse(String username);

    List<TicketNotification> findByRecipientUsernameAndReadFalse(String username);
}
