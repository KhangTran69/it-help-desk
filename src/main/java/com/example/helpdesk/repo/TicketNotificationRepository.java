package com.example.helpdesk.repo;

import com.example.helpdesk.model.TicketNotification;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TicketNotificationRepository extends JpaRepository<TicketNotification, Long> {

    Page<TicketNotification> findByRecipientUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    long countByRecipientUsernameAndReadFalse(String username);

    Optional<TicketNotification> findByIdAndRecipientUsername(Long id, String username);

    /** Bulk read-state update; returns how many notifications flipped from unread to read. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update TicketNotification n set n.read = true where n.recipient.username = :username and n.read = false")
    int markAllReadByUsername(@Param("username") String username);
}
