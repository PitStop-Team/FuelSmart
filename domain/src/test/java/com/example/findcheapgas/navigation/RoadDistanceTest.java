package com.example.findcheapgas.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.DiscountPolicy;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.util.SavingCalculator;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** 구간별 계수 경계값, RouteDistanceProvider 대체 경로, 예상 이득 계산 연결을 검증한다. */
public class RoadDistanceTest {

    private static final double EPS = 1e-9;

    private static GasStation station(String id, int price, double straight, double lat, double lon) {
        return new GasStation(id, "SKE", id, price, straight, 0, 0, lat, lon);
    }

    // ---------- 구간별 계수 경계값 ----------

    @Test
    public void factorBoundaries() {
        assertEquals(1.85, RoadDistanceEstimator.factorFor(0), EPS);
        assertEquals(1.85, RoadDistanceEstimator.factorFor(999.9), EPS);
        assertEquals(1.52, RoadDistanceEstimator.factorFor(1_000), EPS);
        assertEquals(1.52, RoadDistanceEstimator.factorFor(2_999.9), EPS);
        assertEquals(1.36, RoadDistanceEstimator.factorFor(3_000), EPS);
        assertEquals(1.36, RoadDistanceEstimator.factorFor(4_999.9), EPS);
        assertEquals(1.4, RoadDistanceEstimator.factorFor(5_000), EPS);
        assertEquals(1.4, RoadDistanceEstimator.factorFor(12_000), EPS);
    }

    @Test
    public void estimateMetersMultipliesByFactor() {
        assertEquals(500 * 1.85, RoadDistanceEstimator.estimateMeters(500), EPS);
        assertEquals(2_000 * 1.52, RoadDistanceEstimator.estimateMeters(2_000), EPS);
        assertEquals(4_000 * 1.36, RoadDistanceEstimator.estimateMeters(4_000), EPS);
        assertEquals(8_000 * 1.4, RoadDistanceEstimator.estimateMeters(8_000), EPS);
    }

    // ---------- Provider 값 사용 / 대체 경로 ----------

    @Test
    public void usesProviderValueWhenAvailable() {
        GasStation s = station("a", 1700, 500, 37.5, 127.0);
        RoadDistanceResolver resolver =
                RoadDistanceResolver.withFallback(MockRouteDistanceProvider.fixed(777), 37.4, 127.1);
        assertEquals(777, resolver.roadDistanceMeters(s), EPS);
    }

    @Test
    public void fallsBackToTieredFactorWhenProviderHasNoValue() {
        GasStation s = station("a", 1700, 2_000, 37.5, 127.0);
        RoadDistanceResolver resolver =
                RoadDistanceResolver.withFallback(MockRouteDistanceProvider.unavailable(), 37.4, 127.1);
        assertEquals(2_000 * 1.52, resolver.roadDistanceMeters(s), EPS);
    }

    @Test
    public void fallsBackWhenProviderIsNull() {
        GasStation s = station("a", 1700, 4_000, 37.5, 127.0);
        RoadDistanceResolver resolver = RoadDistanceResolver.withFallback(null, 37.4, 127.1);
        assertEquals(4_000 * 1.36, resolver.roadDistanceMeters(s), EPS);
    }

    @Test
    public void userInputProviderIsPerDestination() {
        UserInputRouteDistanceProvider provider = new UserInputRouteDistanceProvider();
        provider.put(37.5, 127.0, 1_234);
        RoadDistanceResolver resolver = RoadDistanceResolver.withFallback(provider, 37.4, 127.1);
        assertEquals(1_234, resolver.roadDistanceMeters(station("known", 1700, 500, 37.5, 127.0)), EPS);
        assertEquals(500 * 1.85, resolver.roadDistanceMeters(station("other", 1700, 500, 38.0, 127.0)), EPS);
    }

    @Test
    public void fixedFactorKeepsLegacyBehavior() {
        assertEquals(500 * 1.4, RoadDistanceResolver.FIXED_FACTOR.roadDistanceMeters(station("a", 1700, 500, 0, 0)),
                EPS);
    }

    // ---------- 예상 이득 계산 연결 ----------

    @Test
    public void savingCalculatorUsesResolverForTravelCost() {
        GasStation near = station("near", 1700, 500, 37.50, 127.00);
        GasStation far = station("far", 1640, 3_000, 37.51, 127.01);
        List<GasStation> all = Arrays.asList(near, far);

        List<StationSaving> tiered = SavingCalculator.calculate(all, CarInfo.DEFAULT, DiscountPolicy.NONE,
                RoadDistanceResolver.withFallback(MockRouteDistanceProvider.unavailable(), 37.49, 126.99));
        assertEquals(500 * 1.85, tiered.get(0).getRoadDistance(), EPS);
        assertEquals(3_000 * 1.36, tiered.get(1).getRoadDistance(), EPS);

        List<StationSaving> provided = SavingCalculator.calculate(all, CarInfo.DEFAULT, DiscountPolicy.NONE,
                RoadDistanceResolver.withFallback(MockRouteDistanceProvider.fixed(10_000), 37.49, 126.99));
        assertEquals(10_000, provided.get(0).getRoadDistance(), EPS);
        // 이동 비용 = 10km ÷ 12 × 가격
        assertEquals(10.0 / 12 * 1_640, provided.get(1).getTravelCost(), 1e-6);
    }

    @Test
    public void calculateWithoutResolverStillUsesLegacyFactor() {
        GasStation s = station("a", 1700, 500, 0, 0);
        List<StationSaving> result =
                SavingCalculator.calculate(Arrays.asList(s), CarInfo.DEFAULT, DiscountPolicy.NONE);
        assertEquals(700, result.get(0).getRoadDistance(), EPS);
        assertTrue(result.get(0).isBest());
    }
}
