package com.example.helpdesk.service;

import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketAttachment;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.TicketAttachmentRepository;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketAttachmentServiceTest {

    @Mock
    private TicketAttachmentRepository repository;
    @Mock
    private TicketRepository tickets;
    @Mock
    private UserRepository users;
    @InjectMocks
    private TicketAttachmentService service;

    private User user(String username, Role role) {
        return new User(username, "pw", username, role);
    }

    @Test
    void storesAttachmentForTicketWithUploader() throws IOException {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        User uploader = user("technician", Role.TECHNICIAN);
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket));
        when(users.findByUsername("technician")).thenReturn(Optional.of(uploader));

        MultipartFile file = stubbedFile("invoice.pdf", 1234, new byte[]{1, 2});
        service.store(1L, file, "technician");

        ArgumentCaptor<TicketAttachment> captured = ArgumentCaptor.forClass(TicketAttachment.class);
        verify(repository).save(captured.capture());
        assertThat(captured.getValue().getFilename()).isEqualTo("invoice.pdf");
        assertThat(captured.getValue().getSizeBytes()).isEqualTo(1234);
        assertThat(captured.getValue().getContentType()).isEqualTo("application/pdf");
        assertThat(captured.getValue().getTicket()).isSameAs(ticket);
        assertThat(captured.getValue().getUploadedBy()).isSameAs(uploader);
    }

    @Test
    void rejectsOversizedAndDisallowedFiles() {
        when(tickets.findById(1L)).thenReturn(Optional.of(new Ticket()));
        when(users.findByUsername("tech")).thenReturn(Optional.of(user("tech", Role.TECHNICIAN)));

        assertThatThrownBy(() -> service.store(1L, stubbedFile("huge.bin", TicketAttachmentService.MAX_SIZE_BYTES + 1, new byte[]{}), "tech"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB");
        verify(repository, never()).save(any());

        assertThatThrownBy(() -> service.store(1L, stubbedFile("virus.exe", 100, new byte[]{}), "tech"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> service.store(1L, stubbedFile("noextension", 10, new byte[]{}), "tech"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void downloadAllowsTheTicketReporter() {
        Ticket ticket = new Ticket();
        User reporter = user("employee", Role.EMPLOYEE);
        ticket.setReporter(reporter);
        TicketAttachment attachment = new TicketAttachment(ticket, "note.pdf", "application/pdf", 3,
                reporter, new byte[]{9});
        when(repository.findById(7L)).thenReturn(Optional.of(attachment));
        when(users.findByUsername("employee")).thenReturn(Optional.of(reporter));

        assertThat(service.readableContent(1L, 7L, "employee")).isSameAs(attachment);
    }

    @Test
    void downloadHidesAttachmentsFromOtherEmployees() {
        Ticket ticket = new Ticket();
        User reporter = user("employee", Role.EMPLOYEE);
        User stranger = user("stranger", Role.EMPLOYEE);
        ticket.setReporter(reporter);
        TicketAttachment attachment = new TicketAttachment(ticket, "note.pdf", "application/pdf", 3,
                reporter, new byte[]{9});
        when(repository.findById(7L)).thenReturn(Optional.of(attachment));
        when(users.findByUsername("stranger")).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> service.readableContent(1L, 7L, "stranger"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void treatsPathTraversalNamesAsPlainFilenames() throws IOException {
        Ticket ticket = new Ticket();
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket));
        when(users.findByUsername("tech")).thenReturn(Optional.of(user("tech", Role.TECHNICIAN)));

        MultipartFile traversal = stubbedFile("../../etc/passwd", 10, new byte[]{});
        assertThatThrownBy(() -> service.store(1L, traversal, "tech"))
                .isInstanceOf(IllegalArgumentException.class);

        MultipartFile nested = stubbedFile("..\\docs\\fine notes.pdf", 10, new byte[]{4});
        service.store(1L, nested, "tech");
        ArgumentCaptor<TicketAttachment> captured = ArgumentCaptor.forClass(TicketAttachment.class);
        verify(repository).save(captured.capture());
        assertThat(captured.getValue().getFilename()).isEqualTo("fine notes.pdf");
    }

    @Test
    void missingTicketOrEmptyFileIsRejected() {
        when(tickets.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.store(99L, org.mockito.Mockito.mock(MultipartFile.class), "tech"))
                .isInstanceOf(ResourceNotFoundException.class);

        MultipartFile empty = org.mockito.Mockito.mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);
        when(tickets.findById(1L)).thenReturn(Optional.of(new Ticket()));
        when(users.findByUsername("tech")).thenReturn(Optional.of(user("tech", Role.TECHNICIAN)));
        assertThatThrownBy(() -> service.store(1L, empty, "tech"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    private MultipartFile stubbedFile(String name, long size, byte[] content) {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        org.mockito.Mockito.lenient().when(file.getOriginalFilename()).thenReturn(name);
        when(file.getSize()).thenReturn(size);
        org.mockito.Mockito.lenient().when(file.getContentType()).thenReturn("application/pdf");
        try {
            org.mockito.Mockito.lenient().when(file.getBytes()).thenReturn(content);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return file;
    }
}
