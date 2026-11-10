package com.example.findcheapgas.pleos;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.constants.WaypointIndex;
import ai.pleos.playground.navi.data.CurrentLocationInfo;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.data.RequestWaypointInfo;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.data.RouteStateInfo;
import ai.pleos.playground.navi.data.WaypointInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;

import com.example.findcheapgas.navigation.NavPoint;
import com.example.findcheapgas.navigation.NavigationController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import kotlin.Unit;

/**
 * Pleos NaviHelper SDK로 내장 내비게이션을 제어하는 NavigationController.
 * 쓰는 메서드: requestRoute, addWaypoint, removeWaypoint, getCurrentLocationInfo, getRouteStateInfo,
 * 그리고 경로 정보 조회용 getDestinationInfo, getWaypointInfo (모두 NaviHelper API Reference에 있는 것).
 *
 * NaviHelper는 비동기다. 그래서 true는 "요청을 내비게이션에 보냈다"는 뜻이고(연결 전이거나 예외면 false),
 * 실제 결과는 이벤트(onDestinationChanged, onWaypointChanged, onError)로 오며 그때 받아 둔 값을 갱신한다.
 * getCurrentRouteInfo()는 마지막으로 받은 값을 돌려준다.
 *
 * 경로를 바꾸는 호출(setDestination, addWaypoint, removeWaypoint)은 사용자 승인 뒤에만 불러야 한다.
 * 경유지 추가는 WaypointApprovalFlow를 거쳐서만 부른다.
 */
public final class NaviHelperNavigationController implements NavigationController {

    private static final String TAG = "NaviHelperNav";
    // 문서: 경유지는 최대 두 개 (WaypointIndex FIRST, SECOND)
    private static final int MAX_WAYPOINTS = 2;

    private static NaviHelperNavigationController instance;

    public static synchronized NaviHelperNavigationController getInstance(Context context) {
        if (instance == null) {
            instance = new NaviHelperNavigationController(context.getApplicationContext());
        }
        return instance;
    }

    /** 경로 요청 결과를 알리는 곳 (오류 코드 등). 메인 스레드가 아닐 수 있다. */
    public interface ErrorListener {
        void onNavigationError(String code);
    }

    private final Context appContext;
    private NaviHelper naviHelper;
    private volatile boolean connected = false;
    private volatile ErrorListener errorListener;

    // 마지막으로 받은 내비 상태
    private volatile NavPoint destination;
    private volatile List<NavPoint> waypoints = Collections.emptyList();
    private volatile NavPoint currentLocation;
    private volatile Boolean routing;

    private final NaviHelperEventListener eventListener = new NaviHelperEventListener() {
        @Override
        public void onDestinationInfo(@NonNull DestinationInfo info) {
            destination = new NavPoint(info.getName(), info.getLatitude(), info.getLongitude());
        }

        @Override
        public void onWayPointInfo(@NonNull List<WaypointInfo> list) {
            List<NavPoint> points = new ArrayList<>(list.size());
            for (WaypointInfo info : list) {
                points.add(new NavPoint(info.getName(), info.getLatitude(), info.getLongitude()));
            }
            waypoints = Collections.unmodifiableList(points);
        }

        @Override
        public void onCurrentLocationInfo(@NonNull CurrentLocationInfo info) {
            currentLocation = new NavPoint("현재 위치", info.getLatitude(), info.getLongitude());
        }

        @Override
        public void onRouteStateInfo(@NonNull RouteStateInfo info) {
            routing = info.isRouting();
        }

        // 경로가 바뀌었다는 알림이 오면 다시 읽는다.
        @Override
        public void onDestinationChanged() {
            refreshRoute();
        }

        @Override
        public void onWaypointChanged(@NonNull ai.pleos.playground.navi.data.WaypointChangedInfo info) {
            refreshRoute();
        }

        @Override
        public void onRouteStarted(@NonNull ai.pleos.playground.navi.data.RouteStartInfo info) {
            refreshRoute();
        }

        @Override
        public void onRoutingCompleted() {
            refreshRoute();
        }

        @Override
        public void onRouteCancelled() {
            destination = null;
            waypoints = Collections.emptyList();
            routing = Boolean.FALSE;
        }

        @Override
        public void onError(@NonNull NaviErrorCode errorCode) {
            Log.w(TAG, "내비게이션 오류: " + errorCode);
            ErrorListener listener = errorListener;
            if (listener != null) {
                listener.onNavigationError(errorCode.name());
            }
        }
    };

    private NaviHelperNavigationController(Context appContext) {
        this.appContext = appContext;
    }

