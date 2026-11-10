package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CoordinateConverterTest {

    // 서울시청
    private static final double LATITUDE = 37.5666;
    private static final double LONGITUDE = 126.9784;

    @Test
    public void wgs84ToKatecAndBack_errorWithinOneMeter() {
        CoordinateConverter.Katec katec = CoordinateConverter.toKatec(LATITUDE, LONGITUDE);
        CoordinateConverter.Wgs84 back = CoordinateConverter.toWgs84(katec.x, katec.y);

        // 위도 1도 ≈ 111km, 경도 1도 ≈ 111km × cos(위도)로 계산한 오차(m)
        double dy = (back.latitude - LATITUDE) * 111_320;
        double dx = (back.longitude - LONGITUDE) * 111_320 * Math.cos(Math.toRadians(LATITUDE));
        double errorMeters = Math.hypot(dx, dy);
        assertTrue("왕복 변환 오차 " + errorMeters + "m", errorMeters < 1.0);
    }

    @Test
    public void wgs84ToKatec_seoulCityHall() {
        // 서울시청은 KATEC 약 (309947, 552085)로 알려져 있다.
        CoordinateConverter.Katec katec = CoordinateConverter.toKatec(LATITUDE, LONGITUDE);
        assertEquals(309947, katec.x, 10);
        assertEquals(552085, katec.y, 10);
    }

    @Test
    public void distanceMeters_isPlaneDistance() {
        CoordinateConverter.Katec from = new CoordinateConverter.Katec(300000, 500000);
        assertEquals(500, CoordinateConverter.distanceMeters(from, 300300, 500400), 1e-9);
    }
}
