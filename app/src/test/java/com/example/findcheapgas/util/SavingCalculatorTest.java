package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.DiscountPolicy;
import com.example.findcheapgas.model.DiscountRule;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.SavingLevel;
import com.example.findcheapgas.model.StationSaving;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** README "예상 이득 계산 방식"의 공식과 예시를 그대로 검증한다. */
public class SavingCalculatorTest {

    private static final double EPSILON = 1e-6;

    private static GasStation station(String id, int price, double distance) {
        return new GasStation(id, "SKE", id, price, distance, 0, 0, 0, 0);
    }

    private static StationSaving find(List<StationSaving> savings, String id) {
        for (StationSaving saving : savings) {
            if (saving.getStation().getId().equals(id)) {
                return saving;
            }
        }
        throw new AssertionError("결과에 없음: " + id);
    }

    // ---------- README 예시 1: 연비 12km/L, 40L, 할인 없음 ----------

    @Test
    public void readmeExample1() {
        GasStation near = station("near", 1700, 500);
        GasStation far = station("far", 1640, 3000);
        List<StationSaving> result = SavingCalculator.calculate(
                Arrays.asList(near, far), CarInfo.DEFAULT, DiscountPolicy.NONE);

        StationSaving nearSaving = find(result, "near");
        StationSaving farSaving = find(result, "far");

        // 기름값
        assertEquals(68_000, nearSaving.getFuelCost(), EPSILON);
        assertEquals(65_600, farSaving.getFuelCost(), EPSILON);
        // 이동 거리 = 직선 × 1.4
        assertEquals(700, nearSaving.getRoadDistance(), EPSILON);
        assertEquals(4_200, farSaving.getRoadDistance(), EPSILON);
        // 이동 비용: 0.7km ÷ 12 × 1,700 = 99.17원, 4.2km ÷ 12 × 1,640 = 574원
        assertEquals(99.1667, nearSaving.getTravelCost(), 1e-3);
        assertEquals(574, farSaving.getTravelCost(), EPSILON);
        assertEquals("99원", FormatUtil.formatWon(nearSaving.getTravelCost()));
        assertEquals("574원", FormatUtil.formatWon(farSaving.getTravelCost()));
        // 예상 총비용
        assertEquals("68,099원", FormatUtil.formatWon(nearSaving.getTotalCost()));
        assertEquals("66,174원", FormatUtil.formatWon(farSaving.getTotalCost()));
        // 예상 이득: 기준(가까운 곳)은 0, 먼 곳은 약 1,925원
        assertTrue(nearSaving.isBaseline());
        assertEquals(0, nearSaving.getSaving(), 0);
        assertFalse(farSaving.isBaseline());
        assertEquals(1_925, farSaving.getSavingWon());
        assertEquals("약 1,925원 이득", FormatUtil.formatSaving(farSaving));
        assertEquals("기준 (가장 가까운 곳)", FormatUtil.formatSaving(nearSaving));
        // 1위와 색
        assertTrue(farSaving.isBest());
        assertFalse(nearSaving.isBest());
        assertEquals(SavingLevel.GAIN, farSaving.getLevel());
        assertEquals(SavingLevel.SIMILAR, nearSaving.getLevel());
    }

    @Test
    public void calculationIsInDoubleUntilDisplayed() {
        List<StationSaving> result = SavingCalculator.calculate(
                Arrays.asList(station("near", 1700, 500), station("far", 1640, 3000)),
                CarInfo.DEFAULT, DiscountPolicy.NONE);
        // 반올림하지 않은 값: 68,099.1667 − 66,174 = 1,925.1667
        assertEquals(1_925.1667, find(result, "far").getSaving(), 1e-3);
    }

    // ---------- README 예시 2: 리터당 100원, 한도 3,000원, 40L → 3,000원 ----------

    @Test
    public void readmeExample2_capLimitsDiscount() {
        DiscountRule rule = new DiscountRule(100, 3_000);
        assertEquals(3_000, rule.amountFor(40), EPSILON);
        assertTrue(rule.isCapAppliedFor(40));
        assertTrue(rule.isCapEntered());
    }

    @Test
    public void discount_belowCap_notApplied() {
        DiscountRule rule = new DiscountRule(100, 5_000);
        assertEquals(4_000, rule.amountFor(40), EPSILON);
        assertFalse(rule.isCapAppliedFor(40));
    }

    @Test
    public void discount_noCap_unlimited() {
        DiscountRule rule = new DiscountRule(200, null);
        assertEquals(8_000, rule.amountFor(40), EPSILON);
        assertFalse(rule.isCapEntered());
        assertFalse(rule.isCapAppliedFor(40));
    }

    @Test
    public void discount_none() {
        assertFalse(DiscountRule.NONE.hasDiscount());
        assertEquals(0, DiscountRule.NONE.amountFor(40), 0);
    }

    @Test
    public void discountIsReflectedInFuelCostAndResult() {
        GasStation near = station("near", 1700, 500);
        GasStation far = station("far", 1710, 1000);
        DiscountPolicy policy = s -> s == far ? new DiscountRule(100, 3_000) : DiscountRule.NONE;
        List<StationSaving> result = SavingCalculator.calculate(Arrays.asList(near, far), CarInfo.DEFAULT, policy);

        StationSaving farSaving = find(result, "far");
        assertEquals(3_000, farSaving.getDiscountAmount(), EPSILON);
        assertEquals(1710 * 40 - 3_000, farSaving.getFuelCost(), EPSILON);
        assertTrue(farSaving.isDiscountCapEntered());
        assertTrue(farSaving.isDiscountCapApplied());
        // 표시 가격은 더 비싸도 할인 덕분에 이득: 기준 68,099.17 − (65,400 + 199.5) = 2,499.67
        assertEquals(2_500, farSaving.getSavingWon());
        assertEquals(0, find(result, "near").getDiscountAmount(), 0);
        assertFalse(find(result, "near").isDiscountCapEntered());
    }

