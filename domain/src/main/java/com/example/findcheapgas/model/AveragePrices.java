package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 상세 화면 평균가 비교에 쓰는 평균가 전체 (4개 유종).
 * 전국 평균은 항상 있다. 시도별 평균은 가져오지 못했으면 빈 목록이고, 그때는 지역 평균 줄만 빠진다.
 */
public final class AveragePrices {

    private final List<AveragePrice> national;
    private final List<AveragePrice> sido;

    public AveragePrices(List<AveragePrice> national, List<AveragePrice> sido) {
        this.national = Collections.unmodifiableList(new ArrayList<>(national));
        this.sido = Collections.unmodifiableList(new ArrayList<>(sido));
    }

    public List<AveragePrice> getNational() {
        return national;
    }

    public List<AveragePrice> getSido() {
        return sido;
    }

    /** 그 유종의 전국 평균. 없으면 null */
    @Nullable
    public AveragePrice nationalOf(FuelType fuelType) {
        for (AveragePrice price : national) {
            if (price.getFuelType() == fuelType) {
                return price;
            }
        }
        return null;
    }
}
