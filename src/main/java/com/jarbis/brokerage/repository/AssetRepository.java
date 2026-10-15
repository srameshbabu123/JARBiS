package com.jarbis.brokerage.repository;

import com.jarbis.brokerage.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Asset entity. Provides database access methods for Asset
 * operations.
 */
@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

	Optional<Asset> findByNameIgnoreCase(String name);
}
