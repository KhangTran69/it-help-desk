package com.example.helpdesk.repo;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.helpdesk.model.Ticket;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketRepositoryQueryTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Test
    void testFilterTicketsWithAllNulls() {
        List<Ticket> result = ticketRepository.filterTickets(null, null, null, null, null, null, null);
        assertNotNull(result);
    }

    @Test
    void testSearchAssetsWithAllNulls() {
        assertNotNull(assetRepository.searchAssets(null, null, null));
    }

    @Test
    void testFindDistinctCategories() {
        assertNotNull(assetRepository.findDistinctCategories());
    }
}
