package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

/**
 * 조회 위치. 주변 주유소를 이 위치 기준으로 조회하고, 거리, 이동 비용, 기준 주유소, 예상 이득도 모두
 * 이 위치에서 출발한다고 보고 계산한다. 위치는 WGS84 위도·경도다.
 * - MY_LOCATION: 현재 위치 (위치를 못 받았으면 기본 위치). 내 위치를 새로 받으면 따라 바뀐다.
 * - PLACE: 검색해서 고른 장소나 주유소. 내 위치가 바뀌어도 그대로 있다.
 * - MAP_CENTER: "이 지역에서 다시 검색"을 눌렀을 때의 지도 중심. PLACE처럼 내 위치가 바뀌어도 그대로 있다.
 *   이름(동 이름)은 조회와 따로 나중에 채워지고, 못 얻으면 null이다.
 */
public final class SearchOrigin {

    public enum Kind {
        MY_LOCATION,
        PLACE,
        MAP_CENTER
    }

    private final Kind kind;
    // PLACE면 장소 이름 (예: 강남역), MAP_CENTER면 동 이름 (예: 역삼1동, 아직 모르거나 못 얻었으면 null).
    // MY_LOCATION이면 null
    private final String name;
    private final double latitude;
    private final double longitude;
    // MY_LOCATION일 때 위치를 어디서 얻었는지. 그 밖에는 null
    private final UserLocation.Source locationSource;

    private SearchOrigin(Kind kind, String name, double latitude, double longitude,
                         UserLocation.Source locationSource) {
        this.kind = kind;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationSource = locationSource;
    }

    public static SearchOrigin myLocation(UserLocation location) {
        return new SearchOrigin(Kind.MY_LOCATION, null, location.getLatitude(), location.getLongitude(),
                location.getSource());
    }

    public static SearchOrigin place(String name, double latitude, double longitude) {
        return new SearchOrigin(Kind.PLACE, name, latitude, longitude, null);
    }

    /** 지도 중심. 동 이름을 아직 모르면 regionName은 null */
    public static SearchOrigin mapCenter(@Nullable String regionName, double latitude, double longitude) {
        return new SearchOrigin(Kind.MAP_CENTER, regionName, latitude, longitude, null);
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isPlace() {
        return kind == Kind.PLACE;
    }

    public boolean isMapCenter() {
        return kind == Kind.MAP_CENTER;
    }

    /** 내 위치를 따라가지 않고 고정된 조회 위치인지 (검색한 장소·주유소, 지도 중심) */
    public boolean isFixed() {
        return kind != Kind.MY_LOCATION;
    }

    /** 장소 이름이나 동 이름. MY_LOCATION이거나 동 이름을 모르면 null */
    @Nullable
    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    /** 내 위치를 못 받아 기본 위치(서울시청)로 조회하는 중인지 */
    public boolean isDefaultLocation() {
        return kind == Kind.MY_LOCATION && locationSource == UserLocation.Source.DEFAULT;
    }

    /** 종류와 좌표가 같은지 (이름만 다른 같은 조회 위치인지) */
    public boolean isSamePosition(@Nullable SearchOrigin other) {
        return other != null && other.kind == kind
                && Double.compare(other.latitude, latitude) == 0
                && Double.compare(other.longitude, longitude) == 0;
    }
}
