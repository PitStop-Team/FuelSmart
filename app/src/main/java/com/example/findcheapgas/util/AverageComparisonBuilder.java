package com.example.findcheapgas.util;

import androidx.annotation.Nullable;

import com.example.findcheapgas.model.AverageComparison;
import com.example.findcheapgas.model.AveragePrice;
import com.example.findcheapgas.model.AveragePrices;
import com.example.findcheapgas.model.FuelPrice;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.StationDetail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 상세 화면 평균가 비교를 만든다.
 * 비교할 가격은 지금 고른 유종의 그 주유소 표시 가격(할인 전)이다. 평균가도 표시 가격이라 할인을 넣지 않는다.
 * - 상세 조회 결과가 있으면 그 유종 가격을 쓰고, 그 유종이 없으면 "팔지 않음"이다.
 * - 상세 조회가 실패했으면 목록에서 넘겨받은 가격을 쓴다. 그것도 없으면 "가격 없음"이다.
 * 평균가는 원 단위로 반올림한 뒤 차이를 구해서, 화면에 보이는 두 숫자의 차이와 리터당 차이가 같게 한다.
 * 지역은 상세정보 주소의 첫 단어로 정한다(SidoMatcher). 시도를 못 찾으면 지역 줄만 뺀다.
 */
public final class AverageComparisonBuilder {

    public static final String NATIONAL_LABEL = "전국";

    private AverageComparisonBuilder() {
    }

    /**
     * @param detail       상세 조회 결과. 실패했으면 null
     * @param listedPrice  목록에서 넘겨받은 그 유종 가격. 모르면 0
     * @param fallbackAddress 상세 조회가 실패했을 때 쓸 주소 (즐겨찾기에 저장된 주소). 없으면 null
     * @param fuelAmount   내 차 정보의 평소 주유량 (L)
     */
    public static AverageComparison build(FuelType fuelType, @Nullable StationDetail detail, int listedPrice,
                                          @Nullable String fallbackAddress, AveragePrices averages, int fuelAmount) {
        int stationPrice;
        String address;
        if (detail != null) {
            FuelPrice price = priceOf(detail.getPrices(), fuelType);
            if (price == null) {
                return empty(AverageComparison.Status.NOT_SOLD, fuelType, 0);
            }
            stationPrice = price.getPrice();
            address = detail.getAddress();
        } else {
            stationPrice = listedPrice;
            address = fallbackAddress;
        }
        if (stationPrice <= 0) {
            return empty(AverageComparison.Status.NO_PRICE, fuelType, 0);
        }

        List<AverageComparison.Line> lines = new ArrayList<>();
        AveragePrice national = averages.nationalOf(fuelType);
        if (national != null) {
            lines.add(line(NATIONAL_LABEL, national.getPrice(), stationPrice, fuelAmount));
        }
        AveragePrice sido = SidoMatcher.find(address, averages.getSido(), fuelType);
        if (sido != null && sido.getSidoName() != null) {
            lines.add(line(SidoMatcher.normalizeSidoName(sido.getSidoName()), sido.getPrice(), stationPrice,
                    fuelAmount));
        }
        if (lines.isEmpty()) {
            return empty(AverageComparison.Status.NO_AVERAGE, fuelType, stationPrice);
        }
        return new AverageComparison(AverageComparison.Status.READY, fuelType, stationPrice, lines);
    }

    private static AverageComparison.Line line(String label, double average, int stationPrice, int fuelAmount) {
        int averageWon = (int) Math.round(average);
        return new AverageComparison.Line(label, averageWon, stationPrice - averageWon, fuelAmount);
    }

    private static AverageComparison empty(AverageComparison.Status status, FuelType fuelType, int stationPrice) {
        return new AverageComparison(status, fuelType, stationPrice, Collections.<AverageComparison.Line>emptyList());
    }

    @Nullable
    private static FuelPrice priceOf(List<FuelPrice> prices, FuelType fuelType) {
        for (FuelPrice price : prices) {
            if (price.getFuelType() == fuelType && price.getPrice() > 0) {
                return price;
            }
        }
        return null;
    }
}
