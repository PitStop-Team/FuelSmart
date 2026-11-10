package com.example.findcheapgas.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.util.FormatUtil;

import org.junit.Test;

/** 설정 탭 오늘 오피넷 사용량의 구간과 문구 (0, 249, 250, 300, 가짜 데이터) */
public class ApiUsageTest {

    private static final int LIMIT = 300;

    @Test
    public void zero() {
        ApiUsage usage = new ApiUsage(0, LIMIT, false);
        assertEquals(ApiUsage.Level.NORMAL, usage.getLevel());
        assertEquals("오늘 0회 / 300회", FormatUtil.formatApiUsageCount(usage));
        assertNull(FormatUtil.formatApiUsageStatus(usage));
        assertEquals(0, usage.getProgressPercent());
        assertFalse(usage.isMockData());
    }

    @Test
    public void typical() {
        ApiUsage usage = new ApiUsage(12, LIMIT, false);
        assertEquals("오늘 12회 / 300회", FormatUtil.formatApiUsageCount(usage));
        assertEquals(4, usage.getProgressPercent());
        assertNull(FormatUtil.formatApiUsageStatus(usage));
    }

    @Test
    public void justBelowWarning() {
        ApiUsage usage = new ApiUsage(249, LIMIT, false);
        assertEquals(ApiUsage.Level.NORMAL, usage.getLevel());
        assertNull(FormatUtil.formatApiUsageStatus(usage));
        assertEquals(83, usage.getProgressPercent());
    }

    @Test
    public void warningFrom250() {
        ApiUsage usage = new ApiUsage(250, LIMIT, false);
        assertEquals(ApiUsage.Level.NEAR_LIMIT, usage.getLevel());
        assertEquals("한도에 가까워졌어요", FormatUtil.formatApiUsageStatus(usage));
        assertEquals(ApiUsage.Level.NEAR_LIMIT, new ApiUsage(299, LIMIT, false).getLevel());
    }

    @Test
    public void limitReached() {
        ApiUsage usage = new ApiUsage(300, LIMIT, false);
        assertEquals(ApiUsage.Level.LIMIT_REACHED, usage.getLevel());
        assertEquals("오늘 300회 / 300회", FormatUtil.formatApiUsageCount(usage));
        assertEquals("오늘 한도에 도달했어요. 저장된 결과만 볼 수 있어요", FormatUtil.formatApiUsageStatus(usage));
        assertEquals(100, usage.getProgressPercent());
    }

    @Test
    public void mockData_keepsCountAndShowsNotice() {
        ApiUsage usage = new ApiUsage(0, LIMIT, true);
        assertTrue(usage.isMockData());
        assertEquals(ApiUsage.Level.NORMAL, usage.getLevel());
        assertEquals("오늘 0회 / 300회", FormatUtil.formatApiUsageCount(usage));
        // 가짜 데이터 모드로 바꾸기 전에 실제로 부른 횟수가 있으면 그대로 보여준다.
        ApiUsage before = new ApiUsage(260, LIMIT, true);
        assertEquals("한도에 가까워졌어요", FormatUtil.formatApiUsageStatus(before));
    }

    @Test
    public void negativeCount_isZero() {
        assertEquals(0, new ApiUsage(-3, LIMIT, false).getCount());
    }
}
