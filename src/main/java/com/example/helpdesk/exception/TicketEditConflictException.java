package com.example.helpdesk.exception;

public class TicketEditConflictException extends RuntimeException {
    public TicketEditConflictException() {
        super("This ticket was updated by someone else while you were editing. Reload it and review the latest changes.");
    }
}
