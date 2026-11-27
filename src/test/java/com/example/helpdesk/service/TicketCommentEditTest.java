package com.example.helpdesk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketComment;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.TicketCommentRepository;
import com.example.helpdesk.repo.TicketHistoryRepository;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class TicketCommentEditTest {

    @Mock
    private TicketCommentRepository comments;

    @Mock
    private UserRepository users;

    private TicketService service;

    @BeforeEach
    void setUp() {
        service = new TicketService(
                mock(TicketRepository.class),
                users,
                mock(TicketHistoryRepository.class),
                comments,
                null,
                null,
                new SlaPolicy(Clock.systemUTC(), 1209600, 259200, 86400));
    }

    private User author() {
        User author = new User("alice", "pw", "Alice", Role.EMPLOYEE);
        author.setId(1L);
        return author;
    }

    private TicketComment comment(User author, LocalDateTime createdAt) {
        Ticket ticket = new Ticket();
        ticket.setId(5L);
        TicketComment comment = new TicketComment(ticket, author, "Original text", false);
        comment.setCreatedAtForTesting(createdAt);
        return comment;
    }

    @Test
    void authorEditsOwnCommentWithinTheWindow() {
        User author = author();
        TicketComment stored = comment(author, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2));
        when(comments.findByIdAndTicketIdAndAuthorUsername(9L, 5L, "alice"))
                .thenReturn(Optional.of(stored));
        when(users.findByUsername("alice")).thenReturn(Optional.of(author));
        when(comments.save(any(TicketComment.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketComment updated = service.editOwnComment(5L, 9L, "alice", "Corrected text");

        assertEquals("Corrected text", updated.getContent());
        assertNotNull(updated.getLastEditedAt());
    }

    @Test
    void editsOutsideTheFifteenMinuteWindowAreRejected() {
        User author = author();
        TicketComment stored = comment(author, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(20));
        when(comments.findByIdAndTicketIdAndAuthorUsername(9L, 5L, "alice"))
                .thenReturn(Optional.of(stored));
        when(users.findByUsername("alice")).thenReturn(Optional.of(author));

        assertThrows(
                AccessDeniedException.class, () -> service.editOwnComment(5L, 9L, "alice", "Too late"));
        verify(comments, never()).save(any(TicketComment.class));
    }

    @Test
    void anotherAuthorsCommentIsNotFoundInsteadOfLeaked() {
        when(comments.findByIdAndTicketIdAndAuthorUsername(9L, 5L, "mallory"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.editOwnComment(5L, 9L, "mallory", "Hijack"));
        verify(comments, never()).save(any(TicketComment.class));
    }

    @Test
    void blankOrOversizedEditsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.editOwnComment(5L, 9L, "alice", "   "));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.editOwnComment(5L, 9L, "alice", "x".repeat(5001)));
        verify(comments, never()).findByIdAndTicketIdAndAuthorUsername(any(), any(), any());
    }
}
