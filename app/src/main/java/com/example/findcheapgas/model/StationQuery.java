package com.example.findcheapgas.model;

/**
 * 오피넷 주변 주유소 조회 조건. 위치는 WGS84 위도·경도다.
 * 반경과 정렬은 조회 조건에 넣지 않는다. 항상 최대 반경으로 받아 두고 앱에서 거르고 정렬한다.
 */
public class StationQuery {

    /** 오피넷 반경 내 주유소 API의 최대 반경(m). 화면 반경(SearchRadius)과 상관없이 항상 이 값으로 요청한다. */
    public static final int FETCH_RADIUS_METERS = 5000;

    private final double latitude;
    private final double longitude;
    private final FuelType fuelType;

    public StationQuery(double latitude, double longitude, FuelType fuelType) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.fuelType = fuelType;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public FuelType getFuelType() {
        return fuelType;
    }
}
