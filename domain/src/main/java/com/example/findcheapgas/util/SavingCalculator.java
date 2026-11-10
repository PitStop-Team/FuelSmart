package com.example.findcheapgas.util;

import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.DiscountPolicy;
import com.example.findcheapgas.model.DiscountRule;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.navigation.RoadDistanceResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 예상 이득 계산 (README "예상 이득 계산 방식"). 안드로이드에 의존하지 않는다.
 *
 * 할인액      = min(리터당 할인 × 주유량, 1회 할인 한도)   한도가 없으면 리터당 할인 × 주유량
 * 기름값      = 표시 가격 × 주유량 − 할인액
 * 이동 거리   = RoadDistanceResolver가 준 도로 거리 (편도). resolver를 안 주면 직선거리 × 1.4
 * 이동 비용   = 이동 거리 ÷ 연비 × 표시 가격
 * 예상 총비용 = 기름값 + 이동 비용
 * 예상 이득   = 기준 주유소 예상 총비용 − 이 주유소 예상 총비용
 *
 * 기준 주유소는 넘겨받은 목록에서 가장 가까운 곳이고, 가장 가까운 곳이 여럿이면 그중 싼 곳이다.
 * 직선거리는 조회 위치에서 주유소까지의 거리(GasStation.getDistance())를 쓴다.
 */
public final class SavingCalculator {

    /** 직선거리 → 도로 거리 추정 계수 */
    public static final double ROAD_DISTANCE_FACTOR = 1.4;

    // 이득 1위 판정: 실제 원 단위 예상 이득이 큰 곳, 같으면 싼 곳, 그다음 가까운 곳. 선호 브랜드는 반영하지 않는다.
    private static final Comparator<StationSaving> BY_ACTUAL_SAVING =
            Comparator.comparingLong((StationSaving s) -> -s.getSavingWon())
                    .thenComparingInt(s -> s.getStation().getPrice())
                    .thenComparingDouble(s -> s.getStation().getDistance());

    private SavingCalculator() {
    }

    /**
     * 목록의 모든 주유소에 대해 예상 이득을 계산한다. 결과는 입력과 같은 순서이고 입력은 바꾸지 않는다.
     * 예상 이득이 가장 큰 곳에 이득 1위 표시를 한다. 원 단위로 1원 이상 이득인 곳이 없으면 기준 주유소가 1위다.
     */
    public static List<StationSaving> calculate(List<GasStation> stations, CarInfo carInfo, DiscountPolicy policy) {
        return calculate(stations, carInfo, policy, RoadDistanceResolver.FIXED_FACTOR);
    }

    /** calculate()와 같고, 도로 거리를 resolver로 구한다. */
    public static List<StationSaving> calculate(List<GasStation> stations, CarInfo carInfo, DiscountPolicy policy,
                                                RoadDistanceResolver distances) {
        if (stations.isEmpty()) {
            return Collections.emptyList();
        }
        GasStation baseline = findBaseline(stations);
        double baselineTotal = totalCost(baseline, carInfo, policy.ruleFor(baseline), distances);

        List<StationSaving> results = new ArrayList<>(stations.size());
        for (GasStation station : stations) {
            results.add(estimate(station, carInfo, policy.ruleFor(station), baseline, baselineTotal, distances));
        }

        // 실제 예상 이득이 가장 큰 곳이 1위. 그래도 이득이 1원도 안 되면 가장 가까운 곳이 가장 유리하다.
        StationSaving best = Collections.min(results, BY_ACTUAL_SAVING);
        int bestIndex = best.getSavingWon() >= 1 ? results.indexOf(best) : stations.indexOf(baseline);
        results.set(bestIndex, results.get(bestIndex).markedBest());
        return results;
    }

    /**
     * 목록 밖의 주유소 한 곳을 이미 정해진 기준 주유소와 비교한다 (즐겨찾기 탭).
     * calculate()와 같은 공식, 같은 할인 정책을 쓰므로 그 주유소가 목록 안에 있었을 때와 같은 값이 나온다.
     * 이득 1위나 선호 브랜드 표시는 하지 않는다.
     */
    public static StationSaving estimateAgainst(GasStation station, GasStation baseline, CarInfo carInfo,
                                                DiscountPolicy policy) {
        return estimateAgainst(station, baseline, carInfo, policy, RoadDistanceResolver.FIXED_FACTOR);
    }

    /** estimateAgainst()와 같고, 도로 거리를 resolver로 구한다. */
    public static StationSaving estimateAgainst(GasStation station, GasStation baseline, CarInfo carInfo,
                                                DiscountPolicy policy, RoadDistanceResolver distances) {
        double baselineTotal = totalCost(baseline, carInfo, policy.ruleFor(baseline), distances);
        return estimate(station, carInfo, policy.ruleFor(station), baseline, baselineTotal, distances);
    }

    /** 가장 가까운 주유소. 거리가 같으면 싼 곳. 목록이 비어 있으면 IllegalArgumentException */
    public static GasStation findBaseline(List<GasStation> stations) {
        if (stations.isEmpty()) {
            throw new IllegalArgumentException("주유소 목록이 비어 있습니다.");
        }
        GasStation baseline = stations.get(0);
        for (GasStation station : stations) {
            if (station.getDistance() < baseline.getDistance()
                    || (station.getDistance() == baseline.getDistance() && station.getPrice() < baseline.getPrice())) {
                baseline = station;
            }
        }
        return baseline;
    }

    private static double totalCost(GasStation station, CarInfo carInfo, DiscountRule rule,
                                    RoadDistanceResolver distances) {
        return fuelCost(station, carInfo, rule) + travelCost(station, carInfo, distances);
    }

    private static double fuelCost(GasStation station, CarInfo carInfo, DiscountRule rule) {
        double liters = carInfo.getFuelAmount();
        return station.getPrice() * liters - rule.amountFor(liters);
    }

    private static double travelCost(GasStation station, CarInfo carInfo, RoadDistanceResolver distances) {
        return distances.roadDistanceMeters(station) / 1000.0 / carInfo.getFuelEfficiency() * station.getPrice();
    }

    private static StationSaving estimate(GasStation station, CarInfo carInfo, DiscountRule rule,
                                          GasStation baseline, double baselineTotal,
                                          RoadDistanceResolver distances) {
        return new StationSaving(station, baseline, carInfo, rule,
                rule.amountFor(carInfo.getFuelAmount()),
                fuelCost(station, carInfo, rule),
                distances.roadDistanceMeters(station),
                travelCost(station, carInfo, distances),
                totalCost(station, carInfo, rule, distances),
                baselineTotal, false);
    }
}
