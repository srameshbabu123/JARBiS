package com.jarbis.brokerage.repository;

import com.jarbis.brokerage.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Holding entity.
 */
@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

	/**
	 * Find all holdings for a specific account.
	 *
	 * @param accountId
	 *            the account ID
	 * @return list of holdings for the account
	 */
	List<Holding> findByAccountId(Long accountId);

	/**
	 * Find holding by account ID and asset ID.
	 *
	 * @param accountId
	 *            the account ID
	 * @param assetId
	 *            the asset ID
	 * @return the holding if exists
	 */
	Holding findByAccountIdAndAssetId(Long accountId, Long assetId);
}
