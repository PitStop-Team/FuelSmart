package com.example.findcheapgas.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 사용자가 등록한 집 또는 회사 위치. 종류마다 하나만 있다. 위치는 WGS84 위도·경도다.
 * 개인정보이므로 이름, 주소, 좌표를 로그에 남기지 않는다.
 */
public final class SavedPlace {

    public enum Kind {
        HOME,
        WORK
    }

    private final Kind kind;
    private final String name;        // 검색해서 고른 장소 이름 (예: 선릉역)
    @Nullable
    private final String address;     // 장소의 주소. 없으면 null
    private final double latitude;
    private final double longitude;
    private final long savedAt;       // 저장한 시각 (System.currentTimeMillis)

    public SavedPlace(@NonNull Kind kind, @NonNull String name, @Nullable String address,
                      double latitude, double longitude, long savedAt) {
        this.kind = kind;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.savedAt = savedAt;
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getAddress() {
        return address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public long getSavedAt() {
        return savedAt;
    }

    /** 좌표가 같은지 (같은 곳을 다시 등록했으면 다시 조회하지 않는다) */
    public boolean isSamePosition(@Nullable SavedPlace other) {
        return other != null && Double.compare(other.latitude, latitude) == 0
                && Double.compare(other.longitude, longitude) == 0;
    }
}
