package com.example.helpdesk.repo;

import com.example.helpdesk.model.Asset;
import com.example.helpdesk.model.AssetStatus;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Composable asset inventory filters, replacing the boolean-parameter mega-query. */
public final class AssetSpecifications {

    private AssetSpecifications() {}

    public static Specification<Asset> withFilters(String query, AssetStatus status, String category) {
        Specification<Asset> spec = (root, cq, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("status"), status));
        }
        if (category != null && !category.isBlank()) {
            String clean = category.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, cq, cb) -> cb.equal(cb.lower(root.get("category")), clean));
        }
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("assetTag")), like),
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("manufacturer")), like)));
        }
        return spec;
    }
}
