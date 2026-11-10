package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

/**
 * 즐겨찾기한 주유소. 가격은 바뀌므로 들고 있지 않고, 즐겨찾기 탭에서 볼 때마다 가져온다 (FavoritePrice).
 * 좌표는 오피넷(KATEC)과 지도(WGS84) 둘 다 가진다.
 */
public final class FavoriteStation {

    /** 즐겨찾기 최대 개수. 즐겨찾기 탭에서 가격을 다시 가져올 때 오피넷 호출이 늘어나지 않게 제한한다. */
    public static final int MAX_COUNT = 20;

    /** 즐겨찾기 추가 결과 */
    public enum AddResult {
        ADDED,          // 새로 저장함
        ALREADY_SAVED,  // 이미 즐겨찾기라서 그대로 둠
        FULL            // 20곳이 차서 저장하지 않음
    }

    private final String id;
    private final String name;
    @Nullable
    private final String brandCode;
    @Nullable
    private final String address;
    private final double katecX;
    private final double katecY;
    private final double latitude;
    private final double longitude;
    private final long savedAt;

    public FavoriteStation(String id, String name, @Nullable String brandCode, @Nullable String address,
                           double katecX, double katecY, double latitude, double longitude, long savedAt) {
        this.id = id;
        this.name = name;
        this.brandCode = brandCode;
        this.address = address;
        this.katecX = katecX;
        this.katecY = katecY;
        this.latitude = latitude;
        this.longitude = longitude;
        this.savedAt = savedAt;
    }

    /**
     * 추가해도 되는지. 이미 저장한 주유소는 개수와 상관없이 그대로 둔다.
     *
     * @param alreadySaved 이 주유소가 이미 즐겨찾기인지
     * @param savedCount   지금 저장된 즐겨찾기 개수
     */
    public static AddResult checkAdd(boolean alreadySaved, int savedCount) {
        if (alreadySaved) {
            return AddResult.ALREADY_SAVED;
        }
        return savedCount >= MAX_COUNT ? AddResult.FULL : AddResult.ADDED;
    }

    /** 이 즐겨찾기를 가격과 조회 위치에서의 거리(m)를 붙인 주유소로 바꾼다. 예상 이득 계산에 쓴다. */
    public GasStation toGasStation(int price, double distance) {
        return new GasStation(id, brandCode, name, price, distance, katecX, katecY, latitude, longitude);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getBrandCode() {
        return brandCode;
    }

    public String getBrandName() {
        return Brand.nameOf(brandCode);
    }

    /** 저장할 때 알던 주소. 모르면 null */
    @Nullable
    public String getAddress() {
        return address;
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

    public long getSavedAt() {
        return savedAt;
    }
}
