package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 집·회사 기준 비교 캐시: 탭을 열 때는 3시간, 당겨서 새로고침할 때는 20분 안의 결과를 그대로 쓴다. */
public class PlaceCompareCachePolicyTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    @Test
    public void maxAge() {
        assertEquals(3 * HOUR, PlaceCompareCachePolicy.maxAgeMs(false));
        assertEquals(20 * MINUTE, PlaceCompareCachePolicy.maxAgeMs(true));
    }

    @Test
    public void openingTab_usesCacheWithinThreeHours() {
        assertTrue(PlaceCompareCachePolicy.isUsable(NOW - MINUTE, NOW, false));
        assertTrue(PlaceCompareCachePolicy.isUsable(NOW - 2 * HOUR - 59 * MINUTE, NOW, false));
        assertTrue(PlaceCompareCachePolicy.isUsable(NOW - 3 * HOUR, NOW, false));
        assertFalse(PlaceCompareCachePolicy.isUsable(NOW - 3 * HOUR - 1, NOW, false));
    }

    @Test
    public void pullToRefresh_refetchesOlderThanTwentyMinutes() {
        assertTrue(PlaceCompareCachePolicy.isUsable(NOW - 19 * MINUTE, NOW, true));
        assertTrue(PlaceCompareCachePolicy.isUsable(NOW - 20 * MINUTE, NOW, true));
        assertFalse(PlaceCompareCachePolicy.isUsable(NOW - 21 * MINUTE, NOW, true));
        // 탭을 열 때는 그대로 쓰는 1시간 전 결과도 새로고침하면 다시 가져온다.
        assertTrue(PlaceCompareCachePolicy.isUsable(NOW - HOUR, NOW, false));
        assertFalse(PlaceCompareCachePolicy.isUsable(NOW - HOUR, NOW, true));
    }
}
