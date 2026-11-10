package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.AroundStations;
import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FavoriteItem;
import com.example.findcheapgas.model.FavoritePrice;
import com.example.findcheapgas.model.FavoriteStation;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.SearchOrigin;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationSaving;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 즐겨찾기 탭 목록: 5km 안이면 지도 탭과 같은 기준으로 예상 이득을 계산하고, 5km 밖이면 계산하지 않는다. */
public class FavoriteListBuilderTest {

    // 강남역
    private static final SearchOrigin ORIGIN = SearchOrigin.place("강남역", 37.4979, 127.0276);
    private static final CarInfo CAR = CarInfo.DEFAULT;

    // 조회 위치에서 약 0.4km, 약 2.2km, 약 8.9km
    private static final GasStation NEAR = gasStation("near", 1700, 37.5015, 127.0276);
    private static final GasStation MID = gasStation("mid", 1640, 37.5179, 127.0276);
    private static final FavoriteStation FAR = favoriteAt("far", 37.5779, 127.0276);

    private static GasStation gasStation(String id, int price, double lat, double lon) {
        CoordinateConverter.Katec katec = CoordinateConverter.toKatec(lat, lon);
        GasStation station = new GasStation(id, "SKE", id, price, 0, katec.x, katec.y, lat, lon);
        CoordinateConverter.Katec origin = CoordinateConverter.toKatec(ORIGIN.getLatitude(), ORIGIN.getLongitude());
        return station.withDistance(CoordinateConverter.distanceMeters(origin, katec.x, katec.y));
    }

    private static FavoriteStation favoriteAt(String id, double lat, double lon) {
        CoordinateConverter.Katec katec = CoordinateConverter.toKatec(lat, lon);
        return new FavoriteStation(id, id, "SKE", null, katec.x, katec.y, lat, lon, 1L);
    }

    private static FavoriteStation favoriteOf(GasStation station) {
        return new FavoriteStation(station.getId(), station.getName(), station.getBrandCode(), null,
                station.getKatecX(), station.getKatecY(), station.getLatitude(), station.getLongitude(), 1L);
    }

    /** 지도 탭과 같은 방식으로 만든 주변 조회 결과 */
    private static AroundStations around(SearchRadius radius, GasStation... all) {
        List<GasStation> list = Arrays.asList(all);
        List<StationSaving> displayed = StationListBuilder.build(list, radius, SortOrder.SAVING, CAR,
                BrandSettings.EMPTY, BrandFilter.ALL);
        return new AroundStations(ORIGIN, FuelType.GASOLINE, list, displayed);
    }

    private static FavoriteItem build(FavoriteStation favorite, FavoritePrice price, AroundStations around) {
        Map<String, FavoritePrice> prices = new HashMap<>();
        if (price != null) {
            prices.put(favorite.getId(), price);
        }
        return FavoriteListBuilder.build(Collections.singletonList(favorite), prices, around, CAR,
                BrandSettings.EMPTY).get(0);
    }

    private static FavoritePrice aroundPrice(int price) {
        return FavoritePrice.available(FavoritePrice.Source.AROUND, FuelType.GASOLINE, price, 0);
    }

    @Test
    public void inMapList_reusesMapCalculation() {
        AroundStations around = around(SearchRadius.KM_5, NEAR, MID);
        FavoriteItem item = build(favoriteOf(MID), aroundPrice(1640), around);

        assertEquals(FavoriteItem.SavingStatus.CALCULATED, item.getSavingStatus());
        assertSame(around.findDisplayed("mid"), item.getSaving());
    }

    @Test
    public void within5kmButFilteredOut_sameBaselineAndFormula() {
        // 지도 탭이 1km만 보여주면 MID(약 2.2km)는 화면 목록에 없다. 기준(NEAR)은 같다.
        AroundStations narrow = around(SearchRadius.KM_1, NEAR, MID);
        FavoriteItem item = build(favoriteOf(MID), aroundPrice(1640), narrow);

        StationSaving saving = item.getSaving();
        assertNotNull(saving);
        assertEquals(FavoriteItem.SavingStatus.CALCULATED, item.getSavingStatus());
        assertEquals("near", saving.getBaseline().getId());

        // 5km 목록에 함께 넣어 계산한 값과 같다.
        StationSaving expected = around(SearchRadius.KM_5, NEAR, MID).findDisplayed("mid");
        assertEquals(expected.getTotalCost(), saving.getTotalCost(), 1e-6);
        assertEquals(expected.getSaving(), saving.getSaving(), 1e-6);
    }

    @Test
    public void beyond5km_noSaving() {
        FavoriteItem item = build(FAR, FavoritePrice.available(FavoritePrice.Source.CACHE, FuelType.GASOLINE,
                1500, 0), around(SearchRadius.KM_5, NEAR, MID));

        assertEquals(FavoriteItem.SavingStatus.TOO_FAR, item.getSavingStatus());
        assertNull(item.getSaving());
        assertTrue(item.getDistance() > 5000);
    }

    @Test
    public void notSold_noSaving() {
        FavoriteItem item = build(favoriteOf(MID),
                FavoritePrice.notSold(FavoritePrice.Source.CACHE, FuelType.GASOLINE, 0),
                around(SearchRadius.KM_5, NEAR));

        assertEquals(FavoriteItem.SavingStatus.NO_PRICE, item.getSavingStatus());
        assertNull(item.getSaving());
    }

    @Test
    public void mapFetchFailed_noBaseline() {
        AroundStations failed = new AroundStations(ORIGIN, FuelType.GASOLINE, null, Collections.emptyList());
        FavoriteItem item = build(favoriteOf(MID),
                FavoritePrice.available(FavoritePrice.Source.FETCHED, FuelType.GASOLINE, 1640, 0), failed);

        assertEquals(FavoriteItem.SavingStatus.NO_BASELINE, item.getSavingStatus());
        assertNotNull(item.getDistance());
    }

    @Test
    public void noOriginYet() {
        FavoriteItem item = build(favoriteOf(MID), null, null);

        assertEquals(FavoriteItem.SavingStatus.NO_ORIGIN, item.getSavingStatus());
        assertNull(item.getDistance());
        assertNull(item.getPrice());
    }

    @Test
    public void keepsFavoriteOrder() {
        FavoriteStation first = favoriteOf(MID);
        FavoriteStation second = favoriteOf(NEAR);
        List<FavoriteItem> items = FavoriteListBuilder.build(Arrays.asList(first, second),
                Collections.emptyMap(), around(SearchRadius.KM_5, NEAR, MID), CAR, BrandSettings.EMPTY);

        assertEquals("mid", items.get(0).getFavorite().getId());
        assertEquals("near", items.get(1).getFavorite().getId());
    }
}
