package com.example.helpdesk.repo;

import com.example.helpdesk.model.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {
    List<TicketAttachment> findByTicketIdOrderByUploadedAtAsc(Long ticketId);
}
