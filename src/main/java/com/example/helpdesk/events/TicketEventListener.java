package com.example.helpdesk.events;

import com.example.helpdesk.model.TicketHistory;
import com.example.helpdesk.repo.TicketHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TicketEventListener {

    private static final Logger log = LoggerFactory.getLogger(TicketEventListener.class);
    private final TicketHistoryRepository historyRepository;

    public TicketEventListener(TicketHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @EventListener
    public void handleTicketEvent(TicketEvent event) {
        log.info("Processing ticket event: {} for ticket {} by user {}",
                event.getAction(), event.getTicket().getId(), event.getActor().getUsername());

        TicketHistory history;
        if (event.getFieldChanged() != null) {
            history = new TicketHistory(
                    event.getTicket(),
                    event.getActor(),
                    event.getAction(),
                    event.getDetails(),
                    event.getFieldChanged(),
                    event.getPreviousValue(),
                    event.getNewValue()
            );
        } else {
            history = new TicketHistory(
                    event.getTicket(),
                    event.getActor(),
                    event.getAction(),
                    event.getDetails()
            );
        }

        historyRepository.save(history);
        log.info("Ticket history saved for event: {}", event.getAction());
    }
}
