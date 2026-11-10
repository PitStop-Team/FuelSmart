package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.DiscountRule;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationSaving;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StationListBuilderTest {

    private static GasStation station(String id, int price, double distance) {
        return new GasStation(id, "SKE", id, price, distance, 0, 0, 0, 0);
    }

    private static List<String> ids(List<StationSaving> savings) {
        List<String> result = new ArrayList<>();
        for (StationSaving saving : savings) {
            result.add(saving.getStation().getId());
        }
        return result;
    }

    private static List<String> stationIds(List<GasStation> stations) {
        List<String> result = new ArrayList<>();
        for (GasStation station : stations) {
            result.add(station.getId());
        }
        return result;
    }

    private static List<StationSaving> build(List<GasStation> all, SearchRadius radius, SortOrder sortOrder) {
        return StationListBuilder.build(all, radius, sortOrder, CarInfo.DEFAULT,
                BrandSettings.EMPTY, BrandFilter.ALL);
    }

    private static final List<GasStation> ALL = Arrays.asList(
            station("a", 1700, 400),
            station("b", 1650, 2500),
            station("c", 1650, 900),
            station("d", 1600, 4200),
            station("e", 1700, 1000),   // 1km 경계: 포함
            station("f", 1600, 3000.5)  // 3km 바로 밖
    );

    @Test
    public void radius_keepsOnlyStationsWithinRadius() {
        assertEquals(3, build(ALL, SearchRadius.KM_1, SortOrder.PRICE).size());
        assertEquals(4, build(ALL, SearchRadius.KM_3, SortOrder.PRICE).size());
        assertEquals(6, build(ALL, SearchRadius.KM_5, SortOrder.PRICE).size());
    }

    @Test
    public void priceOrder_samePriceSortedByDistance() {
        List<StationSaving> result = build(ALL, SearchRadius.KM_5, SortOrder.PRICE);
        assertEquals(Arrays.asList("f", "d", "c", "b", "a", "e"), ids(result));
    }

    @Test
    public void distanceOrder_sameDistanceSortedByPrice() {
        List<GasStation> sameDistance = Arrays.asList(
                station("x", 1700, 500),
                station("y", 1600, 500),
                station("z", 1650, 300));
        List<StationSaving> result = build(sameDistance, SearchRadius.KM_1, SortOrder.DISTANCE);
        assertEquals(Arrays.asList("z", "y", "x"), ids(result));
    }

    @Test
    public void savingOrder_largestSavingFirst() {
        // 기준 a(1,700원, 0.4km). 40L 기준 이득: f 3,519원, d 3,295원, c 1,906원, b 1,598원, a 0원, e −119원
        List<StationSaving> result = build(ALL, SearchRadius.KM_5, SortOrder.SAVING);
        assertEquals(Arrays.asList("f", "d", "c", "b", "a", "e"), ids(result));
        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i - 1).getSavingWon() >= result.get(i).getSavingWon());
        }
    }

    /** 예상 총비용을 직접 정한 계산 결과 (정렬 비교만 확인할 때) */
    private static StationSaving saving(GasStation station, GasStation baseline, double totalCost) {
        return new StationSaving(station, baseline, CarInfo.DEFAULT, DiscountRule.NONE,
                0, 0, 0, 0, totalCost, 70_000, false);
    }

    @Test
    public void savingOrder_sameSavingSortedByPriceThenDistance() {
        GasStation base = station("base", 1700, 300);
        GasStation pricey = station("pricey", 1690, 1000);
        GasStation cheapFar = station("cheapFar", 1650, 2000);
        GasStation cheapNear = station("cheapNear", 1650, 1500);
        List<StationSaving> list = new ArrayList<>(Arrays.asList(
                saving(pricey, base, 68_000.2),     // 이득 1,999.8 → 2,000원
                saving(cheapFar, base, 68_000.4),   // 이득 1,999.6 → 2,000원
                saving(cheapNear, base, 67_999.9),  // 이득 2,000.1 → 2,000원
                saving(base, base, 70_000)));
        Collections.sort(list, SortOrder.SAVING.getComparator());
        // 화면에 보이는 이득이 같으면 싼 곳, 그다음 가까운 곳
        assertEquals(Arrays.asList("cheapNear", "cheapFar", "pricey", "base"), ids(list));
    }

    @Test
    public void baselineIsNearestWithinRadius() {
        // 반경을 줄이면 화면 목록 안에서 기준을 다시 정한다.
        List<GasStation> stations = Arrays.asList(station("near", 1700, 400), station("mid", 1650, 2000));
        List<StationSaving> all = build(stations, SearchRadius.KM_3, SortOrder.SAVING);
        assertEquals("near", all.get(0).getBaseline().getId());
        List<StationSaving> narrow = build(Arrays.asList(station("mid", 1650, 2000), station("far", 1600, 4500)),
                SearchRadius.KM_3, SortOrder.SAVING);
        assertEquals(1, narrow.size());
        assertTrue(narrow.get(0).isBaseline());
    }

    @Test
    public void build_doesNotChangeInput() {
        List<GasStation> input = new ArrayList<>(ALL);
        build(input, SearchRadius.KM_1, SortOrder.DISTANCE);
        assertEquals(stationIds(ALL), stationIds(input));
    }

    @Test
    public void emptyWhenNothingInRadius() {
        List<GasStation> far = Arrays.asList(station("far", 1600, 2000));
        assertEquals(0, build(far, SearchRadius.KM_1, SortOrder.PRICE).size());
    }
}
