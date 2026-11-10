package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

/**
 * 즐겨찾기 주유소 한 곳의 지금 고른 유종 가격과, 그 가격을 어디서 가져왔는지.
 * 가격이 없으면 이유(Status)를 가진다.
 */
public final class FavoritePrice {

    public enum Status {
        AVAILABLE,    // 가격 있음
        NOT_SOLD,     // 상세정보에 이 유종 가격이 없음 (팔지 않음)
        NOT_FOUND,    // 오피넷에서 주유소를 찾을 수 없음 (폐업 등)
        DAILY_LIMIT,  // 오늘 호출 한도에 도달해서 조회하지 않음
        NETWORK,      // 인터넷 연결 실패
        API_KEY,      // 오피넷 API 키가 설정되지 않음
        OPINET        // 오피넷 오류
    }

    /** 가격 출처. 오피넷 호출을 아끼려고 위에서부터 차례로 확인한다. */
    public enum Source {
        AROUND,   // 지도 탭이 이미 받아 둔 주변 목록 (호출 없음)
        CACHE,    // 상세정보 캐시 (호출 없음)
        FETCHED   // 이번에 상세정보를 새로 가져옴 (가짜 데이터 또는 실제 API)
    }

    private final Status status;
    @Nullable
    private final Source source;
    private final FuelType fuelType;
    private final int price;
    // 상세정보를 가져온 시각 (캐시 저장 시각). 주변 목록에서 가져왔으면 0
    private final long fetchedAt;

    private FavoritePrice(Status status, @Nullable Source source, FuelType fuelType, int price, long fetchedAt) {
        this.status = status;
        this.source = source;
        this.fuelType = fuelType;
        this.price = price;
        this.fetchedAt = fetchedAt;
    }

    public static FavoritePrice available(Source source, FuelType fuelType, int price, long fetchedAt) {
        return new FavoritePrice(Status.AVAILABLE, source, fuelType, price, fetchedAt);
    }

    public static FavoritePrice notSold(Source source, FuelType fuelType, long fetchedAt) {
        return new FavoritePrice(Status.NOT_SOLD, source, fuelType, 0, fetchedAt);
    }

    public static FavoritePrice failed(Status status, FuelType fuelType) {
        return new FavoritePrice(status, null, fuelType, 0, 0);
    }

    public Status getStatus() {
        return status;
    }

    public boolean isAvailable() {
        return status == Status.AVAILABLE;
    }

    /** 가격이 없으면 null */
    @Nullable
    public Source getSource() {
        return source;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    /** 원/리터. 가격이 없으면 0 */
    public int getPrice() {
        return price;
    }

    public long getFetchedAt() {
        return fetchedAt;
    }
}
