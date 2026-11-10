package com.example.findcheapgas.model;

import java.io.Serializable;

/**
 * 주유소 한 곳의 예상 이득 계산 결과. SavingCalculator가 만든다.
 * 금액은 모두 실수(원)로 들고 있고, 화면에 보일 때만 원 단위로 반올림한다.
 * 상세 화면에 계산 내역을 넘길 때 Intent에 그대로 담는다 (Serializable).
 *
 * 선호 브랜드 여부(preferred)는 예상 이득순 정렬에만 쓴다. 정렬용 점수에 500원을 더할 뿐,
 * 화면에 보이는 예상 이득과 이득 1위 판정은 실제 예상 이득 그대로다.
 */
public final class StationSaving implements Serializable {

    private static final long serialVersionUID = 2L;

    /** 예상 이득순 정렬에서 선호 브랜드의 정렬용 점수에 더하는 금액(원) */
    public static final long PREFERRED_SORT_BONUS = 500;

    private final GasStation station;
    private final GasStation baseline;      // 비교 기준 (화면 목록에서 가장 가까운 주유소)
    private final CarInfo carInfo;
    private final DiscountRule discountRule;
    private final double discountAmount;    // 할인액 (한도 적용 후)
    private final double fuelCost;          // 기름값 = 표시 가격 × 주유량 − 할인액
    private final double roadDistance;      // 추정 도로 거리(m, 편도) = 직선거리 × 1.4
    private final double travelCost;        // 이동 비용 = 이동 거리 ÷ 연비 × 표시 가격
    private final double totalCost;         // 예상 총비용 = 기름값 + 이동 비용
    private final double baselineTotalCost;
    private final boolean best;             // 목록에서 예상 이득 1위인지
    private final boolean preferred;        // 선호 브랜드인지 (정렬과 ★ 표시에만 씀)

    public StationSaving(GasStation station, GasStation baseline, CarInfo carInfo, DiscountRule discountRule,
                         double discountAmount, double fuelCost, double roadDistance, double travelCost,
                         double totalCost, double baselineTotalCost, boolean best) {
        this(station, baseline, carInfo, discountRule, discountAmount, fuelCost, roadDistance, travelCost,
                totalCost, baselineTotalCost, best, false);
    }

    private StationSaving(GasStation station, GasStation baseline, CarInfo carInfo, DiscountRule discountRule,
                          double discountAmount, double fuelCost, double roadDistance, double travelCost,
                          double totalCost, double baselineTotalCost, boolean best, boolean preferred) {
        this.station = station;
        this.baseline = baseline;
        this.carInfo = carInfo;
        this.discountRule = discountRule;
        this.discountAmount = discountAmount;
        this.fuelCost = fuelCost;
        this.roadDistance = roadDistance;
        this.travelCost = travelCost;
        this.totalCost = totalCost;
        this.baselineTotalCost = baselineTotalCost;
        this.best = best;
        this.preferred = preferred;
    }

    /** 이득 1위 표시를 한 같은 결과 */
    public StationSaving markedBest() {
        return new StationSaving(station, baseline, carInfo, discountRule, discountAmount, fuelCost,
                roadDistance, travelCost, totalCost, baselineTotalCost, true, preferred);
    }

    /** 선호 브랜드 표시를 바꾼 같은 결과. 금액과 이득 1위 여부는 바뀌지 않는다. */
    public StationSaving withPreferred(boolean newPreferred) {
        return new StationSaving(station, baseline, carInfo, discountRule, discountAmount, fuelCost,
                roadDistance, travelCost, totalCost, baselineTotalCost, best, newPreferred);
    }

    public GasStation getStation() {
        return station;
    }

    public GasStation getBaseline() {
        return baseline;
    }

    public CarInfo getCarInfo() {
        return carInfo;
    }

    public DiscountRule getDiscountRule() {
        return discountRule;
    }

    public double getDiscountAmount() {
        return discountAmount;
    }

    /** 할인 전 금액: 표시 가격 × 주유량 */
    public double getFuelCostBeforeDiscount() {
        return (double) station.getPrice() * carInfo.getFuelAmount();
    }

    public double getFuelCost() {
        return fuelCost;
    }

    /** 한도 때문에 할인액이 줄었는지 */
    public boolean isDiscountCapApplied() {
        return discountRule.isCapAppliedFor(carInfo.getFuelAmount());
    }

    /** 1회 할인 한도를 입력했는지. 아니면 화면에 "한도 미적용"을 표시한다. */
    public boolean isDiscountCapEntered() {
        return discountRule.isCapEntered();
    }

    public double getStraightDistance() {
        return station.getDistance();
    }

    public double getRoadDistance() {
        return roadDistance;
    }

    public double getTravelCost() {
        return travelCost;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public double getBaselineTotalCost() {
        return baselineTotalCost;
    }

    /** 예상 이득 = 기준 주유소 예상 총비용 − 이 주유소 예상 총비용. 음수면 손해 */
    public double getSaving() {
        return baselineTotalCost - totalCost;
    }

    /** 화면에 보이는 원 단위 예상 이득 */
    public long getSavingWon() {
        return Math.round(getSaving());
    }

    /**
     * 예상 이득순 정렬에 쓰는 점수(원). 원 단위 예상 이득에 선호 브랜드면 500원을 더한다.
     * 주유소마다 정해진 하나의 숫자로 비교하므로 정렬 규칙(전이성)이 깨지지 않는다.
     */
    public long getSortScoreWon() {
        return getSavingWon() + (preferred ? PREFERRED_SORT_BONUS : 0);
    }

    public SavingLevel getLevel() {
        return SavingLevel.of(getSavingWon());
    }

    public boolean isBaseline() {
        return station.getId().equals(baseline.getId());
    }

    public boolean isBest() {
        return best;
    }

    public boolean isPreferred() {
        return preferred;
    }
}
