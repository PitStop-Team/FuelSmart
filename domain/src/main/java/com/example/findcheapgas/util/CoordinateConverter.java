package com.example.findcheapgas.util;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

/**
 * WGS84(GPS, 카카오맵 위도·경도) ↔ KATEC(오피넷 좌표, 미터 단위 평면 좌표) 변환.
 * 오피넷에 요청할 때는 KATEC, 지도에 표시할 때는 WGS84를 쓴다.
 */
public final class CoordinateConverter {

    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";
    private static final String KATEC = "+proj=tmerc +lat_0=38 +lon_0=128 +k=0.9999 +x_0=400000 +y_0=600000"
            + " +ellps=bessel +units=m +no_defs +towgs84=-115.80,474.99,674.11,1.16,-2.31,-1.63,6.43";

    private static final CoordinateTransform TO_KATEC;
    private static final CoordinateTransform TO_WGS84;

    static {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem wgs84 = crsFactory.createFromParameters("WGS84", WGS84);
        CoordinateReferenceSystem katec = crsFactory.createFromParameters("KATEC", KATEC);
        CoordinateTransformFactory transformFactory = new CoordinateTransformFactory();
        TO_KATEC = transformFactory.createTransform(wgs84, katec);
        TO_WGS84 = transformFactory.createTransform(katec, wgs84);
    }

    /** KATEC 좌표 (x: 동쪽 방향, y: 북쪽 방향, 미터) */
    public static final class Katec {
        public final double x;
        public final double y;

        public Katec(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    /** WGS84 위도·경도 */
    public static final class Wgs84 {
        public final double latitude;
        public final double longitude;

        public Wgs84(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private CoordinateConverter() {
    }

    // Proj4J 변환 객체는 여러 스레드에서 같이 쓰면 안전하지 않아서 synchronized로 막는다.
    public static synchronized Katec toKatec(double latitude, double longitude) {
        ProjCoordinate result = new ProjCoordinate();
        // Proj4J는 x에 경도, y에 위도를 넣는다.
        TO_KATEC.transform(new ProjCoordinate(longitude, latitude), result);
        return new Katec(result.x, result.y);
    }

    public static synchronized Wgs84 toWgs84(double katecX, double katecY) {
        ProjCoordinate result = new ProjCoordinate();
        TO_WGS84.transform(new ProjCoordinate(katecX, katecY), result);
        return new Wgs84(result.y, result.x);
    }

    /** KATEC은 미터 단위 평면 좌표라서 두 점 사이 직선 거리를 바로 구할 수 있다. */
    public static double distanceMeters(Katec from, double katecX, double katecY) {
        return Math.hypot(katecX - from.x, katecY - from.y);
    }
}
