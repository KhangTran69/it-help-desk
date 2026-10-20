package com.example.helpdesk.web;

import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.exception.TicketEditConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ResourceNotFoundException ex, Model model) {
        log.warn("Resource not found: {}", ex.getMessage());
        model.addAttribute("status", 404);
        model.addAttribute("error", "Resource Not Found");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleMissingStaticResource(NoResourceFoundException ex, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("error", "Page not found");
        model.addAttribute("message", "The requested page or resource could not be found.");
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        log.warn("Access denied: {}", ex.getMessage());
        model.addAttribute("status", 403);
        model.addAttribute("error", "Access Denied");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidInput(IllegalArgumentException ex, Model model) {
        model.addAttribute("status", 400);
        model.addAttribute("error", "Invalid request");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleMalformedRequest(Exception ex, Model model) {
        model.addAttribute("status", 400);
        model.addAttribute("error", "Invalid request");
        model.addAttribute("message", "A request value is invalid or missing. Check your input and try again.");
        return "error";
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public String handleUnsupportedMethod(HttpRequestMethodNotSupportedException ex, Model model,
                                          jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Unsupported method {} for {}", ex.getMethod(), request.getRequestURI());
        model.addAttribute("status", 405);
        model.addAttribute("error", "Action not available");
        model.addAttribute("message", "That action is not supported here. Please use the page controls and try again.");
        return "error";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleConflict(DataIntegrityViolationException ex, Model model) {
        model.addAttribute("status", 409);
        model.addAttribute("error", "Unable to save changes");
        model.addAttribute("message", "A record with the same unique value already exists.");
        return "error";
    }

    @ExceptionHandler(TicketEditConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleEditConflict(TicketEditConflictException ex, Model model) {
        model.addAttribute("status", 409);
        model.addAttribute("error", "Ticket changed by another technician");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleOptimisticLock(Exception ex, Model model) {
        model.addAttribute("status", 409);
        model.addAttribute("error", "Record changed by another user");
        model.addAttribute("message", "Someone saved a newer version while you were making changes. Reload the record before trying again.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericError(Exception ex, Model model) {
        log.error("Internal Server Error occurred: ", ex);
        model.addAttribute("status", 500);
        model.addAttribute("error", "Internal Server Error");
        model.addAttribute("message", "An unexpected error occurred. Please try again.");
        return "error";
    }
}
