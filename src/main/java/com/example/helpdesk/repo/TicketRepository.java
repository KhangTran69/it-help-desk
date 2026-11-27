package com.example.helpdesk.repo;

import com.example.helpdesk.model.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    boolean existsByTitle(String title);

    List<Ticket> findTop10ByOrderByUpdatedAtDesc();

    List<Ticket> findByReporterUsernameOrderByUpdatedAtDesc(String username);

    Page<Ticket> findByReporterUsername(String username, Pageable pageable);

    Optional<Ticket> findByIdAndReporterUsername(Long id, String username);

    long countByStatus(TicketStatus status);

    long countByPriority(Priority priority);

    long countByTechnicianIsNull();

    long countByTechnician(User technician);

    Page<Ticket> findByReporter(User reporter, Pageable pageable);

    @Query(
            "select count(t) from Ticket t where t.slaDueAt is not null and t.slaDueAt < :now and t.status not in (com.example.helpdesk.model.TicketStatus.RESOLVED, com.example.helpdesk.model.TicketStatus.CLOSED)")
    long countOverdue(@Param("now") LocalDateTime now);

    List<Ticket> findBySlaDueAtBeforeAndSlaBreachNotifiedAtIsNull(LocalDateTime now);
}
