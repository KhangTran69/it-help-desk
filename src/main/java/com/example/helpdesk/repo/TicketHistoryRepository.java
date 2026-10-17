package com.example.helpdesk.repo;

import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {
    List<TicketHistory> findByTicketOrderByCreatedAtDesc(Ticket t);
    List<TicketHistory> findByTicketOrderByCreatedAtAsc(Ticket t);
}
