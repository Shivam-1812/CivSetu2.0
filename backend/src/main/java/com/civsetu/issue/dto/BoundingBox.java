package com.civsetu.issue.dto;

import org.springframework.http.HttpStatus;

import com.civsetu.common.error.ApiException;

/**
 * A map viewport in WGS 84 degrees, parsed from {@code minLng,minLat,maxLng,maxLat}
 * (the order Leaflet and MapLibre use). Boxes crossing the antimeridian are not supported.
 */
public record BoundingBox(double minLongitude, double minLatitude, double maxLongitude, double maxLatitude) {

    public static BoundingBox parse(String value) {
        String[] parts = value.split(",");
        if (parts.length != 4) {
            throw invalid();
        }
        try {
            BoundingBox box = new BoundingBox(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
            if (!box.isValid()) {
                throw invalid();
            }
            return box;
        } catch (NumberFormatException e) {
            throw invalid();
        }
    }

    private boolean isValid() {
        return minLongitude >= -180 && maxLongitude <= 180 && minLatitude >= -90 && maxLatitude <= 90
                && minLongitude < maxLongitude && minLatitude < maxLatitude;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BBOX",
                "bbox must be minLng,minLat,maxLng,maxLat with longitudes in [-180, 180], "
                        + "latitudes in [-90, 90] and min < max.");
    }
}
