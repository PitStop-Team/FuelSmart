package com.example.findcheapgas.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 주유소 이름 검색 결과 캐시 한 건. 검색 결과 목록은 JSON 문자열로 저장한다. */
@Entity(tableName = "station_search_cache")
public class StationSearchCacheEntity {

    // 데이터 출처 + 정리한 검색어 (StationSearchRepository.cacheKey 참고)
    @PrimaryKey
    @NonNull
    public String cacheKey;

    @NonNull
    public String resultsJson;

    // 저장한 시각 (System.currentTimeMillis)
    public long savedAt;

    public StationSearchCacheEntity(@NonNull String cacheKey, @NonNull String resultsJson, long savedAt) {
        this.cacheKey = cacheKey;
        this.resultsJson = resultsJson;
        this.savedAt = savedAt;
    }
}