    public void setErrorListener(ErrorListener listener) {
        this.errorListener = listener;
    }

    /** 앱이 시작될 때 한 번. 연결에 실패하면(폰, 내비게이션 서비스 없음) 계속 "연결 안 됨"으로 둔다. */
    public synchronized void start() {
        if (naviHelper != null) {
            return;
        }
        try {
            NaviHelper helper = new NaviHelper(appContext);
            naviHelper = helper;
            helper.initialize(() -> {
                connected = true;
                try {
                    helper.addListener(eventListener);
                    refreshRoute();
                } catch (Throwable t) {
                    Log.w(TAG, "NaviHelper 리스너 등록 실패", t);
                }
                return Unit.INSTANCE;
            }, error -> {
                connected = false;
                Log.w(TAG, "NaviHelper 연결 실패: " + error);
                return Unit.INSTANCE;
            });
        } catch (Throwable t) {
            Log.w(TAG, "NaviHelper를 시작할 수 없습니다.", t);
            naviHelper = null;
            connected = false;
        }
    }

    /** 내비게이션에 연결돼 있는지. false면 경유지 제안을 보여 주지 않는다. */
    public boolean isConnected() {
        return connected && naviHelper != null;
    }

    /** 현재 위치, 경로 안내 중 여부, 목적지, 경유지를 다시 요청한다. 답은 이벤트로 온다. */
    public void refreshRoute() {
        NaviHelper helper = naviHelper;
        if (helper == null || !connected) {
            return;
        }
        try {
            helper.getCurrentLocationInfo();
            helper.getRouteStateInfo();
            helper.getDestinationInfo();
            helper.getWaypointInfo();
        } catch (Throwable t) {
            Log.w(TAG, "경로 정보 요청 실패", t);
        }
    }

    /** 마지막으로 받은 현재 위치. 모르면 empty */
    public Optional<NavPoint> getCurrentLocation() {
        return Optional.ofNullable(currentLocation);
    }

    // ---------- NavigationController ----------

    @Override
    public boolean setDestination(NavPoint newDestination) {
        NaviHelper helper = naviHelper;
        if (helper == null || !connected || newDestination == null) {
            return false;
        }
        try {
            // 인자 순서: latitude, longitude, displayLatitude, displayLongitude, poiName, poiId, address, poiSubId,
            // routeOption, poiProvider, isMobileRequest (SDK 클래스의 선언 순서)
            helper.requestRoute(new RouteInfo(newDestination.getLatitude(), newDestination.getLongitude(),
                    null, null, newDestination.getName(), "", "", "",
                    RouteOption.RECOMMENDED, "", false));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "목적지 요청 실패", t);
            return false;
        }
    }

    @Override
    public boolean addWaypoint(NavPoint waypoint) {
        NaviHelper helper = naviHelper;
        if (helper == null || !connected || waypoint == null || destination == null) {
            return false;
        }
        int count = waypoints.size();
        if (count >= MAX_WAYPOINTS) {
            return false;
        }
        WaypointIndex index = count == 0 ? WaypointIndex.FIRST : WaypointIndex.SECOND;
        try {
            // 인자 순서: latitude, longitude, poiName, waypointIndex, poiId, address, poiSubId, poiProvider
            helper.addWaypoint(new RequestWaypointInfo(waypoint.getLatitude(), waypoint.getLongitude(),
                    waypoint.getName(), index, "", "", "", ""));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "경유지 추가 요청 실패", t);
            return false;
        }
    }

    @Override
    public boolean removeWaypoint(NavPoint waypoint) {
        NaviHelper helper = naviHelper;
        if (helper == null || !connected || waypoint == null) {
            return false;
        }
        List<NavPoint> current = waypoints;
        int position = -1;
        for (int i = 0; i < current.size(); i++) {
            NavPoint point = current.get(i);
            if (point.getLatitude() == waypoint.getLatitude() && point.getLongitude() == waypoint.getLongitude()) {
                position = i;
                break;
            }
        }
        if (position < 0 || position >= MAX_WAYPOINTS) {
            return false;
        }
        try {
            helper.removeWaypoint(position == 0 ? WaypointIndex.FIRST : WaypointIndex.SECOND);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "경유지 삭제 요청 실패", t);
            return false;
        }
    }

    @Override
    public Optional<com.example.findcheapgas.navigation.RouteInfo> getCurrentRouteInfo() {
        NavPoint dest = destination;
        if (dest == null || Boolean.FALSE.equals(routing)) {
            return Optional.empty();
        }
        return Optional.of(new com.example.findcheapgas.navigation.RouteInfo(dest, waypoints));
    }
}
