package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class AverageCachePolicyTest {

    private static final TimeZone SEOUL = TimeZone.getTimeZone("Asia/Seoul");
    private static final long MINUTE = 60 * 1000L;
    private static final long HOUR = 60 * MINUTE;

    private static long seoul(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(SEOUL);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute);
        return calendar.getTimeInMillis();
    }

    @Test
    public void average_freshForThreeHours() {
        long savedAt = seoul(2026, 11, 6, 9, 0);
        assertTrue(AverageCachePolicy.isAverageFresh(savedAt, savedAt));
        assertTrue(AverageCachePolicy.isAverageFresh(savedAt, savedAt + 3 * HOUR - 1));
        assertFalse(AverageCachePolicy.isAverageFresh(savedAt, savedAt + 3 * HOUR));
    }

    @Test
    public void average_notSavedOrFromFutureIsStale() {
        long now = seoul(2026, 11, 6, 9, 0);
        assertFalse(AverageCachePolicy.isAverageFresh(0, now));
        assertFalse(AverageCachePolicy.isAverageFresh(now + MINUTE, now));
    }

    @Test
    public void trend_freshOnlyOnSameDay() {
        long savedAt = seoul(2026, 11, 6, 0, 5);
        String day = AverageCachePolicy.dayOf(savedAt, SEOUL);
        assertEquals("2026-11-06", day);

        assertTrue(AverageCachePolicy.isTrendFresh(day, savedAt, seoul(2026, 11, 6, 23, 59), SEOUL));
        assertFalse(AverageCachePolicy.isTrendFresh(day, savedAt, seoul(2026, 11, 7, 0, 0), SEOUL));
    }

    @Test
    public void trend_lessThanADayButNextDateIsStale() {
        long savedAt = seoul(2026, 11, 6, 23, 50);
        String day = AverageCachePolicy.dayOf(savedAt, SEOUL);
        assertFalse(AverageCachePolicy.isTrendFresh(day, savedAt, savedAt + 20 * MINUTE, SEOUL));
    }

    @Test
    public void trend_missingDayOrFutureSaveIsStale() {
        long now = seoul(2026, 11, 6, 12, 0);
        assertFalse(AverageCachePolicy.isTrendFresh(null, now, now, SEOUL));
        assertFalse(AverageCachePolicy.isTrendFresh("2026-11-06", now + HOUR, now, SEOUL));
    }
}
