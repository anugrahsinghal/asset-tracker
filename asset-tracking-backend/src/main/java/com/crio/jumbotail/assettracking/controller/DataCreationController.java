package com.crio.jumbotail.assettracking.controller;

import com.crio.jumbotail.assettracking.exchanges.AssetCreatedResponse;
import com.crio.jumbotail.assettracking.exchanges.AssetCreationRequest;
import com.crio.jumbotail.assettracking.exchanges.LocationDataDto;
import com.crio.jumbotail.assettracking.exchanges.LocationDto;
import com.crio.jumbotail.assettracking.service.AssetCreationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
public class DataCreationController {

	@Autowired
	AssetCreationService assetCreationService;


	/**
	 * @param assetCreationRequest request to create a new asset with its initial location
	 * @return id of the created asset
	 */
	@PostMapping("/assets")
	@ResponseStatus(HttpStatus.CREATED)
	public AssetCreatedResponse createNewAsset(@RequestBody AssetCreationRequest assetCreationRequest) {
		return assetCreationService.createAsset(assetCreationRequest);
	}

	@PatchMapping("/assets")
	public void updateLocationOfAsset() {

	}

	@GetMapping("/create")
	public void createData() throws IOException {
		final Path path = Paths.get("C:\\Projects\\Personal\\JUMBOGPS-T9\\asset-tracking-backend\\src\\main\\resources\\locations.csv");
		final List<String> locations = Files.readAllLines(path);

		for (String location : locations) {
			final String[] s = location.split("\t");
			LOG.info("location = " + Arrays.toString(s));
			assert (s.length == 2);
			LocationDto locationDto = new LocationDto(Double.valueOf(s[0]), Double.valueOf(s[1]));

			LocationDataDto locationDataDto = new LocationDataDto(locationDto,
					Instant.now().plus((long) (Math.random() * 15000), ChronoUnit.SECONDS).getEpochSecond());

			AssetCreationRequest assetCreationRequest = new AssetCreationRequest(
					RandomStringUtils.randomAlphabetic(10),
					RandomStringUtils.randomAlphabetic(40),
					locationDataDto, RandomUtils.nextInt() % 2 == 0 ? "TRUCK" : "SALESPERSON");

			createNewAsset(assetCreationRequest);
		}

	}

}