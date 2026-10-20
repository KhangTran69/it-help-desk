package com.example.helpdesk.service;

import com.example.helpdesk.dto.AssetForm;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.metrics.BusinessMetrics;
import com.example.helpdesk.model.Asset;
import com.example.helpdesk.model.AssetStatus;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repo.AssetRepository;
import com.example.helpdesk.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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

    public AssetService(AssetRepository repo, UserRepository users) {
        this(repo, users, null);
    }

    public List<Asset> all(String q) {
        return filter(q, null, null);
    }

    public List<Asset> filter(String q, AssetStatus status, String category) {
        String cleanQ = (q != null && !q.isBlank()) ? q.trim() : null;
        String cleanCat = (category != null && !category.isBlank()) ? category.trim() : null;
        return repo.searchAssets(cleanQ, status, cleanCat);
    }

    public Page<Asset> filterPage(String q, AssetStatus status, String category, Pageable pageable) {
        String cleanQ = (q != null && !q.isBlank()) ? q.trim() : null;
        String cleanCat = (category != null && !category.isBlank()) ? category.trim() : null;
        return repo.pageAssets(cleanQ, status, cleanCat, pageable);
    }

    public List<String> categories() {
        return repo.findDistinctCategories();
    }

    public Asset get(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Asset not found with ID: " + id));
    }

    @Transactional
    public Asset save(Asset a) {
        if (a.getAssetTag() == null || a.getAssetTag().isBlank()) {
            throw new IllegalArgumentException("Asset tag is required");
        }
        if (a.getId() == null) {
            if (repo.existsByAssetTag(a.getAssetTag())) {
                throw new IllegalArgumentException("Asset tag already exists: " + a.getAssetTag());
            }
        } else {
            if (repo.existsByAssetTagAndIdNot(a.getAssetTag(), a.getId())) {
                throw new IllegalArgumentException("Asset tag already exists: " + a.getAssetTag());
            }
        }
        if (a.getStatus() == null) {
            a.setStatus(AssetStatus.AVAILABLE);
        }
        if (a.getStatus() == AssetStatus.RETIRED && a.getAssignedUser() != null) {
            throw new IllegalArgumentException("Retired assets cannot be assigned");
        }
        Asset saved = repo.save(a);
        if (a.getId() == null) {
            if (businessMetrics != null) businessMetrics.incrementAssetCreated();
        }
        return saved;
    }

    @Transactional
    public Asset create(AssetForm form) {
        validate(form);
        String cleanTag = form.getAssetTag().trim();
        if (repo.existsByAssetTag(cleanTag)) throw new IllegalArgumentException("Asset tag already exists: " + cleanTag);

        Asset asset = new Asset();
        form.updateAsset(asset);
        asset.setAssetTag(cleanTag);

        if (form.getAssignedUserId() != null) {
            if (form.getStatus() == AssetStatus.RETIRED) {
                throw new IllegalArgumentException("Retired assets cannot be assigned");
            }
            if (users != null) {
                User user = users.findById(form.getAssignedUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("Assigned user not found"));
                if (user.getRole() != Role.EMPLOYEE) throw new IllegalArgumentException("Assets can only be assigned to employees");
                asset.setAssignedUser(user);
                if (asset.getStatus() == AssetStatus.AVAILABLE) {
                    asset.setStatus(AssetStatus.ASSIGNED);
                }
            }
        } else {
            asset.setAssignedUser(null);
            if (asset.getStatus() == AssetStatus.ASSIGNED) {
                asset.setStatus(AssetStatus.AVAILABLE);
            }
        }

        return repo.save(asset);
    }

    @Transactional
    public Asset update(Long id, AssetForm form) {
        Asset existing = get(id);
        validate(form);

        String newTag = form.getAssetTag() != null ? form.getAssetTag().trim() : "";
        if (newTag.isBlank()) {
            throw new IllegalArgumentException("Asset tag is required");
        }
        if (repo.existsByAssetTagAndIdNot(newTag, id)) {
            throw new IllegalArgumentException("Asset tag already exists: " + newTag);
        }

        if (form.getStatus() == AssetStatus.RETIRED && form.getAssignedUserId() != null) {
            throw new IllegalArgumentException("Retired assets cannot be assigned");
        }

        form.updateAsset(existing);
        existing.setAssetTag(newTag);

        if (form.getAssignedUserId() != null) {
            if (users != null) {
                User user = users.findById(form.getAssignedUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("Assigned user not found"));
                if (user.getRole() != Role.EMPLOYEE) throw new IllegalArgumentException("Assets can only be assigned to employees");
                existing.setAssignedUser(user);
                if (existing.getStatus() == AssetStatus.AVAILABLE) {
                    existing.setStatus(AssetStatus.ASSIGNED);
                }
            }
        } else {
            existing.setAssignedUser(null);
            if (existing.getStatus() == AssetStatus.ASSIGNED) {
                existing.setStatus(AssetStatus.AVAILABLE);
            }
        }

        existing.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return repo.save(existing);
    }

    @Transactional
    public void unassign(Long id) {
        Asset existing = get(id);
        existing.setAssignedUser(null);
        if (existing.getStatus() == AssetStatus.ASSIGNED) {
            existing.setStatus(AssetStatus.AVAILABLE);
        }
        existing.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        repo.save(existing);
    }

    public long count() {
        return repo.count();
    }

    private void validate(AssetForm form) {
        if (form.getAssetTag() == null || form.getAssetTag().isBlank()) throw new IllegalArgumentException("Asset tag is required");
        if (form.getName() == null || form.getName().isBlank()) throw new IllegalArgumentException("Equipment name is required");
        if (form.getCategory() == null || form.getCategory().isBlank()) throw new IllegalArgumentException("Category is required");
        if (form.getStatus() == null) throw new IllegalArgumentException("Status is required");
        if (form.getStatus() == AssetStatus.RETIRED && form.getAssignedUserId() != null) {
            throw new IllegalArgumentException("Retired assets cannot be assigned");
        }
    }

    @Transactional(readOnly = true)
    public Page<Asset> getAllAssetsPaginated(Pageable pageable) {
        return repo.findAll(pageable);
    }

    public Asset getAssetById(Long id) {
        return get(id);
    }

    public Asset createAsset(Asset asset) {
        return save(asset);
    }

    public Asset updateAsset(Long id, Asset asset) {
        Asset existing = get(id);
        existing.setAssetTag(asset.getAssetTag());
        existing.setName(asset.getName());
        existing.setCategory(asset.getCategory());
        existing.setManufacturer(asset.getManufacturer());
        existing.setSerialNumber(asset.getSerialNumber());
        existing.setPurchaseDate(asset.getPurchaseDate());
        existing.setNotes(asset.getNotes());
        existing.setStatus(asset.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return save(existing);
    }

    public Asset assignAsset(Long id, Long userId) {
        Asset existing = get(id);
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getRole() != Role.EMPLOYEE) throw new IllegalArgumentException("Assets can only be assigned to employees");
        if (existing.getStatus() == AssetStatus.RETIRED) throw new IllegalArgumentException("Retired assets cannot be assigned");
        existing.setAssignedUser(user);
        if (existing.getStatus() == AssetStatus.AVAILABLE) {
            existing.setStatus(AssetStatus.ASSIGNED);
        }
        existing.setUpdatedAt(LocalDateTime.now());
        Asset saved = repo.save(existing);
        if (businessMetrics != null) businessMetrics.incrementAssetAssigned();
        return saved;
    }

    public Asset unassignAsset(Long id) {
        unassign(id);
        return get(id);
    }

    public void deleteAsset(Long id) {
        Asset existing = get(id);
        repo.delete(existing);
    }
}
