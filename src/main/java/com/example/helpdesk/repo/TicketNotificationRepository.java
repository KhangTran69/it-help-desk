package com.example.helpdesk.repo;

import com.example.helpdesk.model.TicketNotification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TicketNotificationRepository extends JpaRepository<TicketNotification, Long> {

    List<TicketNotification> findByRecipientUsernameOrderByCreatedAtDesc(String username);

    long countByRecipientUsernameAndReadFalse(String username);

    List<TicketNotification> findByRecipientUsernameAndReadFalse(String username);

    Optional<TicketNotification> findByIdAndRecipientUsername(Long id, String username);

    /** Bulk read-state update; returns how many notifications flipped from unread to read. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update TicketNotification n set n.read = true where n.recipient.username = :username and n.read = false")
    int markAllReadByUsername(@Param("username") String username);
}
