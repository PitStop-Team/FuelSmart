package com.example.findcheapgas.navigation;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * 사용자가 알려 준 도로 거리를 목적지 좌표별로 들고 있는 구현. 등록하지 않은 곳은 empty다.
 * 출발지는 보지 않는다. 사용자가 입력한 값은 지금 출발지 기준이라고 본다.
 */
public final class UserInputRouteDistanceProvider implements RouteDistanceProvider {

    private final Map<String, Double> metersByDestination = new HashMap<>();

    public void put(double toLatitude, double toLongitude, double roadDistanceMeters) {
        metersByDestination.put(key(toLatitude, toLongitude), roadDistanceMeters);
    }

    public void clear() {
        metersByDestination.clear();
    }

    @Override
    public OptionalDouble getRoadDistanceMeters(double fromLatitude, double fromLongitude,
                                                double toLatitude, double toLongitude) {
        Double meters = metersByDestination.get(key(toLatitude, toLongitude));
        return meters == null ? OptionalDouble.empty() : OptionalDouble.of(meters);
    }

    private static String key(double latitude, double longitude) {
        return latitude + "," + longitude;
    }
}
