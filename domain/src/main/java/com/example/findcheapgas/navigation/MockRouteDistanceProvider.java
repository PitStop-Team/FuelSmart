package com.example.findcheapgas.navigation;

import java.util.OptionalDouble;

/** 에뮬레이터/테스트용 모의 구현. 모든 곳에 같은 거리를 주거나, 항상 empty를 준다. */
public final class MockRouteDistanceProvider implements RouteDistanceProvider {

    private final OptionalDouble fixedMeters;

    private MockRouteDistanceProvider(OptionalDouble fixedMeters) {
        this.fixedMeters = fixedMeters;
    }

    public static MockRouteDistanceProvider fixed(double roadDistanceMeters) {
        return new MockRouteDistanceProvider(OptionalDouble.of(roadDistanceMeters));
    }

    public static MockRouteDistanceProvider unavailable() {
        return new MockRouteDistanceProvider(OptionalDouble.empty());
    }

    @Override
    public OptionalDouble getRoadDistanceMeters(double fromLatitude, double fromLongitude,
                                                double toLatitude, double toLongitude) {
        return fixedMeters;
    }
}
