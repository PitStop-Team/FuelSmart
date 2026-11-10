package com.example.findcheapgas.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * 평균가 응답 캐시 (SharedPreferences). 평균가는 사용자 데이터가 아니고 다시 받으면 되는 값이라 Room 대신 여기에 둔다.
 * 항목마다 해석한 결과(JSON), 저장한 시각, 저장한 날짜(yyyy-MM-dd)를 둔다. 언제까지 쓸지는 AverageCachePolicy가 정한다.
 * 이름에는 가짜 데이터와 실제 API 결과가 섞이지 않도록 출처를 붙인다 (예: api|national).
 */
public class AveragePriceCache {

    private static final String PREFS_NAME = "average_price_cache";
    private static final String SUFFIX_JSON = "|json";
    private static final String SUFFIX_SAVED_AT = "|saved_at";
    private static final String SUFFIX_DAY = "|day";

    /** 저장된 항목 하나 */
    public static final class Entry {
        public final String json;
        public final long savedAt;
        @Nullable
        public final String day;

        Entry(String json, long savedAt, @Nullable String day) {
            this.json = json;
            this.savedAt = savedAt;
            this.day = day;
        }
    }

    private final SharedPreferences prefs;

    public AveragePriceCache(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 저장된 적이 없으면 null */
    @Nullable
    public Entry read(String name) {
        String json = prefs.getString(name + SUFFIX_JSON, null);
        if (json == null) {
            return null;
        }
        return new Entry(json, prefs.getLong(name + SUFFIX_SAVED_AT, 0), prefs.getString(name + SUFFIX_DAY, null));
    }

    public void save(String name, String json, long savedAt, String day) {
        prefs.edit()
                .putString(name + SUFFIX_JSON, json)
                .putLong(name + SUFFIX_SAVED_AT, savedAt)
                .putString(name + SUFFIX_DAY, day)
                .apply();
    }
}
