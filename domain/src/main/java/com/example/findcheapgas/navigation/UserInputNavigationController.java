package com.example.findcheapgas.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 사용자가 정한 목적지/경유지를 앱 안에서만 들고 있는 구현. 실제 내비게이션은 움직이지 않는다. */
public class UserInputNavigationController implements NavigationController {

    private NavPoint destination;
    private final List<NavPoint> waypoints = new ArrayList<>();

    @Override
    public boolean setDestination(NavPoint newDestination) {
        if (newDestination == null) {
            return false;
        }
        destination = newDestination;
        waypoints.clear();
        return true;
    }

    @Override
    public boolean addWaypoint(NavPoint waypoint) {
        if (destination == null || waypoint == null) {
            return false;
        }
        waypoints.add(waypoint);
        return true;
    }

    @Override
    public boolean removeWaypoint(NavPoint waypoint) {
        return waypoints.remove(waypoint);
    }

    @Override
    public Optional<RouteInfo> getCurrentRouteInfo() {
        return destination == null ? Optional.empty() : Optional.of(new RouteInfo(destination, waypoints));
    }
}
