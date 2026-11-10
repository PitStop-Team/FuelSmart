package com.example.findcheapgas.navigation;

/**
 * 직선거리 → 도로 거리 추정 (RouteDistanceProvider가 값을 못 줄 때 쓰는 대체 경로).
 * 구간별 계수 (구간은 아래 경계 포함, 위 경계 미포함):
 * 0~1km 1.85 / 1~3km 1.52 / 3~5km 1.36 / 5km 이상 1.4
 */
public final class RoadDistanceEstimator {

    public static final double FACTOR_UNDER_1KM = 1.85;
    public static final double FACTOR_1_TO_3KM = 1.52;
    public static final double FACTOR_3_TO_5KM = 1.36;
    public static final double FACTOR_OVER_5KM = 1.4;

    private RoadDistanceEstimator() {
    }

    public static double factorFor(double straightMeters) {
        if (straightMeters < 1_000) {
            return FACTOR_UNDER_1KM;
        }
        if (straightMeters < 3_000) {
            return FACTOR_1_TO_3KM;
        }
        if (straightMeters < 5_000) {
            return FACTOR_3_TO_5KM;
        }
        return FACTOR_OVER_5KM;
    }

    /** 추정 도로 거리(m) */
    public static double estimateMeters(double straightMeters) {
        return straightMeters * factorFor(straightMeters);
    }
}
