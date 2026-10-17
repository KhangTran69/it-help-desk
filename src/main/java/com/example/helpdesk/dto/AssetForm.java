package com.example.helpdesk.dto;

import com.example.helpdesk.model.Asset;
import com.example.helpdesk.model.AssetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

public class AssetForm {
    private Long id;

    @NotBlank(message = "Asset tag is required")
    @Size(max = 30, message = "Asset tag cannot exceed 30 characters")
    private String assetTag;

    @NotBlank(message = "Equipment name is required")
    @Size(max = 120, message = "Equipment name cannot exceed 120 characters")
    private String name;

    @NotBlank(message = "Category is required")
    private String category;

    private String manufacturer;
    private String serialNumber;

    @NotNull(message = "Status is required")
    private AssetStatus status = AssetStatus.AVAILABLE;

    private Long assignedUserId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate purchaseDate;

    private String notes;

    public AssetForm() {}

    public static AssetForm fromAsset(Asset asset) {
        AssetForm f = new AssetForm();
        f.setId(asset.getId());
        f.setAssetTag(asset.getAssetTag());
        f.setName(asset.getName());
        f.setCategory(asset.getCategory());
        f.setManufacturer(asset.getManufacturer());
        f.setSerialNumber(asset.getSerialNumber());
        f.setStatus(asset.getStatus());
        f.setAssignedUserId(asset.getAssignedUser() != null ? asset.getAssignedUser().getId() : null);
        f.setPurchaseDate(asset.getPurchaseDate());
        f.setNotes(asset.getNotes());
        return f;
    }

    public void updateAsset(Asset asset) {
        asset.setAssetTag(this.assetTag);
        asset.setName(this.name);
        asset.setCategory(this.category);
        asset.setManufacturer(this.manufacturer);
        asset.setSerialNumber(this.serialNumber);
        asset.setStatus(this.status);
        asset.setPurchaseDate(this.purchaseDate);
        asset.setNotes(this.notes);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus status) { this.status = status; }

    public Long getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
