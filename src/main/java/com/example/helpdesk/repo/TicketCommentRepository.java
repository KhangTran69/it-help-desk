package com.example.helpdesk.repo;

import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketComment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

    List<TicketComment> findByTicketAndInternalNoteFalseOrderByCreatedAtAsc(Ticket ticket);

    List<TicketComment> findByTicketOrderByCreatedAtAsc(Ticket ticket);

    Page<TicketComment> findByTicketAndInternalNoteFalse(Ticket ticket, Pageable pageable);

    Page<TicketComment> findByTicket(Ticket ticket, Pageable pageable);

    Optional<TicketComment> findByIdAndTicketIdAndAuthorUsername(Long id, Long ticketId, String username);
}
