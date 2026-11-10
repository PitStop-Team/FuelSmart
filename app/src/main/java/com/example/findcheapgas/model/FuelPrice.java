package com.example.findcheapgas.model;

/** 주유소 상세정보의 유종별 가격 한 줄. 기준 일시는 오피넷 값 그대로(yyyyMMdd, HHmmss) 두고 화면에서 바꾼다. */
public class FuelPrice {

    private final FuelType fuelType;
    private final int price;          // 원/리터
    private final String tradeDate;   // TRADE_DT, 없으면 빈 문자열
    private final String tradeTime;   // TRADE_TM, 없으면 빈 문자열

    public FuelPrice(FuelType fuelType, int price, String tradeDate, String tradeTime) {
        this.fuelType = fuelType;
        this.price = price;
        this.tradeDate = tradeDate;
        this.tradeTime = tradeTime;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    public int getPrice() {
        return price;
    }

    public String getTradeDate() {
        return tradeDate;
    }

    public String getTradeTime() {
        return tradeTime;
    }
}
