package com.example.findcheapgas.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** 집·회사 위치 조회와 저장. LiveData 조회는 Room이 백그라운드에서 하고, 나머지는 메인 스레드가 아닌 곳에서 부른다. */
@Dao
public interface SavedPlaceDao {

    /** 표가 바뀔 때마다 새 목록이 나온다. */
    @Query("SELECT * FROM saved_place")
    LiveData<List<SavedPlaceEntity>> observeAll();

    /** 홈 화면 위젯 작업용. 메인 스레드가 아닌 곳에서 부른다. */
    @Query("SELECT * FROM saved_place")
    List<SavedPlaceEntity> getAll();

    @Query("SELECT * FROM saved_place WHERE kind = :kind LIMIT 1")
    SavedPlaceEntity find(String kind);

    /** 같은 종류가 이미 있으면 바꾼다. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(SavedPlaceEntity entity);

    @Query("DELETE FROM saved_place WHERE kind = :kind")
    int delete(String kind);
}
