package com.example.findcheapgas.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface StationCacheDao {

    /** minSavedAt 이후에 저장된 캐시만 돌려준다. 없으면 null. */
    @Query("SELECT * FROM station_cache WHERE cacheKey = :cacheKey AND savedAt >= :minSavedAt LIMIT 1")
    StationCacheEntity find(String cacheKey, long minSavedAt);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(StationCacheEntity entity);

    @Query("DELETE FROM station_cache WHERE savedAt < :minSavedAt")
    int deleteOlderThan(long minSavedAt);
}
