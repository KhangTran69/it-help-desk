package com.example.helpdesk.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.helpdesk.dto.AssetForm;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.model.Asset;
import com.example.helpdesk.model.AssetStatus;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.AssetRepository;
import com.example.helpdesk.repo.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        service = new AssetService(repo, users, null);
        testUser = new User("alice", "pass", "Alice Adams", Role.EMPLOYEE);
        testUser.setId(101L);
    }

    private AssetForm form(String tag, String name, String category, AssetStatus status) {
        AssetForm form = new AssetForm();
        form.setAssetTag(tag);
        form.setName(name);
        form.setCategory(category);
        form.setStatus(status);
        return form;
    }

    @Test
    void createAssetSuccess() {
        AssetForm form = form("AST-1001", "MacBook Pro 16", "Laptop", AssetStatus.AVAILABLE);

        when(repo.existsByAssetTag("AST-1001")).thenReturn(false);
        when(repo.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        Asset created = service.create(form);
        assertEquals("AST-1001", created.getAssetTag());
        assertEquals("MacBook Pro 16", created.getName());
        assertEquals(AssetStatus.AVAILABLE, created.getStatus());
        verify(repo).save(any(Asset.class));
    }

    @Test
    void createAssignedAssetWithoutEmployeeIsRejected() {
        AssetForm form = form("AST-UNASSIGNED", "Laptop", "Laptop", AssetStatus.ASSIGNED);
        when(repo.existsByAssetTag("AST-UNASSIGNED")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(form));

        assertEquals("Assigned assets must have an employee", ex.getMessage());
        verify(repo, never()).save(any(Asset.class));
    }

    @Test
    void updateKeepsAssignedStatusWhileEmployeeRemainsAssigned() {
        Asset existing = new Asset();
        existing.setId(21L);
        existing.setVersion(0L);
        existing.setAssetTag("AST-21");
        existing.setName("Assigned laptop");
        existing.setCategory("Laptop");
        existing.setAssignedUser(testUser);
        existing.setStatus(AssetStatus.ASSIGNED);

        when(repo.findById(21L)).thenReturn(Optional.of(existing));
        when(repo.existsByAssetTagAndIdNot("AST-21", 21L)).thenReturn(false);
        when(users.findById(101L)).thenReturn(Optional.of(testUser));
        when(repo.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetForm form = form("AST-21", "Assigned laptop", "Laptop", AssetStatus.AVAILABLE);
        form.setAssignedUserId(testUser.getId());
        form.setVersion(existing.getVersion());

        Asset updated = service.update(21L, form);

        assertSame(testUser, updated.getAssignedUser());
        assertEquals(AssetStatus.ASSIGNED, updated.getStatus());
    }

    @Test
    void createAssetDuplicateTagThrowsException() {
        AssetForm form = form("AST-DUPLICATE", "Monitor", "Display", AssetStatus.AVAILABLE);
        when(repo.existsByAssetTag("AST-DUPLICATE")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.create(form));
        verify(repo, never()).save(any(Asset.class));
    }

    @Test
    void editExistingAssetPreservesTagWithoutDuplicateError() {
        Asset existing = new Asset();
        existing.setId(10L);
        existing.setVersion(0L);
        existing.setAssetTag("AST-KEEP");
        existing.setName("Dell Latitude");
        existing.setCategory("Laptop");
        existing.setStatus(AssetStatus.AVAILABLE);

        when(repo.findById(10L)).thenReturn(Optional.of(existing));
        when(repo.existsByAssetTagAndIdNot("AST-KEEP", 10L)).thenReturn(false);
        when(repo.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetForm form = form("AST-KEEP", "Dell Latitude 5540 (Upgraded RAM)", "Laptop", AssetStatus.AVAILABLE);
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
        existing.setVersion(0L);
        existing.setAssetTag("AST-OLD");
        when(repo.findById(10L)).thenReturn(Optional.of(existing));
        when(repo.existsByAssetTagAndIdNot("AST-TAKEN", 10L)).thenReturn(true);

        AssetForm form = form("AST-TAKEN", "Keyboard", "Peripherals", AssetStatus.AVAILABLE);
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

        AssetForm form = form("AST-OLD", "Old laptop", "Laptop", AssetStatus.AVAILABLE);
        form.setVersion(2L);

        assertThrows(
                com.example.helpdesk.exception.AssetEditConflictException.class, () -> service.update(10L, form));
        verify(repo, never()).save(any(Asset.class));
    }

    @Test
    void updateWithMissingVersionIsRejected() {
        Asset existing = new Asset();
        existing.setId(10L);
        existing.setVersion(3L);
        existing.setAssetTag("AST-OLD");
        when(repo.findById(10L)).thenReturn(Optional.of(existing));

        AssetForm form = form("AST-OLD", "Old laptop", "Laptop", AssetStatus.AVAILABLE);

        assertThrows(
                com.example.helpdesk.exception.AssetEditConflictException.class, () -> service.update(10L, form));
        verify(repo, never()).save(any(Asset.class));
    }

    @Test
    void retiredAssetCannotBeAssigned() {
        AssetForm form = form("AST-RETIRED", "Old Server", "Server", AssetStatus.RETIRED);
        form.setAssignedUserId(101L);

        assertThrows(IllegalArgumentException.class, () -> service.create(form));
    }

    @Test
    void assignUserTransitionsStatusToAssigned() {
        Asset existing = new Asset();
        existing.setId(20L);
        existing.setVersion(0L);
        existing.setAssetTag("AST-20");
        existing.setName("iPad Pro");
        existing.setCategory("Tablet");
        existing.setStatus(AssetStatus.AVAILABLE);

        when(repo.findById(20L)).thenReturn(Optional.of(existing));
        when(repo.existsByAssetTagAndIdNot("AST-20", 20L)).thenReturn(false);
        when(users.findById(101L)).thenReturn(Optional.of(testUser));
        when(repo.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetForm form = form("AST-20", "iPad Pro", "Tablet", AssetStatus.AVAILABLE);
        form.setAssignedUserId(101L);
        form.setVersion(0L);

        Asset updated = service.update(20L, form);

        assertSame(testUser, updated.getAssignedUser());
        assertEquals(AssetStatus.ASSIGNED, updated.getStatus());
    }

    @Test
    void unassignSetsStatusToAvailable() {
        Asset existing = new Asset();
        existing.setId(30L);
        existing.setVersion(1L);
        existing.setAssetTag("AST-30");
        existing.setName("Lenovo ThinkPad");
        existing.setCategory("Laptop");
        existing.setAssignedUser(testUser);
        existing.setStatus(AssetStatus.ASSIGNED);

        when(repo.findById(30L)).thenReturn(Optional.of(existing));
        when(repo.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        Asset updated = service.unassign(30L, 1L);

        assertNull(updated.getAssignedUser());
        assertEquals(AssetStatus.AVAILABLE, updated.getStatus());
        verify(repo).save(existing);
    }

    @Test
    void unassignWithMissingVersionIsRejected() {
        Asset existing = new Asset();
        existing.setId(30L);
        existing.setVersion(1L);
        existing.setAssetTag("AST-30");
        when(repo.findById(30L)).thenReturn(Optional.of(existing));

        assertThrows(
                com.example.helpdesk.exception.AssetEditConflictException.class, () -> service.unassign(30L, null));
        verify(repo, never()).save(any(Asset.class));
    }

    @Test
    void nonexistentAssetThrowsResourceNotFoundException() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        AssetForm form = form("AST-X", "Unknown", "Other", AssetStatus.AVAILABLE);

        assertThrows(ResourceNotFoundException.class, () -> service.update(999L, form));
    }

    @Test
    void createCountsCreationMetricWhenRepositoryAssignsIdImmediately() {
        com.example.helpdesk.metrics.BusinessMetrics metrics =
                mock(com.example.helpdesk.metrics.BusinessMetrics.class);
        AssetService measuredService = new AssetService(repo, users, metrics);

        when(repo.existsByAssetTag("AST-METRIC")).thenReturn(false);
        when(repo.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        measuredService.create(form("AST-METRIC", "Monitor", "Display", AssetStatus.AVAILABLE));

        verify(metrics).incrementAssetCreated();
    }

    @Test
    void assignRequiresExpectedVersion() {
        Asset existing = new Asset();
        existing.setId(40L);
        existing.setVersion(2L);
        existing.setAssetTag("AST-40");
        existing.setStatus(AssetStatus.AVAILABLE);
        when(repo.findById(40L)).thenReturn(Optional.of(existing));

        assertThrows(
                com.example.helpdesk.exception.AssetEditConflictException.class,
                () -> service.assign(40L, 101L, null));
        verify(repo, never()).save(any(Asset.class));
    }
}
