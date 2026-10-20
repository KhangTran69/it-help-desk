package com.example.helpdesk.api.dto;

import com.example.helpdesk.model.AssetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class AssetDto {
    private Long id;
    @NotBlank(message = "Asset tag is required")
    @Size(min = 3, max = 50, message = "Asset tag must be between 3 and 50 characters")
    private String assetTag;
    @NotBlank(message = "Model name is required")
    @Size(min = 3, max = 200, message = "Model name must be between 3 and 200 characters")
    private String modelName;
    private String category;
    private String manufacturer;
    private String serialNumber;
    private LocalDate purchaseDate;
    private String notes;
    private AssetStatus status;
    private String assignedUserName;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAssetTag() { return assetTag; }
    public void setAssetTag(String assetTag) { this.assetTag = assetTag; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus status) { this.status = status; }
    public String getAssignedUserName() { return assignedUserName; }
    public void setAssignedUserName(String assignedUserName) { this.assignedUserName = assignedUserName; }
}
