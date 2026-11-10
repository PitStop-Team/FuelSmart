package com.example.findcheapgas.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 현재 안내 중인 경로: 목적지와 경유지 순서. */
public final class RouteInfo {

    private final NavPoint destination;
    private final List<NavPoint> waypoints;

    public RouteInfo(NavPoint destination, List<NavPoint> waypoints) {
        this.destination = destination;
        this.waypoints = Collections.unmodifiableList(new ArrayList<>(waypoints));
    }

    public NavPoint getDestination() {
        return destination;
    }

    public List<NavPoint> getWaypoints() {
        return waypoints;
    }
}
