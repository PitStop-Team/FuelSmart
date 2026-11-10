package com.example.findcheapgas.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 주변 주유소 조회 결과 캐시 한 건. 주유소 목록은 JSON 문자열로 저장한다. */
@Entity(tableName = "station_cache")
public class StationCacheEntity {

    // 조회 반경(5km) + 데이터 출처 + 반올림한 위치 + 유종 (StationRepository.cacheKey 참고)
    @PrimaryKey
    @NonNull
    public String cacheKey;

    @NonNull
    public String stationsJson;

    // 저장한 시각 (System.currentTimeMillis)
    public long savedAt;

    public StationCacheEntity(@NonNull String cacheKey, @NonNull String stationsJson, long savedAt) {
        this.cacheKey = cacheKey;
        this.stationsJson = stationsJson;
        this.savedAt = savedAt;
    }
}
