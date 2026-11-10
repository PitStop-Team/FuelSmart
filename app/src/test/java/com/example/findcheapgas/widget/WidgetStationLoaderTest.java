package com.example.findcheapgas.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.data.local.DailyLimitReachedException;
import com.example.findcheapgas.data.remote.OpinetKeyMissingException;
import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.PlaceCompareCard;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.util.PlaceCompareBuilder;
import com.example.findcheapgas.util.PlaceCompareCachePolicy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 위젯 주변 조회: 같은 위치는 한 번만 조회, 등록 안 한 위치와 가짜 데이터 범위 밖은 조회하지 않음,
 * 새로고침 20분 / 주기 갱신·다시 그리기 3시간 캐시 규칙.
 * 실제 오피넷 대신 캐시를 흉내 내는 가짜 StationSource를 쓴다.
 */
public class WidgetStationLoaderTest {

    private static final long MINUTE = 60_000L;
    private static final long NOW = 100L * 60 * MINUTE;

    // 선릉역, 양재역 (강남역 주변 5km 안)
    private static final SavedPlace HOME = new SavedPlace(SavedPlace.Kind.HOME, "선릉역", null,
            37.5045, 127.0490, 1L);
    private static final SavedPlace WORK = new SavedPlace(SavedPlace.Kind.WORK, "양재역", null,
            37.4844, 127.0343, 2L);

    private static final List<GasStation> STATIONS = Collections.singletonList(
            new GasStation("s1", "SKE", "s1", 1650, 300, 0, 0, 0, 0));

    /** 저장된 캐시가 maxCacheAgeMs 안이면 캐시를, 아니면 "조회"한 것으로 센다. */
    private static final class FakeSource implements WidgetStationLoader.StationSource {
        final long cachedAt;
        final List<Long> requestedMaxAges = new ArrayList<>();
        int loads = 0;
        int fetches = 0;
        Exception failure;

        FakeSource(long cachedAt) {
            this.cachedAt = cachedAt;
        }

        @Override
        public WidgetStationLoader.Fetched load(double latitude, double longitude, FuelType fuelType,
                                                long maxCacheAgeMs) throws Exception {
            loads++;
            requestedMaxAges.add(maxCacheAgeMs);
            if (failure != null) {
                throw failure;
            }
            if (NOW - cachedAt <= maxCacheAgeMs) {
                return new WidgetStationLoader.Fetched(STATIONS, cachedAt);
            }
            fetches++;
            return new WidgetStationLoader.Fetched(STATIONS, NOW);
        }
    }

    private static Map<SavedPlace.Kind, PlaceCompareBuilder.Source> load(List<SavedPlace.Kind> kinds,
                                                                          SavedPlaces places,
                                                                          WidgetRefreshMode mode,
                                                                          FakeSource source) {
        return WidgetStationLoader.load(kinds, places, FuelType.GASOLINE, mode.maxCacheAgeMs(), false, source);
    }

    private static PlaceCompareCard card(PlaceCompareBuilder.Source source) {
        return PlaceCompareBuilder.build(Collections.singletonList(source), SearchRadius.KM_3, CarInfo.DEFAULT,
                BrandSettings.EMPTY, BrandFilter.ALL).get(0);
    }

    @Test
    public void manyWidgets_eachPlaceLoadedOnce() {
        FakeSource source = new FakeSource(0);
        List<SavedPlace.Kind> widgets = Arrays.asList(SavedPlace.Kind.HOME, SavedPlace.Kind.HOME,
                SavedPlace.Kind.WORK, SavedPlace.Kind.HOME, SavedPlace.Kind.WORK);

        Map<SavedPlace.Kind, PlaceCompareBuilder.Source> result =
                load(widgets, new SavedPlaces(HOME, WORK), WidgetRefreshMode.PERIODIC, source);

        assertEquals(2, source.loads);
        assertEquals(2, result.size());
        assertEquals(PlaceCompareCard.Slot.HOME, card(result.get(SavedPlace.Kind.HOME)).getSlot());
        assertEquals(PlaceCompareCard.Slot.WORK, card(result.get(SavedPlace.Kind.WORK)).getSlot());
    }

    @Test
    public void homeAndWorkAtSamePosition_loadedOnce() {
        FakeSource source = new FakeSource(0);
        SavedPlace workAtHome = new SavedPlace(SavedPlace.Kind.WORK, "선릉역", null,
                HOME.getLatitude(), HOME.getLongitude(), 3L);

        Map<SavedPlace.Kind, PlaceCompareBuilder.Source> result = load(
                Arrays.asList(SavedPlace.Kind.HOME, SavedPlace.Kind.WORK), new SavedPlaces(HOME, workAtHome),
                WidgetRefreshMode.PERIODIC, source);

        assertEquals(1, source.loads);
        assertEquals(PlaceCompareCard.Status.READY, card(result.get(SavedPlace.Kind.WORK)).getStatus());
    }

