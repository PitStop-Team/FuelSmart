package com.example.findcheapgas.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** 즐겨찾기 조회와 저장. LiveData 조회는 Room이 백그라운드에서 하고, 나머지는 메인 스레드가 아닌 곳에서 부른다. */
@Dao
public interface FavoriteStationDao {

    /** 최근에 저장한 순서. 표가 바뀔 때마다 새 목록이 나온다. */
    @Query("SELECT * FROM favorite_station ORDER BY savedAt DESC")
    LiveData<List<FavoriteStationEntity>> observeAll();

    @Query("SELECT COUNT(*) FROM favorite_station")
    int count();

    @Query("SELECT COUNT(*) FROM favorite_station WHERE stationId = :stationId")
    int countById(String stationId);

    /** 이미 있으면 바꾸지 않는다 (저장한 시각 유지). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(FavoriteStationEntity entity);

    @Query("SELECT * FROM favorite_station WHERE stationId = :stationId LIMIT 1")
    FavoriteStationEntity find(String stationId);

    @Query("DELETE FROM favorite_station WHERE stationId = :stationId")
    int delete(String stationId);

    /** 주소를 모르고 저장한 즐겨찾기에만 주소를 채운다. */
    @Query("UPDATE favorite_station SET address = :address WHERE stationId = :stationId AND address IS NULL")
    int fillAddress(String stationId, String address);
}
