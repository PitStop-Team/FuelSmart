package com.example.findcheapgas.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 집 또는 회사 위치 한 곳 (버전 5부터). 사용자가 저장한 데이터라서 앱을 업데이트해도 지워지면 안 된다.
 * 종류(HOME, WORK)가 기본 키라서 종류마다 하나만 저장된다.
 * 열을 바꾸면 AppDatabase의 버전을 올리고 데이터를 옮기는 Migration을 작성해야 한다.
 */
@Entity(tableName = "saved_place")
public class SavedPlaceEntity {

    // SavedPlace.Kind 이름 (HOME, WORK)
    @PrimaryKey
    @NonNull
    public String kind;

    // 검색해서 고른 장소 이름
    @NonNull
    public String name;

    @Nullable
    public String address;

    // WGS84
    public double latitude;
    public double longitude;

    // 저장한 시각 (System.currentTimeMillis)
    public long savedAt;

    public SavedPlaceEntity(@NonNull String kind, @NonNull String name, @Nullable String address,
                            double latitude, double longitude, long savedAt) {
        this.kind = kind;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.savedAt = savedAt;
    }
}