    @Test
    public void notRegistered_notLoaded() {
        FakeSource source = new FakeSource(0);

        Map<SavedPlace.Kind, PlaceCompareBuilder.Source> result = load(
                Collections.singletonList(SavedPlace.Kind.WORK), new SavedPlaces(HOME, null),
                WidgetRefreshMode.REFRESH, source);

        assertEquals(0, source.loads);
        assertFalse(result.containsKey(SavedPlace.Kind.WORK));
    }

    @Test
    public void mockMode_outsideMockArea_notLoaded() {
        FakeSource source = new FakeSource(0);
        SavedPlace busan = new SavedPlace(SavedPlace.Kind.HOME, "부산역", null, 35.1151, 129.0415, 1L);

        Map<SavedPlace.Kind, PlaceCompareBuilder.Source> result = WidgetStationLoader.load(
                Collections.singletonList(SavedPlace.Kind.HOME), new SavedPlaces(busan, null), FuelType.GASOLINE,
                WidgetRefreshMode.PERIODIC.maxCacheAgeMs(), true, source);

        assertEquals(0, source.loads);
        assertEquals(PlaceCompareCard.Status.OUTSIDE_MOCK_AREA, card(result.get(SavedPlace.Kind.HOME)).getStatus());
    }

    @Test
    public void mockMode_insideMockArea_loaded() {
        FakeSource source = new FakeSource(0);

        WidgetStationLoader.load(Collections.singletonList(SavedPlace.Kind.HOME), new SavedPlaces(HOME, null),
                FuelType.GASOLINE, WidgetRefreshMode.PERIODIC.maxCacheAgeMs(), true, source);

        assertEquals(1, source.loads);
    }

    @Test
    public void dailyLimit_becomesError() {
        FakeSource source = new FakeSource(0);
        source.failure = new DailyLimitReachedException(300);

        Map<SavedPlace.Kind, PlaceCompareBuilder.Source> result = load(
                Collections.singletonList(SavedPlace.Kind.HOME), new SavedPlaces(HOME, null),
                WidgetRefreshMode.REFRESH, source);

        PlaceCompareCard card = card(result.get(SavedPlace.Kind.HOME));
        assertEquals(PlaceCompareCard.Status.ERROR, card.getStatus());
        assertEquals(StationError.DAILY_LIMIT, card.getError());
    }

    @Test
    public void missingApiKey_becomesKeyError() {
        FakeSource source = new FakeSource(0);
        source.failure = new OpinetKeyMissingException();

        Map<SavedPlace.Kind, PlaceCompareBuilder.Source> result = load(
                Collections.singletonList(SavedPlace.Kind.HOME), new SavedPlaces(HOME, null),
                WidgetRefreshMode.REFRESH, source);

        PlaceCompareCard card = card(result.get(SavedPlace.Kind.HOME));
        assertEquals(PlaceCompareCard.Status.ERROR, card.getStatus());
        assertEquals(StationError.API_KEY, card.getError());
    }

    @Test
    public void refresh_usesCacheWithin20Minutes() {
        FakeSource source = new FakeSource(NOW - 19 * MINUTE);

        load(Collections.singletonList(SavedPlace.Kind.HOME), new SavedPlaces(HOME, null),
                WidgetRefreshMode.REFRESH, source);

        assertEquals(Long.valueOf(PlaceCompareCachePolicy.REFRESH_MAX_AGE_MS), source.requestedMaxAges.get(0));
        assertEquals(0, source.fetches);
    }

    @Test
    public void refresh_fetchesAfter20Minutes() {
        FakeSource source = new FakeSource(NOW - 21 * MINUTE);

        load(Collections.singletonList(SavedPlace.Kind.HOME), new SavedPlaces(HOME, null),
                WidgetRefreshMode.REFRESH, source);

        assertEquals(1, source.fetches);
    }

    @Test
    public void periodicAndRedraw_useCacheWithin3Hours() {
        for (WidgetRefreshMode mode : new WidgetRefreshMode[]{WidgetRefreshMode.PERIODIC, WidgetRefreshMode.REDRAW}) {
            FakeSource fresh = new FakeSource(NOW - 170 * MINUTE);
            load(Collections.singletonList(SavedPlace.Kind.HOME), new SavedPlaces(HOME, null), mode, fresh);
            assertEquals(mode.name(), 0, fresh.fetches);

            FakeSource old = new FakeSource(NOW - 181 * MINUTE);
            load(Collections.singletonList(SavedPlace.Kind.HOME), new SavedPlaces(HOME, null), mode, old);
            assertEquals(mode.name(), 1, old.fetches);
        }
    }

    @Test
    public void refreshMode_maxAges() {
        assertEquals(20 * MINUTE, WidgetRefreshMode.REFRESH.maxCacheAgeMs());
        assertEquals(180 * MINUTE, WidgetRefreshMode.PERIODIC.maxCacheAgeMs());
        assertEquals(180 * MINUTE, WidgetRefreshMode.REDRAW.maxCacheAgeMs());
        assertTrue(WidgetRefreshMode.fromName(null) == WidgetRefreshMode.REDRAW);
        assertTrue(WidgetRefreshMode.fromName("PERIODIC") == WidgetRefreshMode.PERIODIC);
    }
}
