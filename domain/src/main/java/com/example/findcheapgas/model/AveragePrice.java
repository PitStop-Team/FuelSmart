package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

/**
 * 오피넷 평균가 한 줄 (표시 가격 기준, 원/L, 소수점 둘째 자리까지 옴).
 * 전국 평균(avgAllPrice.do)이면 시도 코드와 이름이 없고, 시도별 평균(avgSidoPrice.do)이면
 * 오피넷 시도 이름(예: "서울", "충북")과 코드가 들어 있다.
 */
public final class AveragePrice {

    private final FuelType fuelType;
    private final double price;
    @Nullable
    private final String sidoCode;
    @Nullable
    private final String sidoName;

    public AveragePrice(FuelType fuelType, double price, @Nullable String sidoCode, @Nullable String sidoName) {
        this.fuelType = fuelType;
        this.price = price;
        this.sidoCode = sidoCode;
        this.sidoName = sidoName;
    }

    /** 전국 평균 한 줄 */
    public static AveragePrice national(FuelType fuelType, double price) {
        return new AveragePrice(fuelType, price, null, null);
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    public double getPrice() {
        return price;
    }

    @Nullable
    public String getSidoCode() {
        return sidoCode;
    }

    @Nullable
    public String getSidoName() {
        return sidoName;
    }
}
