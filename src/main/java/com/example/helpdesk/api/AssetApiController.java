package com.example.helpdesk.api;

import com.example.helpdesk.api.dto.AssetDto;
import com.example.helpdesk.api.dto.PageResponse;
import com.example.helpdesk.model.Asset;
import com.example.helpdesk.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assets")
@Tag(name = "Assets", description = "Asset management API endpoints")
public class AssetApiController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final java.util.Set<String> SORT_FIELDS = java.util.Set.of("id", "assetTag", "name", "category", "status", "purchaseDate", "updatedAt");

    private final AssetService assetService;

    public AssetApiController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    @Operation(summary = "Get all assets with pagination")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<PageResponse<AssetDto>> getAllAssets(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "updatedAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {

        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || !SORT_FIELDS.contains(sortBy)
                || !("asc".equalsIgnoreCase(sortDir) || "desc".equalsIgnoreCase(sortDir))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid pagination or sorting parameters");
        }
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Asset> assets = assetService.getAllAssetsPaginated(pageable);

        List<AssetDto> assetDtos = assets.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        PageResponse<AssetDto> response = new PageResponse<>(
                assetDtos,
                assets.getNumber(),
                assets.getTotalPages(),
                assets.getTotalElements(),
                assets.getSize()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get asset by ID")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<AssetDto> getAssetById(@PathVariable Long id) {
        Asset asset = assetService.getAssetById(id);
        return ResponseEntity.ok(convertToDto(asset));
    }

    @PostMapping
    @Operation(summary = "Create a new asset")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<AssetDto> createAsset(@Valid @RequestBody AssetDto assetDto) {
        Asset asset = convertToEntity(assetDto);
        Asset created = assetService.createAsset(asset);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing asset")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<AssetDto> updateAsset(@PathVariable Long id, @Valid @RequestBody AssetDto assetDto) {
        Asset asset = convertToEntity(assetDto);
        Asset updated = assetService.updateAsset(id, asset);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign asset to user")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<AssetDto> assignAsset(@PathVariable Long id, @RequestParam Long userId) {
        Asset updated = assetService.assignAsset(id, userId);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @PatchMapping("/{id}/unassign")
    @Operation(summary = "Unassign asset")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<AssetDto> unassignAsset(@PathVariable Long id) {
        Asset updated = assetService.unassignAsset(id);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an asset")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ResponseEntity.noContent().build();
    }

    private AssetDto convertToDto(Asset asset) {
        AssetDto dto = new AssetDto();
        dto.setId(asset.getId());
        dto.setAssetTag(asset.getAssetTag());
        dto.setModelName(asset.getName());
        dto.setCategory(asset.getCategory());
        dto.setManufacturer(asset.getManufacturer());
        dto.setSerialNumber(asset.getSerialNumber());
        dto.setPurchaseDate(asset.getPurchaseDate());
        dto.setNotes(asset.getNotes());
        dto.setStatus(asset.getStatus());
        if (asset.getAssignedUser() != null) {
            dto.setAssignedUserName(asset.getAssignedUser().getFullName());
        }
        return dto;
    }

    private Asset convertToEntity(AssetDto dto) {
        Asset asset = new Asset();
        asset.setAssetTag(dto.getAssetTag());
        asset.setName(dto.getModelName());
        asset.setCategory(dto.getCategory());
        asset.setManufacturer(dto.getManufacturer());
        asset.setSerialNumber(dto.getSerialNumber());
        asset.setPurchaseDate(dto.getPurchaseDate());
        asset.setNotes(dto.getNotes());
        asset.setStatus(dto.getStatus());
        return asset;
    }
}
