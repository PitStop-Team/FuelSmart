package com.example.findcheapgas.model;

/**
 * 지도 중심으로 쓸 위치(WGS84 위도·경도)와 그 위치를 어디서 얻었는지.
 */
public class UserLocation {

    public enum Source {
        CURRENT,     // getCurrentLocation으로 방금 가져온 위치
        LAST_KNOWN,  // 기기에 남아 있던 마지막 위치
        DEFAULT      // 위치를 얻지 못해 쓰는 기본 위치(서울시청)
    }

    private final double latitude;
    private final double longitude;
    private final Source source;

    public UserLocation(double latitude, double longitude, Source source) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.source = source;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public Source getSource() {
        return source;
    }

    /** 실제 내 위치인지. 기본 위치일 때는 지도에 내 위치 점을 그리지 않는다. */
    public boolean isUserLocation() {
        return source != Source.DEFAULT;
    }
}
