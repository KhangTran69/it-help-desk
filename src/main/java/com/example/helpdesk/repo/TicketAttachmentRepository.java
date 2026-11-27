package com.example.helpdesk.repo;

import com.example.helpdesk.model.TicketAttachment;
import com.example.helpdesk.model.TicketAttachmentMetadata;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {
    @Query(
            """
            select new com.example.helpdesk.model.TicketAttachmentMetadata(
                attachment.id, attachment.ticket.id, attachment.filename, attachment.contentType, attachment.sizeBytes,
                uploader.username, uploader.fullName, attachment.uploadedAt)
            from TicketAttachment attachment
            join attachment.uploadedBy uploader
            where attachment.ticket.id = :ticketId
            order by attachment.uploadedAt asc
            """)
    List<TicketAttachmentMetadata> findMetadataByTicketId(@Param("ticketId") Long ticketId);
}
