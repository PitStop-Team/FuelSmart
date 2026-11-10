package com.example.findcheapgas.widget;

import com.example.findcheapgas.util.PlaceCompareCachePolicy;

/**
 * 위젯을 왜 다시 그리는지. 주변 조회 캐시를 얼마나 오래된 것까지 그대로 쓸지가 달라진다 (집·회사 기준 비교와 같은 규칙).
 * - PERIODIC: 3시간마다 하는 주기 갱신. 3시간 안의 캐시면 그대로 쓴다.
 * - REFRESH: 위젯의 새로고침 버튼. 당겨서 새로고침과 같이 20분이 지났을 때만 다시 가져온다.
 * - REDRAW: 내 차 정보, 내 브랜드, 유종·반경·필터, 집·회사 위치가 바뀌었거나 위젯을 새로 놓았을 때.
 *   3시간 안의 캐시로 다시 계산만 하고, 캐시가 없을 때만 가져온다.
 */
public enum WidgetRefreshMode {
    PERIODIC,
    REFRESH,
    REDRAW;

    /** 그대로 쓰는 주변 조회 캐시의 최대 나이 */
    public long maxCacheAgeMs() {
        return PlaceCompareCachePolicy.maxAgeMs(this == REFRESH);
    }

    static WidgetRefreshMode fromName(String name) {
        if (name != null) {
            for (WidgetRefreshMode mode : values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }
        }
        return REDRAW;
    }
}
