package com.example.helpdesk.repo;

import com.example.helpdesk.model.Asset;
import com.example.helpdesk.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

    List<Asset> findByAssignedUser(User user);

    boolean existsByAssetTag(String tag);

    boolean existsByAssetTagAndIdNot(String tag, Long id);

    @Query(
            "SELECT DISTINCT a.category FROM Asset a WHERE a.category IS NOT NULL AND a.category != '' ORDER BY a.category")
    List<String> findDistinctCategories();
}
