package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Asset;
import com.jarbis.brokerage.exception.AssetNotFoundException;
import com.jarbis.brokerage.repository.AssetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AssetService {

	private final AssetRepository assetRepository;

	@Autowired
	public AssetService(AssetRepository assetRepository) {
		this.assetRepository = assetRepository;
	}

	public Asset getAssetById(Long assetId) {
		return assetRepository.findById(assetId)
				.orElseThrow(() -> new AssetNotFoundException("Asset not found with id: " + assetId));
	}
}
