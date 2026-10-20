package com.example.helpdesk.repo;

import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findByTicketAndInternalNoteFalseOrderByCreatedAtAsc(Ticket ticket);
    List<TicketComment> findByTicketOrderByCreatedAtAsc(Ticket ticket);
}
