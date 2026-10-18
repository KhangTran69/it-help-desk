package com.example.helpdesk.events;

import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.User;
import org.springframework.context.ApplicationEvent;

public class TicketEvent extends ApplicationEvent {
    private final Ticket ticket;
    private final User actor;
    private final String action;
    private final String details;
    private final String fieldChanged;
    private final String previousValue;
    private final String newValue;

    public TicketEvent(Object source, Ticket ticket, User actor, String action, String details) {
        super(source);
        this.ticket = ticket;
        this.actor = actor;
        this.action = action;
        this.details = details;
        this.fieldChanged = null;
        this.previousValue = null;
        this.newValue = null;
    }

    public TicketEvent(Object source, Ticket ticket, User actor, String action, String details, String fieldChanged, String previousValue, String newValue) {
        super(source);
        this.ticket = ticket;
        this.actor = actor;
        this.action = action;
        this.details = details;
        this.fieldChanged = fieldChanged;
        this.previousValue = previousValue;
        this.newValue = newValue;
    }

    public Ticket getTicket() { return ticket; }
    public User getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public String getFieldChanged() { return fieldChanged; }
    public String getPreviousValue() { return previousValue; }
    public String getNewValue() { return newValue; }
}
