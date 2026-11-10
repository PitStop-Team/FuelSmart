package com.example.findcheapgas.navigation;

import java.util.OptionalDouble;

/** 출발지에서 주유소까지 실제 도로 거리. 값을 못 주면 empty이고, 그때는 RoadDistanceEstimator의 구간별 계수로 대체한다. */
public interface RouteDistanceProvider {

    /** 도로 거리(m, 편도). 알 수 없으면 empty */
    OptionalDouble getRoadDistanceMeters(double fromLatitude, double fromLongitude,
                                         double toLatitude, double toLongitude);
}
