package com.crio.jumbotail.assettracking.exchanges.response;

import com.crio.jumbotail.assettracking.entity.Asset;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Schema(description = "Response with the assets and centroid")
public class AssetDataResponse {

	@Schema(description = "The centroid of the assets")
	private Point centroid;

	@Schema(description = "List of Assets that were found")
	private List<Asset> assets;

}
