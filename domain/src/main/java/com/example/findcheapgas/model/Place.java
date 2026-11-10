package com.example.findcheapgas.model;

/** 카카오 로컬 API로 찾은 장소 한 곳. 위치는 WGS84 위도·경도다. */
public final class Place {

    private final String name;
    private final String address;    // 도로명 주소가 있으면 도로명, 없으면 지번
    private final String category;   // 마지막 분류 (예: 수도권2호선). 주소 검색 결과는 "주소"
    private final double latitude;
    private final double longitude;

    public Place(String name, String address, String category, double latitude, double longitude) {
        this.name = name;
        this.address = address;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCategory() {
        return category;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
