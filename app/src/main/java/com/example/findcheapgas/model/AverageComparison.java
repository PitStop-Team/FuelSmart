package com.example.findcheapgas.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 상세 화면 평균가 비교 결과. 지금 고른 유종의 그 주유소 표시 가격(할인 전)을 전국·지역 평균과 비교한다.
 * 금액은 화면에 보이는 숫자끼리 맞도록 평균을 원 단위로 반올림한 뒤 차이를 구한다.
 */
public final class AverageComparison {

    public enum Status {
        READY,       // 비교 줄이 하나 이상 있음
        NOT_SOLD,    // 이 주유소는 이 유종을 팔지 않음
        NO_PRICE,    // 이 주유소 가격을 알 수 없음 (상세 조회 실패, 목록에서도 가격 없음)
        NO_AVERAGE   // 이 유종의 평균가가 응답에 없음
    }

    /** 비교 한 줄. 예: 전국 평균 1,712원보다 리터당 41원 저렴, 40L 기준 약 1,640원 */
    public static final class Line {
        private final String regionLabel;
        private final int averageWon;
        private final int differencePerLiter;
        private final int fuelAmount;

        public Line(String regionLabel, int averageWon, int differencePerLiter, int fuelAmount) {
            this.regionLabel = regionLabel;
            this.averageWon = averageWon;
            this.differencePerLiter = differencePerLiter;
            this.fuelAmount = fuelAmount;
        }

        /** "전국", "서울" 같은 지역 이름 */
        public String getRegionLabel() {
            return regionLabel;
        }

        /** 원 단위로 반올림한 평균가 */
        public int getAverageWon() {
            return averageWon;
        }

        /** 이 주유소 가격 − 평균가 (원/L). 음수면 평균보다 저렴, 양수면 비쌈, 0이면 같음 */
        public int getDifferencePerLiter() {
            return differencePerLiter;
        }

        public int getFuelAmount() {
            return fuelAmount;
        }

        /** 리터당 차이 × 내 주유량 (원). 부호는 getDifferencePerLiter와 같다. */
        public int getDifferenceForFuelAmount() {
            return differencePerLiter * fuelAmount;
        }
    }

    private final Status status;
    private final FuelType fuelType;
    private final int stationPrice;
    private final List<Line> lines;

    public AverageComparison(Status status, FuelType fuelType, int stationPrice, List<Line> lines) {
        this.status = status;
        this.fuelType = fuelType;
        this.stationPrice = stationPrice;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public Status getStatus() {
        return status;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    /** 비교에 쓴 이 주유소 표시 가격. 가격을 모르면 0 */
    public int getStationPrice() {
        return stationPrice;
    }

    /** 전국, 지역 순서. READY가 아니면 빈 목록 */
    public List<Line> getLines() {
        return lines;
    }
}
