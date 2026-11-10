package com.example.findcheapgas.navigation;

import java.util.Optional;

/**
 * 내비게이션 제어. 경유지 추가처럼 경로를 바꾸는 호출은 사용자 승인을 받은 뒤에만 부른다.
 * 실패하거나 지원하지 않으면 false/empty를 돌려준다.
 */
public interface NavigationController {

    /** 목적지를 정하고 경로를 요청한다. 이미 있던 경유지는 지운다. */
    boolean setDestination(NavPoint destination);

    /** 현재 경로에 경유지를 추가한다. 목적지가 없으면 false */
    boolean addWaypoint(NavPoint waypoint);

    /** 현재 경로에서 경유지를 뺀다. 없던 경유지면 false */
    boolean removeWaypoint(NavPoint waypoint);

    /** 현재 경로 정보. 안내 중인 경로가 없으면 empty */
    Optional<RouteInfo> getCurrentRouteInfo();
}
