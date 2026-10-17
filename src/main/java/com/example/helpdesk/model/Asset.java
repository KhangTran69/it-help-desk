package com.example.helpdesk.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_tag", unique = true, nullable = false)
    private String assetTag;

    @Column(nullable = false)
    private String name;

    private String category;
    private String manufacturer;
    private String serialNumber;
    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @Enumerated(EnumType.STRING)
    private AssetStatus status;

    private LocalDate purchaseDate;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Asset() {}

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String v) { assetTag = v; }

    public String getName() { return name; }
    public void setName(String v) { name = v; }

    public String getCategory() { return category; }
    public void setCategory(String v) { category = v; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String v) { manufacturer = v; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String v) { serialNumber = v; }

    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus v) { status = v; }

    public User getAssignedUser() { return assignedUser; }
    public void setAssignedUser(User v) { assignedUser = v; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate v) { purchaseDate = v; }

    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
