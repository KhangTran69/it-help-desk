package com.example.helpdesk.repo;

import com.example.helpdesk.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findTop10ByOrderByUpdatedAtDesc();
    List<Ticket> findByReporterOrderByUpdatedAtDesc(User user);
    List<Ticket> findByReporterUsernameOrderByUpdatedAtDesc(String username);
    Page<Ticket> findByReporterUsername(String username, Pageable pageable);
    Optional<Ticket> findByIdAndReporterUsername(Long id, String username);
    long countByStatus(TicketStatus status);
    long countByPriority(Priority priority);
    long countByTechnicianIsNull();
    long countByTechnician(User technician);
    Page<Ticket> findByReporter(User reporter, Pageable pageable);

    @Query("select t from Ticket t where lower(t.title) like lower(concat('%',:q,'%')) or cast(t.id as string)=:q order by t.updatedAt desc")
    List<Ticket> search(@Param("q") String query);

    @Query("SELECT t FROM Ticket t WHERE " +
           "(:reporter IS NULL OR t.reporter = :reporter) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "(:technician IS NULL OR t.technician = :technician) AND " +
           "(:unassigned IS NULL OR (:unassigned = true AND t.technician IS NULL) OR (:unassigned = false AND t.technician IS NOT NULL)) AND " +
           "(:q IS NULL OR :q = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')) OR CAST(t.id AS string) = :q) " +
           "ORDER BY t.updatedAt DESC")
    List<Ticket> filterTickets(@Param("reporter") User reporter, @Param("status") TicketStatus status,
                               @Param("priority") Priority priority, @Param("category") Category category,
                               @Param("technician") User technician, @Param("unassigned") Boolean unassigned,
                               @Param("q") String query);

    @Query(value = "SELECT t FROM Ticket t WHERE " +
           "(:reporter IS NULL OR t.reporter = :reporter) AND " +
           "(:resolvedClosed = false OR t.status IN (com.example.helpdesk.model.TicketStatus.RESOLVED, com.example.helpdesk.model.TicketStatus.CLOSED)) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "(:technician IS NULL OR t.technician = :technician) AND " +
           "(:unassigned IS NULL OR (:unassigned = true AND t.technician IS NULL) OR (:unassigned = false AND t.technician IS NOT NULL)) AND " +
           "(:q IS NULL OR :q = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')) OR CAST(t.id AS string) = :q)",
           countQuery = "SELECT COUNT(t) FROM Ticket t WHERE " +
           "(:reporter IS NULL OR t.reporter = :reporter) AND " +
           "(:resolvedClosed = false OR t.status IN (com.example.helpdesk.model.TicketStatus.RESOLVED, com.example.helpdesk.model.TicketStatus.CLOSED)) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "(:technician IS NULL OR t.technician = :technician) AND " +
           "(:unassigned IS NULL OR (:unassigned = true AND t.technician IS NULL) OR (:unassigned = false AND t.technician IS NOT NULL)) AND " +
           "(:q IS NULL OR :q = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')) OR CAST(t.id AS string) = :q)")
    Page<Ticket> pageTickets(@Param("reporter") User reporter, @Param("status") TicketStatus status,
                             @Param("priority") Priority priority, @Param("category") Category category,
                             @Param("technician") User technician, @Param("unassigned") Boolean unassigned,
                             @Param("q") String query, @Param("resolvedClosed") boolean resolvedClosed,
                             Pageable pageable);
}
