package com.example.findcheapgas.vehicle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.DiscountPolicy;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.util.SavingCalculator;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class LowFuelTest {

    private static OptionalInt p(int value) {
        return OptionalInt.of(value);
    }

    // ---------- 트리거 ----------

    @Test
    public void firstEntryBelowThresholdSuggests() {
        LowFuelTrigger trigger = new LowFuelTrigger();
        assertFalse(trigger.onFuelPercent(p(60)));
        assertFalse(trigger.onFuelPercent(p(30)));
        assertTrue(trigger.onFuelPercent(p(24)));
    }

    @Test
    public void exactlyAtThresholdDoesNotSuggest() {
        LowFuelTrigger trigger = new LowFuelTrigger();
        assertEquals(25, LowFuelTrigger.SUGGEST_BELOW_PERCENT);
        assertFalse(trigger.onFuelPercent(p(25)));
        assertTrue(trigger.onFuelPercent(p(24)));
    }

    @Test
    public void staysQuietWhileFuelRemainsLow() {
        LowFuelTrigger trigger = new LowFuelTrigger();
        assertTrue(trigger.onFuelPercent(p(20)));
        assertFalse(trigger.onFuelPercent(p(20)));
        assertFalse(trigger.onFuelPercent(p(19)));
        assertFalse(trigger.onFuelPercent(p(5)));
    }

    @Test
    public void dismissingNeedsNoExtraStateSoSameDropStaysQuiet() {
        // 거절해도 트리거에는 알릴 것이 없다. 이미 낸 제안은 같은 하락 구간에서 다시 나오지 않는다.
        LowFuelTrigger trigger = new LowFuelTrigger();
        assertTrue(trigger.onFuelPercent(p(22)));
        // (운전자가 "나중에"를 누른 뒤에도 값이 계속 들어온다)
        for (int percent = 22; percent >= 3; percent--) {
            assertFalse(trigger.onFuelPercent(p(percent)));
        }
    }

    @Test
    public void rearmsOnlyAfterRecoveryToRearmLevel() {
        LowFuelTrigger trigger = new LowFuelTrigger();
        assertEquals(35, LowFuelTrigger.REARM_AT_PERCENT);
        assertTrue(trigger.onFuelPercent(p(20)));
        // 34%까지 올라도 아직 재무장하지 않는다
        assertFalse(trigger.onFuelPercent(p(34)));
        assertFalse(trigger.onFuelPercent(p(20)));
        // 35% 이상으로 회복하면 재무장
        assertFalse(trigger.onFuelPercent(p(35)));
        assertFalse(trigger.onFuelPercent(p(80)));
        assertTrue(trigger.onFuelPercent(p(24)));
    }

    @Test
    public void exactlyRearmLevelRearms() {
        LowFuelTrigger trigger = new LowFuelTrigger();
        assertTrue(trigger.onFuelPercent(p(10)));
        assertFalse(trigger.onFuelPercent(p(35)));
        assertTrue(trigger.onFuelPercent(p(10)));
    }

    @Test
    public void missingValueDoesNothing() {
        LowFuelTrigger trigger = new LowFuelTrigger();
        assertFalse(trigger.onFuelPercent(OptionalInt.empty()));
        assertFalse(trigger.onFuelPercent(null));
        // 값이 없는 동안 상태가 바뀌지 않는다: 이어서 낮은 값이 오면 제안한다
        assertTrue(trigger.onFuelPercent(p(10)));
        assertFalse(trigger.onFuelPercent(OptionalInt.empty()));
        assertFalse(trigger.onFuelPercent(p(10)));
    }

    @Test
    public void unsupportedVehicleNeverSuggests() {
        // 미지원 차량: 캐시에 값이 없어 getFuelPercent()가 항상 empty
        LowFuelTrigger trigger = new LowFuelTrigger();
        VehicleDataSource unsupported = MockVehicleDataSource.unsupported();
        for (int i = 0; i < 5; i++) {
            assertFalse(trigger.onFuelPercent(unsupported.getFuelPercent()));
        }
        CachedVehicleDataSource cache = new CachedVehicleDataSource();
        assertFalse(trigger.onFuelPercent(cache.getFuelPercent()));
    }

    // ---------- 제안 내용 ----------

    private static GasStation station(String name, int price, double distance) {
        return new GasStation(name, "SKE", name, price, distance, 0, 0, 0, 0);
    }

    private static List<StationSaving> displayed(GasStation... stations) {
        return SavingCalculator.calculate(Arrays.asList(stations), CarInfo.DEFAULT, DiscountPolicy.NONE);
    }

    @Test
    public void emptyListGivesNoSuggestion() {
        assertFalse(LowFuelSuggestion.create(20, Collections.emptyList(), false).isPresent());
        assertFalse(LowFuelSuggestion.create(20, null, false).isPresent());
    }

    @Test
    public void picksBestSavingStationAndMentionsSaving() {
        List<StationSaving> list = displayed(station("가까운주유소", 1700, 500), station("싼주유소", 1640, 3000));
        Optional<LowFuelSuggestion> suggestion = LowFuelSuggestion.create(22, list, false);
        assertTrue(suggestion.isPresent());
        assertEquals("싼주유소", suggestion.get().getSaving().getStation().getName());
        assertEquals(LowFuelSuggestion.Action.ROUTE_HERE, suggestion.get().getAction());
        assertEquals("여기로 안내", suggestion.get().actionLabel());
        String message = suggestion.get().message();
        assertTrue(message, message.startsWith("연료가 부족해요(약 22%). 싼주유소가 "));
        assertTrue(message, message.contains("가장 가까운 곳보다 약 "));
        assertTrue(message, message.endsWith("이득이에요."));
    }

    @Test
    public void withRouteTheActionIsAddWaypoint() {
        List<StationSaving> list = displayed(station("가까운주유소", 1700, 500), station("싼주유소", 1640, 3000));
        LowFuelSuggestion suggestion = LowFuelSuggestion.create(20, list, true).get();
        assertEquals(LowFuelSuggestion.Action.ADD_WAYPOINT, suggestion.getAction());
        assertEquals("경유지로 추가", suggestion.actionLabel());
    }

    @Test
    public void smallSavingIsShownAsSimilarWithoutAmount() {
        // 가격 차이가 작아 이득이 1,000원 미만
        List<StationSaving> list = displayed(station("가까운주유소", 1700, 500), station("조금싼주유소", 1695, 800));
        LowFuelSuggestion suggestion = LowFuelSuggestion.create(20, list, false).get();
        String message = suggestion.message();
        assertFalse(message, message.contains("원 이득"));
        assertTrue(message, message.contains("가장 가까운 곳과 비슷해요") || message.contains("가장 가까운 주유소예요"));
    }

    @Test
    public void particleFollowsFinalConsonant() {
        assertEquals("이", LowFuelSuggestion.subjectParticle("역삼셀프주유소점")); // 점: 받침 있음
        assertEquals("가", LowFuelSuggestion.subjectParticle("강남주유소"));
        assertEquals("이(가)", LowFuelSuggestion.subjectParticle("GS25"));
        assertEquals("이(가)", LowFuelSuggestion.subjectParticle(""));
    }
}
