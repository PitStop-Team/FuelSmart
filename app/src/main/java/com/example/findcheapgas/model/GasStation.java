package com.example.findcheapgas.model;

import java.io.Serializable;

/**
 * 주변 주유소 한 곳. 오피넷 좌표(KATEC)와 지도에 쓸 좌표(WGS84)를 함께 가진다.
 * 응답 캐시에 Gson으로 그대로 저장되고, 상세 화면에 계산 내역을 넘길 때 Intent에 담긴다.
 */
public class GasStation implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;          // 오피넷 주유소 ID (UNI_ID)
    private final String brandCode;   // POLL_DIV_CD
    private final String name;
    private final int price;          // 원/리터
    private final double distance;    // 조회 위치에서 거리(m)
    private final double katecX;
    private final double katecY;
    private final double latitude;
    private final double longitude;

    public GasStation(String id, String brandCode, String name, int price, double distance,
                      double katecX, double katecY, double latitude, double longitude) {
        this.id = id;
        this.brandCode = brandCode;
        this.name = name;
        this.price = price;
        this.distance = distance;
        this.katecX = katecX;
        this.katecY = katecY;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** 캐시에서 꺼낸 결과를 지금 위치 기준 거리로 바꿀 때 쓴다. */
    public GasStation withDistance(double newDistance) {
        return new GasStation(id, brandCode, name, price, newDistance, katecX, katecY, latitude, longitude);
    }

    public String getId() {
        return id;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public String getBrandName() {
        return Brand.nameOf(brandCode);
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public double getDistance() {
        return distance;
    }

    public double getKatecX() {
        return katecX;
    }

    public double getKatecY() {
        return katecY;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
