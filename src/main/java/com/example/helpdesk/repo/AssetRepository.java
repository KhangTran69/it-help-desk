package com.example.helpdesk.repo;

import com.example.helpdesk.model.Asset;
import com.example.helpdesk.model.AssetStatus;
import com.example.helpdesk.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByAssignedUser(User u);
    boolean existsByAssetTag(String tag);
    boolean existsByAssetTagAndIdNot(String tag, Long id);

    @Query("SELECT a FROM Asset a WHERE " +
           "(:q IS NULL OR :q = '' OR LOWER(a.assetTag) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(a.manufacturer) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:category IS NULL OR :category = '' OR a.category = :category) " +
           "ORDER BY a.updatedAt DESC")
    List<Asset> searchAssets(@Param("q") String q, @Param("status") AssetStatus status, @Param("category") String category);

    @Query(value = "SELECT a FROM Asset a WHERE " +
           "(:q IS NULL OR :q = '' OR LOWER(a.assetTag) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(a.manufacturer) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:status IS NULL OR a.status = :status) AND (:category IS NULL OR :category = '' OR a.category = :category)",
           countQuery = "SELECT COUNT(a) FROM Asset a WHERE " +
           "(:q IS NULL OR :q = '' OR LOWER(a.assetTag) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(a.manufacturer) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:status IS NULL OR a.status = :status) AND (:category IS NULL OR :category = '' OR a.category = :category)")
    Page<Asset> pageAssets(@Param("q") String q, @Param("status") AssetStatus status,
                           @Param("category") String category, Pageable pageable);

    @Query("SELECT DISTINCT a.category FROM Asset a WHERE a.category IS NOT NULL AND a.category != '' ORDER BY a.category")
    List<String> findDistinctCategories();
}
