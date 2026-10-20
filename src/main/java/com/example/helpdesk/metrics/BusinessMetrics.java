package com.example.helpdesk.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final Counter ticketCreatedCounter;
    private final Counter ticketResolvedCounter;
    private final Counter ticketClosedCounter;
    private final Counter assetCreatedCounter;
    private final Counter assetAssignedCounter;

    public BusinessMetrics(MeterRegistry registry) {
        this.ticketCreatedCounter = Counter.builder("helpdesk.tickets.created")
                .description("Number of tickets created")
                .register(registry);

        this.ticketResolvedCounter = Counter.builder("helpdesk.tickets.resolved")
                .description("Number of tickets resolved")
                .register(registry);

        this.ticketClosedCounter = Counter.builder("helpdesk.tickets.closed")
                .description("Number of tickets closed")
                .register(registry);

        this.assetCreatedCounter = Counter.builder("helpdesk.assets.created")
                .description("Number of assets created")
                .register(registry);

        this.assetAssignedCounter = Counter.builder("helpdesk.assets.assigned")
                .description("Number of assets assigned")
                .register(registry);
    }

    public void incrementTicketCreated() {
        ticketCreatedCounter.increment();
    }

    public void incrementTicketResolved() {
        ticketResolvedCounter.increment();
    }

    public void incrementTicketClosed() {
        ticketClosedCounter.increment();
    }

    public void incrementAssetCreated() {
        assetCreatedCounter.increment();
    }

    public void incrementAssetAssigned() {
        assetAssignedCounter.increment();
    }
}
