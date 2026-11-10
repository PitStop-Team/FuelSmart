package com.example.findcheapgas.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 주유소 상세정보 캐시 한 건. 상세정보(StationDetail)는 JSON 문자열로 저장한다. */
@Entity(tableName = "station_detail_cache")
public class StationDetailCacheEntity {

    // 데이터 출처 + 주유소 ID (StationDetailRepository.cacheKey 참고)
    @PrimaryKey
    @NonNull
    public String cacheKey;

    @NonNull
    public String detailJson;

    // 저장한 시각 (System.currentTimeMillis)
    public long savedAt;

    public StationDetailCacheEntity(@NonNull String cacheKey, @NonNull String detailJson, long savedAt) {
        this.cacheKey = cacheKey;
        this.detailJson = detailJson;
        this.savedAt = savedAt;
    }
}
