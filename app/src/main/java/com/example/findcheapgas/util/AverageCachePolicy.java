package com.example.findcheapgas.util;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 평균가 캐시 규칙.
 * - 전국·시도 평균가: 3시간. 상세 화면을 여러 번 열어도 3시간 안이면 다시 부르지 않는다.
 * - 최근 7일 추이: 하루에 한 번. 오늘(기기 시간대 날짜) 받은 것이면 쓰고, 날짜가 바뀌면 다시 받는다.
 * 기기 시계를 과거로 돌려 저장 시각이 지금보다 뒤에 있으면 믿을 수 없으므로 오래된 것으로 본다.
 */
public final class AverageCachePolicy {

    public static final long AVERAGE_TTL_MS = 3 * 60 * 60 * 1000L;

    private AverageCachePolicy() {
    }

    /** 전국·시도 평균가 캐시를 그대로 써도 되는지 */
    public static boolean isAverageFresh(long savedAt, long now) {
        return savedAt > 0 && savedAt <= now && now - savedAt < AVERAGE_TTL_MS;
    }

    /** 7일 추이 캐시를 그대로 써도 되는지 (오늘 받은 것인지) */
    public static boolean isTrendFresh(@Nullable String savedDay, long savedAt, long now, TimeZone timeZone) {
        return savedDay != null && savedAt > 0 && savedAt <= now && savedDay.equals(dayOf(now, timeZone));
    }

    /** 시각 → "2026-11-06" (주어진 시간대 기준) */
    public static String dayOf(long millis, TimeZone timeZone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(timeZone);
        return format.format(new Date(millis));
    }
}
