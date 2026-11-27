package com.example.helpdesk.repo;

import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {
    List<TicketHistory> findByTicketOrderByCreatedAtDesc(Ticket t);

    List<TicketHistory> findByTicketOrderByCreatedAtAsc(Ticket t);
}
