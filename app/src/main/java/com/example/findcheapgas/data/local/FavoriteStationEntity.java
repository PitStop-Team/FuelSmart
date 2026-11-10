package com.example.findcheapgas.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 즐겨찾기한 주유소 한 곳. 사용자가 저장한 데이터라서 앱을 업데이트해도 지워지면 안 된다.
 * 가격은 계속 바뀌므로 저장하지 않고, 볼 때마다 주변 목록이나 상세정보에서 가져온다.
 * 열을 바꾸면 AppDatabase의 버전을 올리고 데이터를 옮기는 Migration을 작성해야 한다.
 */
@Entity(tableName = "favorite_station")
public class FavoriteStationEntity {

    // 오피넷 주유소 ID (UNI_ID)
    @PrimaryKey
    @NonNull
    public String stationId;

    @NonNull
    public String name;

    // 오피넷 브랜드 코드 (POLL_DIV_CD). 응답에 없으면 null
    @Nullable
    public String brandCode;

    // 저장할 때 알고 있던 주소. 상세정보를 받기 전에 저장했으면 null이고, 나중에 상세정보를 받으면 채운다.
    @Nullable
    public String address;

    // 오피넷 좌표 (KATEC)
    public double katecX;
    public double katecY;

    // 지도 좌표 (WGS84)
    public double latitude;
    public double longitude;

    // 저장한 시각 (System.currentTimeMillis). 즐겨찾기 탭은 최근에 저장한 순서로 보여준다.
    public long savedAt;

    public FavoriteStationEntity(@NonNull String stationId, @NonNull String name, @Nullable String brandCode,
                                 @Nullable String address, double katecX, double katecY,
                                 double latitude, double longitude, long savedAt) {
        this.stationId = stationId;
        this.name = name;
        this.brandCode = brandCode;
        this.address = address;
        this.katecX = katecX;
        this.katecY = katecY;
        this.latitude = latitude;
        this.longitude = longitude;
        this.savedAt = savedAt;
    }
}
