package com.example.findcheapgas.util;

/**
 * 집·회사 기준 비교의 주변 조회 캐시 규칙 (오피넷 하루 300회 제한).
 * - 탭을 열 때: 같은 위치·유종의 주변 조회 캐시가 3시간 안이면 그대로 쓰고 "약 1시간 전 가격"처럼 보여준다.
 * - 당겨서 새로고침: 20분이 지난 것만 다시 가져온다 (지도 탭 캐시와 같은 기준).
 * 집·회사 두 곳이라 한 번에 많아야 2회 호출한다. 지금 조회 위치 카드는 지도 탭 결과를 쓰므로 호출하지 않는다.
 */
public final class PlaceCompareCachePolicy {

    /** 탭을 열 때 그대로 쓰는 캐시의 최대 나이 */
    public static final long OPEN_MAX_AGE_MS = 3 * 60 * 60 * 1000L;

    /** 당겨서 새로고침할 때 그대로 쓰는 캐시의 최대 나이 */
    public static final long REFRESH_MAX_AGE_MS = 20 * 60 * 1000L;

    private PlaceCompareCachePolicy() {
    }

    public static long maxAgeMs(boolean refresh) {
        return refresh ? REFRESH_MAX_AGE_MS : OPEN_MAX_AGE_MS;
    }

    /** savedAt에 저장한 결과를 지금 그대로 쓸 수 있는지 */
    public static boolean isUsable(long savedAt, long now, boolean refresh) {
        return now - savedAt <= maxAgeMs(refresh);
    }
}
