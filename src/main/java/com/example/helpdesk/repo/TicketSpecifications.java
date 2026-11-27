package com.example.helpdesk.repo;

import com.example.helpdesk.model.Category;
import com.example.helpdesk.model.Priority;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Composable ticket queue filters, replacing the boolean-parameter mega-query. */
public final class TicketSpecifications {

    private TicketSpecifications() {}

    public static Specification<Ticket> withFilters(
            User reporter,
            TicketStatus status,
            Priority priority,
            Category category,
            User technician,
            Boolean unassigned,
            String query,
            boolean resolvedClosed,
            boolean overdue,
            LocalDateTime now) {
        Specification<Ticket> spec = (root, cq, cb) -> cb.conjunction();
        if (reporter != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("reporter"), reporter));
        }
        if (status != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("status"), status));
        }
        if (priority != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("priority"), priority));
        }
        if (category != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("category"), category));
        }
        if (technician != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("technician"), technician));
        }
        if (unassigned != null) {
            spec = spec.and((root, cq, cb) ->
                    unassigned ? cb.isNull(root.get("technician")) : cb.isNotNull(root.get("technician")));
        }
        if (resolvedClosed) {
            spec = spec.and((root, cq, cb) ->
                    root.get("status").in(TicketStatus.RESOLVED, TicketStatus.CLOSED));
        }
        if (overdue) {
            spec = spec.and((root, cq, cb) -> cb.and(
                    cb.isNotNull(root.get("slaDueAt")),
                    cb.lessThan(root.get("slaDueAt"), now),
                    root.get("status").in(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)));
        }
        if (query != null && !query.isBlank()) {
            String clean = query.trim();
            String like = "%" + clean.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, cq, cb) -> {
                List<Predicate> parts = new ArrayList<>();
                parts.add(cb.like(cb.lower(root.get("title")), like));
                parts.add(cb.like(cb.lower(root.get("description")), like));
                try {
                    parts.add(cb.equal(root.get("id"), Long.parseLong(clean)));
                } catch (NumberFormatException ignored) {
                    // Query is not a numeric id; title and description matching still apply.
                }
                return cb.or(parts.toArray(new Predicate[0]));
            });
        }
        return spec;
    }
}
