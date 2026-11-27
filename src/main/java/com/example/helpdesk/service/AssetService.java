package com.example.helpdesk.service;

import com.example.helpdesk.dto.AssetForm;
import com.example.helpdesk.exception.AssetEditConflictException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.metrics.BusinessMetrics;
import com.example.helpdesk.model.Asset;
import com.example.helpdesk.model.AssetStatus;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.AssetRepository;
import com.example.helpdesk.repo.AssetSpecifications;
import com.example.helpdesk.repo.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {

    private final AssetRepository repo;
    private final UserRepository users;
    private final BusinessMetrics businessMetrics;

    @Autowired
    public AssetService(AssetRepository repo, UserRepository users, BusinessMetrics businessMetrics) {
        this.repo = repo;
        this.users = users;
        this.businessMetrics = businessMetrics;
    }

    public Page<Asset> filterPage(String q, AssetStatus status, String category, Pageable pageable) {
        return repo.findAll(AssetSpecifications.withFilters(clean(q), status, clean(category)), pageable);
    }

    public List<String> categories() {
        return repo.findDistinctCategories();
    }

    public Asset get(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Asset not found with ID: " + id));
    }

    @Transactional
    public Asset create(AssetForm form) {
        validate(form);
        String cleanTag = form.getAssetTag().trim();
        if (repo.existsByAssetTag(cleanTag))
            throw new IllegalArgumentException("Asset tag already exists: " + cleanTag);

        Asset asset = new Asset();
        form.updateAsset(asset);
        asset.setAssetTag(cleanTag);
        applyAssignment(asset, form.getAssignedUserId());
        assertAssignmentConsistency(asset);
        return persist(asset);
    }

    @Transactional
    public Asset update(Long id, AssetForm form) {
        Asset existing = get(id);
        if (form.getVersion() == null || !Objects.equals(form.getVersion(), existing.getVersion())) {
            throw new AssetEditConflictException();
        }
        validate(form);

        String newTag = form.getAssetTag() == null ? "" : form.getAssetTag().trim();
        if (newTag.isBlank()) throw new IllegalArgumentException("Asset tag is required");
        if (repo.existsByAssetTagAndIdNot(newTag, id)) {
            throw new IllegalArgumentException("Asset tag already exists: " + newTag);
        }
        if (form.getStatus() == AssetStatus.RETIRED && form.getAssignedUserId() != null) {
            throw new IllegalArgumentException("Retired assets cannot be assigned");
        }

        form.updateAsset(existing);
        existing.setAssetTag(newTag);
        applyAssignment(existing, form.getAssignedUserId());
        assertAssignmentConsistency(existing);
        existing.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return persist(existing);
    }

    @Transactional
    public Asset assign(Long id, Long userId, Long expectedVersion) {
        Asset existing = get(id);
        assertExpectedVersion(existing, expectedVersion);
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != Role.EMPLOYEE)
            throw new IllegalArgumentException("Assets can only be assigned to employees");
        if (existing.getStatus() == AssetStatus.RETIRED) {
            throw new IllegalArgumentException("Retired assets cannot be assigned");
        }
        existing.setAssignedUser(user);
        if (existing.getStatus() == AssetStatus.AVAILABLE) {
            existing.setStatus(AssetStatus.ASSIGNED);
        }
        existing.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        Asset saved = persist(existing);
        if (businessMetrics != null) businessMetrics.incrementAssetAssigned();
        return saved;
    }

    @Transactional
    public Asset unassign(Long id, Long expectedVersion) {
        Asset existing = get(id);
        assertExpectedVersion(existing, expectedVersion);
        existing.setAssignedUser(null);
        if (existing.getStatus() == AssetStatus.ASSIGNED) {
            existing.setStatus(AssetStatus.AVAILABLE);
        }
        existing.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return persist(existing);
    }

    @Transactional
    public void delete(Long id, Long expectedVersion) {
        Asset existing = get(id);
        assertExpectedVersion(existing, expectedVersion);
        repo.delete(existing);
    }

    public long count() {
        return repo.count();
    }

    private Asset persist(Asset asset) {
        if (asset.getAssetTag() == null || asset.getAssetTag().isBlank()) {
            throw new IllegalArgumentException("Asset tag is required");
        }
        if (asset.getStatus() == null) asset.setStatus(AssetStatus.AVAILABLE);
        if (asset.getStatus() == AssetStatus.RETIRED && asset.getAssignedUser() != null) {
            throw new IllegalArgumentException("Retired assets cannot be assigned");
        }
        if (asset.getStatus() == AssetStatus.ASSIGNED && asset.getAssignedUser() == null) {
            throw new IllegalArgumentException("Assigned assets must have an employee");
        }
        if (asset.getStatus() == AssetStatus.AVAILABLE && asset.getAssignedUser() != null) {
            asset.setStatus(AssetStatus.ASSIGNED);
        }
        boolean creating = asset.getId() == null;
        Asset saved = repo.save(asset);
        if (creating && businessMetrics != null) businessMetrics.incrementAssetCreated();
        return saved;
    }

    private void applyAssignment(Asset asset, Long assignedUserId) {
        if (assignedUserId == null) {
            asset.setAssignedUser(null);
            return;
        }
        if (asset.getStatus() == AssetStatus.RETIRED) {
            throw new IllegalArgumentException("Retired assets cannot be assigned");
        }
        User user = users.findById(assignedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Assigned user not found"));
        if (user.getRole() != Role.EMPLOYEE)
            throw new IllegalArgumentException("Assets can only be assigned to employees");
        asset.setAssignedUser(user);
    }

    private void assertAssignmentConsistency(Asset asset) {
        if (asset.getStatus() == AssetStatus.ASSIGNED && asset.getAssignedUser() == null) {
            throw new IllegalArgumentException("Assigned assets must have an employee");
        }
    }

    private void assertExpectedVersion(Asset existing, Long expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(existing.getVersion())) {
            throw new AssetEditConflictException();
        }
    }

    private void validate(AssetForm form) {
        if (form.getAssetTag() == null || form.getAssetTag().isBlank()) {
            throw new IllegalArgumentException("Asset tag is required");
        }
        if (form.getName() == null || form.getName().isBlank()) {
            throw new IllegalArgumentException("Equipment name is required");
        }
        if (form.getCategory() == null || form.getCategory().isBlank()) {
            throw new IllegalArgumentException("Category is required");
        }
        if (form.getStatus() == null) throw new IllegalArgumentException("Status is required");
        if (form.getStatus() == AssetStatus.RETIRED && form.getAssignedUserId() != null) {
            throw new IllegalArgumentException("Retired assets cannot be assigned");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