    // ---------- 기준 주유소 ----------

    @Test
    public void singleStation_isBaselineAndBest() {
        List<StationSaving> result = SavingCalculator.calculate(
                Collections.singletonList(station("only", 1700, 800)), CarInfo.DEFAULT, DiscountPolicy.NONE);
        assertEquals(1, result.size());
        StationSaving only = result.get(0);
        assertTrue(only.isBaseline());
        assertTrue(only.isBest());
        assertEquals(0, only.getSavingWon());
        assertEquals(SavingLevel.SIMILAR, only.getLevel());
    }

    @Test
    public void baselineOwnSavingIsExactlyZero() {
        List<StationSaving> result = SavingCalculator.calculate(
                Arrays.asList(station("a", 1683, 1234.5), station("b", 1650, 2500)),
                CarInfo.userEntered(137, 55), DiscountPolicy.NONE);
        StationSaving baseline = find(result, "a");
        assertTrue(baseline.isBaseline());
        assertEquals(0, baseline.getSaving(), 0);
        assertEquals(baseline.getTotalCost(), baseline.getBaselineTotalCost(), 0);
    }

    @Test
    public void tiedNearest_cheaperIsBaseline() {
        GasStation expensive = station("expensive", 1720, 600);
        GasStation cheap = station("cheap", 1690, 600);
        GasStation far = station("far", 1600, 2000);
        List<StationSaving> result = SavingCalculator.calculate(
                Arrays.asList(expensive, cheap, far), CarInfo.DEFAULT, DiscountPolicy.NONE);
        assertSame(cheap, SavingCalculator.findBaseline(Arrays.asList(expensive, cheap, far)));
        assertTrue(find(result, "cheap").isBaseline());
        assertFalse(find(result, "expensive").isBaseline());
        // 같은 거리, 30원 비싼 곳: 40L × 30원 + 이동 비용 차이 → 손해
        assertTrue(find(result, "expensive").getSavingWon() < 0);
        assertSame(cheap, find(result, "far").getBaseline());
    }

    @Test
    public void nearestIsBest_whenNobodySaves() {
        List<StationSaving> result = SavingCalculator.calculate(
                Arrays.asList(station("far", 1700, 3000), station("near", 1650, 300)),
                CarInfo.DEFAULT, DiscountPolicy.NONE);
        StationSaving near = find(result, "near");
        StationSaving far = find(result, "far");
        assertTrue(near.isBaseline());
        assertTrue(near.isBest());
        assertFalse(far.isBest());
        assertTrue(far.getSavingWon() < 0);
        assertEquals(SavingLevel.LOSS, far.getLevel());
        assertTrue(FormatUtil.formatSaving(far).endsWith("원 손해"));
    }

    @Test
    public void onlyOneBest() {
        List<StationSaving> result = SavingCalculator.calculate(
                Arrays.asList(station("near", 1700, 500), station("x", 1600, 2000), station("y", 1600, 2000)),
                CarInfo.DEFAULT, DiscountPolicy.NONE);
        int bestCount = 0;
        for (StationSaving saving : result) {
            if (saving.isBest()) {
                bestCount++;
            }
        }
        assertEquals(1, bestCount);
    }

    @Test
    public void resultKeepsInputOrder() {
        List<GasStation> input = Arrays.asList(station("b", 1700, 2000), station("a", 1650, 500));
        List<StationSaving> result = SavingCalculator.calculate(input, CarInfo.DEFAULT, DiscountPolicy.NONE);
        assertEquals("b", result.get(0).getStation().getId());
        assertEquals("a", result.get(1).getStation().getId());
    }

    @Test
    public void emptyList_emptyResult() {
        assertTrue(SavingCalculator.calculate(Collections.emptyList(), CarInfo.DEFAULT, DiscountPolicy.NONE)
                .isEmpty());
    }

    @Test
    public void userCarInfoIsUsed() {
        // 연비 15km/L, 50L: 기름값 1,640 × 50 = 82,000, 이동 비용 4.2km ÷ 15 × 1,640 = 459.2
        List<StationSaving> result = SavingCalculator.calculate(
                Arrays.asList(station("near", 1700, 500), station("far", 1640, 3000)),
                CarInfo.userEntered(150, 50), DiscountPolicy.NONE);
        StationSaving far = find(result, "far");
        assertEquals(82_000, far.getFuelCost(), EPSILON);
        assertEquals(459.2, far.getTravelCost(), EPSILON);
        // 기준: 85,000 + 0.7 ÷ 15 × 1,700 = 85,079.33 → 이득 85,079.33 − 82,459.2 = 2,620.13
        assertEquals(2_620, far.getSavingWon());
    }

    // ---------- 이득 구간 ----------

    @Test
    public void savingLevel_thresholds() {
        assertEquals(SavingLevel.GAIN, SavingLevel.of(1_000));
        assertEquals(SavingLevel.SIMILAR, SavingLevel.of(999));
        assertEquals(SavingLevel.SIMILAR, SavingLevel.of(0));
        assertEquals(SavingLevel.SIMILAR, SavingLevel.of(-999));
        assertEquals(SavingLevel.LOSS, SavingLevel.of(-1_000));
    }
}
