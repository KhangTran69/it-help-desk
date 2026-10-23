package com.example.helpdesk.web;

import com.example.helpdesk.dto.AssetForm;
import com.example.helpdesk.model.Asset;
import com.example.helpdesk.model.AssetStatus;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.repo.UserRepository;
import com.example.helpdesk.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

@Controller
public class AssetPageController {
    private final AssetService assets;
    private final UserRepository users;

    public AssetPageController(AssetService assets, UserRepository users) {
        this.assets = assets;
        this.users = users;
    }

    @GetMapping("/assets")
    String assetList(@RequestParam(required = false) String q,
                     @RequestParam(required = false) AssetStatus status,
                     @RequestParam(required = false) String category,
                     @RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "20") int size,
                     @RequestParam(defaultValue = "updatedAt") String sort,
                     @RequestParam(defaultValue = "desc") String dir, Model model) {
        Pageable pageable = pageable(page, size, sort, dir,
                Set.of("updatedAt", "createdAt", "assetTag", "name", "status", "category"), "updatedAt");
        model.addAttribute("assets", assets.filterPage(q, status, category, pageable));
        model.addAttribute("q", q);
        model.addAttribute("statuses", AssetStatus.values());
        model.addAttribute("categories", assets.categories());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("sort", pageable.getSort().stream().findFirst().map(Sort.Order::getProperty).orElse("updatedAt"));
        model.addAttribute("dir", pageable.getSort().stream().findFirst().map(o -> o.getDirection().name().toLowerCase()).orElse("desc"));
        model.addAttribute("size", pageable.getPageSize());
        model.addAttribute("isTechnician", true);
        return "assets";
    }

    @GetMapping("/assets/new")
    String newAsset(Model model) {
        model.addAttribute("form", new AssetForm());
        populateAssetForm(model, false);
        return "asset-form";
    }

    @PostMapping("/assets")
    String createAsset(@Valid @ModelAttribute("form") AssetForm form, BindingResult result,
                       Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            populateAssetForm(model, false);
            return "asset-form";
        }
        try {
            assets.create(form);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            populateAssetForm(model, false);
            return "asset-form";
        }
        redirect.addFlashAttribute("success", "Asset registered successfully.");
        return "redirect:/assets";
    }

    @GetMapping("/assets/{id}/edit")
    String editAsset(@PathVariable Long id, Model model) {
        Asset asset = assets.get(id);
        model.addAttribute("asset", asset);
        model.addAttribute("form", AssetForm.fromAsset(asset));
        populateAssetForm(model, true);
        return "asset-form";
    }

    @PostMapping("/assets/{id}/edit")
    String updateAsset(@PathVariable Long id, @Valid @ModelAttribute("form") AssetForm form,
                       BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("asset", assets.get(id));
            populateAssetForm(model, true);
            return "asset-form";
        }
        try {
            assets.update(id, form);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("asset", assets.get(id));
            model.addAttribute("error", ex.getMessage());
            populateAssetForm(model, true);
            return "asset-form";
        }
        redirect.addFlashAttribute("success", "Asset updated successfully.");
        return "redirect:/assets";
    }

    @PostMapping("/assets/{id}/unassign")
    String unassignAsset(@PathVariable Long id, @RequestParam Long version, RedirectAttributes redirect) {
        assets.unassign(id, version);
        redirect.addFlashAttribute("success", "Asset unassigned and returned to inventory.");
        return "redirect:/assets";
    }

    private void populateAssetForm(Model model, boolean isEdit) {
        model.addAttribute("statuses", AssetStatus.values());
        model.addAttribute("users", users.findByRole(Role.EMPLOYEE));
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("isTechnician", true);
    }

    private Pageable pageable(int page, int size, String sort, String direction, Set<String> allowed, String fallback) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String safeSort = allowed.contains(sort) ? sort : fallback;
        Sort.Direction safeDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(safeDirection, safeSort));
    }
}
