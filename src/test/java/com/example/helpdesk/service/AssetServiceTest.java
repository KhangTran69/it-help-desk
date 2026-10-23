package com.example.helpdesk.service;

import com.example.helpdesk.dto.AssetForm;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.model.Asset;
import com.example.helpdesk.model.AssetStatus;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.AssetRepository;
import com.example.helpdesk.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    AssetRepository repo;
    @Mock
    UserRepository users;

    private AssetService service;
    private User testUser;

    @BeforeEach
    void setUp() {
        service = new AssetService(repo, users);
        testUser = new User("alice", "pass", "Alice Adams", Role.EMPLOYEE);
        testUser.setId(101L);
    }

    @Test
    void createAssetSuccess() {
        AssetForm form = new AssetForm();
        form.setAssetTag("AST-1001");
        form.setName("MacBook Pro 16");
        form.setCategory("Laptop");
        form.setStatus(AssetStatus.AVAILABLE);
        form.setVersion(0L);

        when(repo.existsByAssetTag("AST-1001")).thenReturn(false);
        when(repo.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        Asset created = service.create(form);
        assertEquals("AST-1001", created.getAssetTag());
        assertEquals("MacBook Pro 16", created.getName());
        assertEquals(AssetStatus.AVAILABLE, created.getStatus());
        verify(repo).save(any(Asset.class));
    }

    @Test
    void createAssetDuplicateTagThrowsException() {
        AssetForm form = new AssetForm();
        form.setAssetTag("AST-DUPLICATE");
        form.setName("Monitor");
        form.setCategory("Display");

        when(repo.existsByAssetTag("AST-DUPLICATE")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(form));
        verify(repo, never()).save(any(Asset.class));
    }

    @Test
    void editExistingAssetPreservesTagWithoutDuplicateError() {
        Asset existing = new Asset();
        existing.setId(10L);
        existing.setAssetTag("AST-KEEP");
        existing.setName("Dell Latitude");
        existing.setCategory("Laptop");
        existing.setStatus(AssetStatus.AVAILABLE);

        when(repo.findById(10L)).thenReturn(Optional.of(existing));
        when(repo.existsByAssetTagAndIdNot("AST-KEEP", 10L)).thenReturn(false);
        when(repo.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetForm form = new AssetForm();
        form.setAssetTag("AST-KEEP");
        form.setName("Dell Latitude 5540 (Upgraded RAM)");
        form.setCategory("Laptop");
        form.setStatus(AssetStatus.AVAILABLE);
        form.setVersion(0L);

        Asset updated = service.update(10L, form);

        assertEquals("Dell Latitude 5540 (Upgraded RAM)", updated.getName());
        assertEquals("AST-KEEP", updated.getAssetTag());
        verify(repo).save(existing);
    }

    @Test
    void editAssetDuplicateTagThrowsException() {
        Asset existing = new Asset();
        existing.setId(10L);
        existing.setAssetTag("AST-OLD");

        when(repo.findById(10L)).thenReturn(Optional.of(existing));
        when(repo.existsByAssetTagAndIdNot("AST-TAKEN", 10L)).thenReturn(true);

        AssetForm form = new AssetForm();
        form.setAssetTag("AST-TAKEN");
        form.setName("Keyboard");
        form.setCategory("Peripherals");
        form.setStatus(AssetStatus.AVAILABLE);
        form.setVersion(0L);

        assertThrows(IllegalArgumentException.class, () -> service.update(10L, form));
        verify(repo, never()).save(existing);
    }

    @Test
    void staleAssetEditIsRejected() {
        Asset existing = new Asset();
        existing.setId(10L);
        existing.setVersion(3L);
        existing.setAssetTag("AST-OLD");
        when(repo.findById(10L)).thenReturn(Optional.of(existing));
        AssetForm form = new AssetForm();
        form.setAssetTag("AST-OLD");
        form.setName("Old laptop");
        form.setCategory("Laptop");
        form.setStatus(AssetStatus.AVAILABLE);
        form.setVersion(2L);
        assertThrows(com.example.helpdesk.exception.AssetEditConflictException.class, () -> service.update(10L, form));
        verify(repo, never()).save(any(Asset.class));
    }

    @Test
    void retiredAssetCannotBeAssigned() {
        AssetForm form = new AssetForm();
        form.setAssetTag("AST-RETIRED");
        form.setName("Old Server");
        form.setCategory("Server");
        form.setStatus(AssetStatus.RETIRED);
        form.setAssignedUserId(101L);
        form.setVersion(0L);

        assertThrows(IllegalArgumentException.class, () -> service.create(form));
    }

    @Test
    void assignUserTransitionsStatusToAssigned() {
        Asset existing = new Asset();
        existing.setId(20L);
        existing.setAssetTag("AST-20");
        existing.setName("iPad Pro");
        existing.setCategory("Tablet");
        existing.setStatus(AssetStatus.AVAILABLE);

        when(repo.findById(20L)).thenReturn(Optional.of(existing));
        when(repo.existsByAssetTagAndIdNot("AST-20", 20L)).thenReturn(false);
        when(users.findById(101L)).thenReturn(Optional.of(testUser));
        when(repo.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetForm form = new AssetForm();
        form.setAssetTag("AST-20");
        form.setName("iPad Pro");
        form.setCategory("Tablet");
        form.setStatus(AssetStatus.AVAILABLE);
        form.setAssignedUserId(101L);
        form.setVersion(0L);

        Asset updated = service.update(20L, form);

        assertSame(testUser, updated.getAssignedUser());
        assertEquals(AssetStatus.ASSIGNED, updated.getStatus());
    }

    @Test
    void unassignAssetSetsStatusToAvailable() {
        Asset existing = new Asset();
        existing.setId(30L);
        existing.setAssetTag("AST-30");
        existing.setName("Lenovo ThinkPad");
        existing.setCategory("Laptop");
        existing.setAssignedUser(testUser);
        existing.setStatus(AssetStatus.ASSIGNED);

        when(repo.findById(30L)).thenReturn(Optional.of(existing));
        when(repo.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        service.unassign(30L);

        assertNull(existing.getAssignedUser());
        assertEquals(AssetStatus.AVAILABLE, existing.getStatus());
        verify(repo).save(existing);
    }

    @Test
    void nonexistentAssetThrowsResourceNotFoundException() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        AssetForm form = new AssetForm();
        form.setAssetTag("AST-X");
        form.setName("Unknown");
        form.setCategory("Other");
        form.setStatus(AssetStatus.AVAILABLE);

        assertThrows(ResourceNotFoundException.class, () -> service.update(999L, form));
    }

    @Test
    void saveCountsCreationEvenWhenRepositoryAssignsIdImmediately() {
        com.example.helpdesk.metrics.BusinessMetrics metrics = mock(com.example.helpdesk.metrics.BusinessMetrics.class);
        AssetService measuredService = new AssetService(repo, users, metrics);
        Asset asset = new Asset();
        asset.setAssetTag("AST-METRIC");
        asset.setName("Monitor");
        when(repo.existsByAssetTag("AST-METRIC")).thenReturn(false);
        when(repo.save(asset)).thenAnswer(invocation -> {
            asset.setId(77L);
            return asset;
        });
        measuredService.save(asset);
        verify(metrics).incrementAssetCreated();
    }
}
