package com.civsetu.common.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Creates WGS 84 points (SRID 4326) for PostGIS columns. Note the axis order: x is longitude,
 * y is latitude.
 */
public final class GeoPoints {

    public static final int WGS84 = 4326;

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), WGS84);

    private GeoPoints() {
    }

    public static Point of(double latitude, double longitude) {
        return FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
