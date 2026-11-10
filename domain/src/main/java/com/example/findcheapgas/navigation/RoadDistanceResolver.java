package com.example.findcheapgas.navigation;

import com.example.findcheapgas.model.GasStation;

import java.util.OptionalDouble;

/** 예상 이득 계산이 주유소까지의 도로 거리(m, 편도)를 얻는 길. */
public interface RoadDistanceResolver {

    double roadDistanceMeters(GasStation station);

    /** 예전 방식: 직선거리 × 1.4 고정. 계수를 구간별로 바꾸기 전의 계산과 같다. */
    RoadDistanceResolver FIXED_FACTOR = station -> station.getDistance() * 1.4;

    /** RouteDistanceProvider에 먼저 묻고, 못 받으면 직선거리 × 구간별 계수로 대체한다. provider는 null이어도 된다. */
    static RoadDistanceResolver withFallback(RouteDistanceProvider provider, double originLatitude,
                                             double originLongitude) {
        return station -> {
            if (provider != null) {
                OptionalDouble road = provider.getRoadDistanceMeters(originLatitude, originLongitude,
                        station.getLatitude(), station.getLongitude());
                if (road.isPresent() && road.getAsDouble() >= 0) {
                    return road.getAsDouble();
                }
            }
            return RoadDistanceEstimator.estimateMeters(station.getDistance());
        };
    }
}
