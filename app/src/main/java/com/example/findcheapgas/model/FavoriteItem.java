package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

/** 즐겨찾기 탭 목록 한 줄: 저장한 정보, 가격, 조회 위치에서의 거리, 예상 이득 */
public final class FavoriteItem {

    /** 예상 이득을 보여줄 수 있는지, 없으면 그 이유 */
    public enum SavingStatus {
        CALCULATED,   // 계산함 (saving이 있음)
        NO_PRICE,     // 가격을 아직 모르거나 못 가져옴
        TOO_FAR,      // 조회 위치에서 5km 밖
        NO_BASELINE,  // 조회 위치는 있지만 비교할 기준 주유소가 없음 (지도 탭 목록이 비었거나 조회 실패)
        NO_ORIGIN     // 조회 위치를 아직 모름
    }

    private final FavoriteStation favorite;
    // 아직 가져오는 중이면 null
    @Nullable
    private final FavoritePrice price;
    // 조회 위치에서 직선거리(m). 조회 위치를 모르면 null
    @Nullable
    private final Double distance;
    @Nullable
    private final StationSaving saving;
    private final SavingStatus savingStatus;

    public FavoriteItem(FavoriteStation favorite, @Nullable FavoritePrice price, @Nullable Double distance,
                        @Nullable StationSaving saving, SavingStatus savingStatus) {
        this.favorite = favorite;
        this.price = price;
        this.distance = distance;
        this.saving = saving;
        this.savingStatus = savingStatus;
    }

    public FavoriteStation getFavorite() {
        return favorite;
    }

    @Nullable
    public FavoritePrice getPrice() {
        return price;
    }

    @Nullable
    public Double getDistance() {
        return distance;
    }

    @Nullable
    public StationSaving getSaving() {
        return saving;
    }

    public SavingStatus getSavingStatus() {
        return savingStatus;
    }
}
