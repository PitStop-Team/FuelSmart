package com.example.findcheapgas.viewmodel;

import androidx.annotation.Nullable;

import com.example.findcheapgas.model.SearchOrigin;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.UserLocation;
import com.example.findcheapgas.util.CoordinateConverter;

/**
 * 조회 위치를 정하는 규칙. MapViewModel이 쓰고, Android에 의존하지 않아서 단위 테스트로 확인한다.
 * - 처음에는 내 위치를 받으면 그 위치가 조회 위치가 된다.
 * - 장소를 고르거나 "이 지역에서 다시 검색"을 누르면 그 위치가 조회 위치가 되고,
 *   그 뒤로 내 위치를 새로 받아도 조회 위치는 그대로다.
 * - 내 위치로 돌아가면(칩 X, 내 위치 버튼) 다시 내 위치가 조회 위치가 된다.
 * 내 위치(지도 위 파란 점)는 조회 위치와 상관없이 항상 따로 기억한다.
 */
final class SearchOriginState {

    /**
     * "이 지역에서 다시 검색" 버튼을 보여주는 최대 거리. 버튼은 지도 중심이 지금 고른 반경 밖으로 나가면 보여주되,
     * 반경이 5km여도 3km를 넘으면 보여준다 (5km까지 기다리면 너무 늦다).
     */
    static final int MAX_RESEARCH_DISTANCE_METERS = 3000;

    private SearchOrigin origin;
    private UserLocation myLocation;

    /** 지금 조회 위치. 아직 없으면 null */
    SearchOrigin getOrigin() {
        return origin;
    }

    /** 마지막으로 받은 내 위치. 아직 없으면 null */
    UserLocation getMyLocation() {
        return myLocation;
    }

    /**
     * 내 위치를 새로 받았을 때.
     *
     * @param returnToMyLocation 고정된 조회 위치여도 내 위치 기준으로 돌아갈지 (내 위치 버튼, 칩 X)
     * @return 조회 위치가 바뀌었으면 새 조회 위치, 고정된 조회 위치라 그대로면 null
     */
    SearchOrigin onMyLocation(UserLocation location, boolean returnToMyLocation) {
        myLocation = location;
        if (origin != null && origin.isFixed() && !returnToMyLocation) {
            return null;
        }
        origin = SearchOrigin.myLocation(location);
        return origin;
    }

    /** 검색한 장소나 주유소를 조회 위치로 */
    SearchOrigin selectPlace(String name, double latitude, double longitude) {
        origin = SearchOrigin.place(name, latitude, longitude);
        return origin;
    }

    /** "이 지역에서 다시 검색": 지도 중심을 조회 위치로. 동 이름은 나중에 nameMapCenter로 채운다. */
    SearchOrigin selectMapCenter(double latitude, double longitude) {
        origin = SearchOrigin.mapCenter(null, latitude, longitude);
        return origin;
    }

    /**
     * 지도 중심의 동 이름을 얻었을 때. 그사이 조회 위치가 다른 곳으로 바뀌었으면 무시한다.
     *
     * @param requested 이름을 물어본 지도 중심 조회 위치
     * @return 이름을 붙인 새 조회 위치. 무시했으면 null
     */
    @Nullable
    SearchOrigin nameMapCenter(SearchOrigin requested, String regionName) {
        if (!requested.isMapCenter() || !requested.isSamePosition(origin)) {
            return null;
        }
        origin = SearchOrigin.mapCenter(regionName, requested.getLatitude(), requested.getLongitude());
        return origin;
    }

    /**
     * 위치를 새로 받지 않고 마지막 내 위치로 돌아간다 (위치 권한이 없을 때).
     *
     * @return 새 조회 위치. 이미 내 위치 기준이거나 내 위치를 아직 모르면 null
     */
    SearchOrigin returnToLastMyLocation() {
        if (myLocation == null || (origin != null && !origin.isFixed())) {
            return null;
        }
        origin = SearchOrigin.myLocation(myLocation);
        return origin;
    }

    /**
     * 지도 중심이 지금 조회 위치에서 고른 반경(최대 3km) 이상 떨어져 있어서 "이 지역에서 다시 검색" 버튼을 보여줄지.
     * 반경 1km → 1km, 3km → 3km, 5km → 3km
     */
    boolean shouldOfferResearch(double centerLatitude, double centerLongitude, SearchRadius radius) {
        return isFarFrom(origin, centerLatitude, centerLongitude, researchDistanceMeters(radius));
    }

    static int researchDistanceMeters(SearchRadius radius) {
        return Math.min(radius.getMeters(), MAX_RESEARCH_DISTANCE_METERS);
    }

    static boolean isFarFrom(@Nullable SearchOrigin origin, double latitude, double longitude, double meters) {
        if (origin == null) {
            return false;
        }
        CoordinateConverter.Katec from = CoordinateConverter.toKatec(origin.getLatitude(), origin.getLongitude());
        CoordinateConverter.Katec to = CoordinateConverter.toKatec(latitude, longitude);
        return CoordinateConverter.distanceMeters(from, to.x, to.y) >= meters;
    }
}
