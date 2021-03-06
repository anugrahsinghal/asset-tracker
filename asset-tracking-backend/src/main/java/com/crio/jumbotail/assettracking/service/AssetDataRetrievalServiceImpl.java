package com.crio.jumbotail.assettracking.service;

import static com.crio.jumbotail.assettracking.utils.SpatialUtils.getCentroidForAssets;
import static java.time.ZoneId.systemDefault;
import static java.time.temporal.ChronoUnit.HOURS;


import com.crio.jumbotail.assettracking.entity.Asset;
import com.crio.jumbotail.assettracking.entity.LocationData;
import com.crio.jumbotail.assettracking.exceptions.AssetNotFoundException;
import com.crio.jumbotail.assettracking.exceptions.InvalidFilterException;
import com.crio.jumbotail.assettracking.exchanges.response.AssetDataResponse;
import com.crio.jumbotail.assettracking.exchanges.response.AssetExportData;
import com.crio.jumbotail.assettracking.exchanges.response.AssetHistoryResponse;
import com.crio.jumbotail.assettracking.repositories.AssetRepository;
import com.crio.jumbotail.assettracking.repositories.LocationDataRepository;
import com.crio.jumbotail.assettracking.utils.SpatialUtils;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.persistence.EntityManager;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class AssetDataRetrievalServiceImpl implements AssetDataRetrievalService {

	@Autowired
	private AssetRepository assetRepository;
	@Autowired
	private LocationDataRepository locationDataRepository;
	@Autowired
	private GeometryFactory geometryFactory;

	@Autowired
	private EntityManager entityManager;

	@Override
	public AssetHistoryResponse getHistoryForAsset(Long assetId) {

		AssetHistoryResponse assetHistoryResponse = new AssetHistoryResponse();

		final Optional<Asset> asset = assetRepository.findById(assetId);
		if (!asset.isPresent()) {
			throw new AssetNotFoundException("Asset not found for Id - " + assetId);
		} else {
			final List<LocationData> last24HourHistory = locationDataRepository.findAllByAsset_IdAndTimestampBetweenOrderByTimestampDesc(assetId,
					LocalDateTime.now().minus(24, HOURS),
					LocalDateTime.now());

			LOG.info("last24HourHistory [{}]", last24HourHistory.size());
			LOG.info("last24HourHistory [{}]", last24HourHistory);

			assetHistoryResponse.setAsset(asset.get());
			assetHistoryResponse.setHistory(last24HourHistory);

			if (!last24HourHistory.isEmpty()) {
				assetHistoryResponse.setCentroid(SpatialUtils.getCentroidForHistory(last24HourHistory));
			} else { // no history for last 24 hours then the current location is the centroid
				LOG.info("Setting Asset Coordinates as centroid");
				assetHistoryResponse.setCentroid(asset.get().getLastReportedCoordinates());
			}

		}

		LOG.info("response [{}]", assetHistoryResponse);
		return assetHistoryResponse;
	}

	@Override
	public Asset getAssetForId(Long assetId) {
		final Optional<Asset> asset = assetRepository.findById(assetId);

		return asset.orElseThrow(() -> new AssetNotFoundException("Asset not found for Id - " + assetId));
	}

	@Override
	public AssetDataResponse getAssetFilteredBy(String assetType, Long startTimestamp, Long endTimestamp, int limit) {
		List<Asset> assets = new ArrayList<>();

		final PageRequest pageRequest = PageRequest.of(0, limit);
		if (hasNoFiltersDefined(assetType, startTimestamp, endTimestamp)) {
			LOG.info("No filters defined. Getting all assets.");
			assets = assetRepository.findAssets(pageRequest);
		} else if (hasOnlyTypeFilterDefined(assetType, startTimestamp, endTimestamp)) {
			LOG.info("Type filter defined");
			assets = assetRepository.filterAssetsByType(assetType, pageRequest);
		} else if (hasOnlyTimeFilterDefined(assetType, startTimestamp, endTimestamp)) {
			LOG.info("Time filter defined");
			checkValidityOfTimeFilter(startTimestamp, endTimestamp);
			assets = assetRepository.filterAssetsByTime(
					localDateTimeFromTimestamp(startTimestamp),
					localDateTimeFromTimestamp(endTimestamp),
					pageRequest);
		} else if (hasBothTimeAndTypeFilter(assetType, startTimestamp, endTimestamp)) {
			LOG.info("Both Type and Time filter defined");
			checkValidityOfTimeFilter(startTimestamp, endTimestamp);
			assets = assetRepository.filterAssetsByTypeAndTime(
					assetType,
					localDateTimeFromTimestamp(startTimestamp),
					localDateTimeFromTimestamp(endTimestamp),
					pageRequest);
		}

		LOG.info("assets.size() [{}]", assets.size());

		// MAYBE
		Point centroid = geometryFactory.createPoint(new Coordinate(0, 0));
		if (!assets.isEmpty()) {
			centroid = getCentroidForAssets(assets);
		}

		return new AssetDataResponse(centroid, assets);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<AssetExportData> exportData() {
		List<AssetExportData> exportAssets = entityManager.createNamedQuery("ExportAssets")
				.getResultList();
		LOG.info("exportAssets.size() [{}]", exportAssets.size());
		if (exportAssets.size() == 0) {
			LOG.info("Exporting with Empty data");
			exportAssets = Collections.singletonList(new AssetExportData());
		}
		LOG.info("FETCHED");
		return exportAssets;
	}

	private void checkValidityOfTimeFilter(Long startTimestamp, Long endTimestamp) {
		if (!isValidTimeFilter(startTimestamp, endTimestamp)) {
			LOG.error("Invalid Time Filter {} , {}", localDateTimeFromTimestamp(startTimestamp), localDateTimeFromTimestamp(endTimestamp));
			throw new InvalidFilterException("Start time should not be less than end date time");
		}
	}

	private boolean hasBothTimeAndTypeFilter(String assetType, Long startTimestamp, Long endTimestamp) {
		return !StringUtils.isEmpty(assetType) && (startTimestamp != null && endTimestamp != null);
	}

	private boolean hasOnlyTimeFilterDefined(String assetType, Long startTimestamp, Long endTimestamp) {
		return StringUtils.isEmpty(assetType) && (startTimestamp != null && endTimestamp != null);
	}

	private boolean hasNoFiltersDefined(String assetType, Long startTimestamp, Long endTimestamp) {
		return StringUtils.isEmpty(assetType) && (startTimestamp == null || endTimestamp == null);
	}

	private boolean hasOnlyTypeFilterDefined(String assetType, Long startTimestamp, Long endTimestamp) {
		return !StringUtils.isEmpty(assetType) && (startTimestamp == null || endTimestamp == null);
	}

	private boolean isValidTimeFilter(Long startTimestamp, Long endTimestamp) {
		// start say 1 - Jan - 2020
		// end say 20 - Jan - 2020
		// is a valid combination
		final boolean isValid = startTimestamp <= endTimestamp;

		LOG.info("startDateTime [{}] , endDateTime [{}], isValid [{}]", startTimestamp, endTimestamp, isValid);

		return isValid;
	}

	private LocalDateTime localDateTimeFromTimestamp(Long timestamp) {
		return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), systemDefault());
	}

}
