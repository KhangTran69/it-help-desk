package com.example.helpdesk.api;

import com.example.helpdesk.api.dto.AssetDto;
import com.example.helpdesk.api.dto.PageResponse;
import com.example.helpdesk.dto.AssetForm;
import com.example.helpdesk.model.Asset;
import com.example.helpdesk.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assets")
@Tag(name = "Assets", description = "Asset management API endpoints")
public class AssetApiController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final java.util.Set<String> SORT_FIELDS =
            java.util.Set.of("id", "assetTag", "name", "category", "status", "purchaseDate", "updatedAt");

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

        Pageable pageable = apiPageable(page, size, sortBy, sortDir);
        Page<Asset> assets = assetService.filterPage(null, null, null, pageable);
        List<AssetDto> assetDtos =
                assets.getContent().stream().map(this::convertToDto).collect(Collectors.toList());
        PageResponse<AssetDto> response = new PageResponse<>(
                assetDtos, assets.getNumber(), assets.getTotalPages(), assets.getTotalElements(), assets.getSize());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get asset by ID")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<AssetDto> getAssetById(@PathVariable Long id) {
        return ResponseEntity.ok(convertToDto(assetService.get(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new asset")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<AssetDto> createAsset(@Valid @RequestBody AssetDto assetDto) {
        Asset created = assetService.create(toForm(assetDto));
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing asset")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<AssetDto> updateAsset(@PathVariable Long id, @Valid @RequestBody AssetDto assetDto) {
        if (assetDto.getVersion() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "Asset version is required. Reload the asset and include its version when updating.");
        }
        AssetForm form = toForm(assetDto);
        form.setVersion(assetDto.getVersion());
        Asset updated = assetService.update(id, form);
        return ResponseEntity.ok(convertToDto(updated));
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign asset to user")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<AssetDto> assignAsset(
            @PathVariable Long id, @RequestParam Long userId, @RequestParam Long version) {
        return ResponseEntity.ok(convertToDto(assetService.assign(id, userId, version)));
    }

    @PatchMapping("/{id}/unassign")
    @Operation(summary = "Unassign asset")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<AssetDto> unassignAsset(@PathVariable Long id, @RequestParam Long version) {
        return ResponseEntity.ok(convertToDto(assetService.unassign(id, version)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an asset")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long id, @RequestParam Long version) {
        assetService.delete(id, version);
        return ResponseEntity.noContent().build();
    }

    private Pageable apiPageable(int page, int size, String sortBy, String sortDir) {
        if (page < 0
                || size < 1
                || size > MAX_PAGE_SIZE
                || !SORT_FIELDS.contains(sortBy)
                || !("asc".equalsIgnoreCase(sortDir) || "desc".equalsIgnoreCase(sortDir))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid pagination or sorting parameters");
        }
        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }

    private AssetForm toForm(AssetDto dto) {
        AssetForm form = new AssetForm();
        form.setAssetTag(dto.getAssetTag());
        form.setName(dto.getModelName());
        form.setCategory(dto.getCategory());
        form.setManufacturer(dto.getManufacturer());
        form.setSerialNumber(dto.getSerialNumber());
        form.setPurchaseDate(dto.getPurchaseDate());
        form.setNotes(dto.getNotes());
        form.setStatus(dto.getStatus());
        return form;
    }

    private AssetDto convertToDto(Asset asset) {
        AssetDto dto = new AssetDto();
        dto.setId(asset.getId());
        dto.setVersion(asset.getVersion());
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
}
