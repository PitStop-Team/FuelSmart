package com.example.findcheapgas.model;

/** 최근 7일 전국 일일 평균가(avgRecentPrice.do) 한 줄. 날짜는 오피넷 형식 그대로 "20261106" */
public final class DailyAveragePrice {

    private final String date;
    private final FuelType fuelType;
    private final double price;

    public DailyAveragePrice(String date, FuelType fuelType, double price) {
        this.date = date;
        this.fuelType = fuelType;
        this.price = price;
    }

    public String getDate() {
        return date;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    public double getPrice() {
        return price;
    }
}
