package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.data.local.DailyLimitReachedException;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.data.remote.OpinetKeyMissingException;
import com.example.findcheapgas.data.remote.StationNotFoundException;
import com.example.findcheapgas.model.AroundStations;
import com.example.findcheapgas.model.FavoritePrice;
import com.example.findcheapgas.model.FavoriteStation;
import com.example.findcheapgas.model.FuelPrice;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.SearchOrigin;
import com.example.findcheapgas.model.StationDetail;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 즐겨찾기 가격 출처 선택: 주변 목록 → 상세 캐시 → 조회. 실제 오피넷은 부르지 않고 가짜 DetailSource로 확인한다.
 */
public class FavoritePriceResolverTest {

    private static final FavoriteStation FAVORITE =
            new FavoriteStation("A1", "역삼주유소", "SKE", null, 0, 0, 37.5, 127.03, 1L);
    private static final SearchOrigin ORIGIN = SearchOrigin.place("강남역", 37.4979, 127.0276);

    /** 호출 기록을 남기는 가짜 상세정보 출처 */
    private static class FakeSource implements FavoritePriceResolver.DetailSource {
        FavoritePriceResolver.CachedDetail cached;
        FavoritePriceResolver.CachedDetail fetched;
        Exception fetchError;
        final List<Long> cacheMaxAges = new ArrayList<>();
        int fetchCount = 0;

        @Override
        public FavoritePriceResolver.CachedDetail findCached(String stationId, long maxAgeMs) {
            cacheMaxAges.add(maxAgeMs);
            return cached;
        }

        @Override
        public FavoritePriceResolver.CachedDetail fetch(String stationId)
                throws IOException, OpinetException, DailyLimitReachedException {
            fetchCount++;
            if (fetchError instanceof IOException) {
                throw (IOException) fetchError;
            }
            if (fetchError instanceof OpinetException) {
                throw (OpinetException) fetchError;
            }
            if (fetchError instanceof DailyLimitReachedException) {
                throw (DailyLimitReachedException) fetchError;
            }
            return fetched;
        }
    }

    private static FavoritePriceResolver.CachedDetail detail(long savedAt, FuelPrice... prices) {
        StationDetail detail = new StationDetail("A1", "역삼주유소", "SKE", "서울 강남구 1", "", "", false, false,
                false, false, null, 0, 0, Arrays.asList(prices));
        return new FavoritePriceResolver.CachedDetail(detail, savedAt);
    }

    private static FuelPrice price(FuelType type, int won) {
        return new FuelPrice(type, won, "", "");
    }

    private static AroundStations around(FuelType fuelType, GasStation... stations) {
        return new AroundStations(ORIGIN, fuelType, Arrays.asList(stations), Collections.emptyList());
    }

    private static GasStation gasStation(String id, int price) {
        return new GasStation(id, "SKE", id, price, 500, 0, 0, 37.5, 127.03);
    }

    @Test
    public void aroundList_usedWithoutTouchingDetail() {
        FakeSource source = new FakeSource();
        FavoritePrice result = FavoritePriceResolver.resolve(FAVORITE, FuelType.GASOLINE,
                around(FuelType.GASOLINE, gasStation("A1", 1650)), false, source, null);

        assertEquals(FavoritePrice.Source.AROUND, result.getSource());
        assertEquals(1650, result.getPrice());
        assertTrue(source.cacheMaxAges.isEmpty());
        assertEquals(0, source.fetchCount);
    }

    @Test
    public void aroundListOfOtherFuel_ignored() {
        FakeSource source = new FakeSource();
        source.fetched = detail(10, price(FuelType.GASOLINE, 1700));
        FavoritePrice result = FavoritePriceResolver.resolve(FAVORITE, FuelType.GASOLINE,
                around(FuelType.DIESEL, gasStation("A1", 1550)), false, source, null);

        assertEquals(FavoritePrice.Source.FETCHED, result.getSource());
        assertEquals(1700, result.getPrice());
    }

    @Test
    public void notInAround_usesCacheWithoutFetch() {
        FakeSource source = new FakeSource();
        source.cached = detail(5, price(FuelType.GASOLINE, 1690));
        FavoritePrice result = FavoritePriceResolver.resolve(FAVORITE, FuelType.GASOLINE,
                around(FuelType.GASOLINE, gasStation("OTHER", 1600)), false, source, null);

        assertEquals(FavoritePrice.Source.CACHE, result.getSource());
        assertEquals(1690, result.getPrice());
        assertEquals(5, result.getFetchedAt());
        assertEquals(0, source.fetchCount);
    }

    @Test
    public void openingTab_acceptsStaleCache_refreshOnlyFresh() {
        FakeSource source = new FakeSource();
        source.cached = detail(5, price(FuelType.GASOLINE, 1690));

        FavoritePriceResolver.resolve(FAVORITE, FuelType.GASOLINE, null, false, source, null);
        FavoritePriceResolver.resolve(FAVORITE, FuelType.GASOLINE, null, true, source, null);

        assertEquals(Arrays.asList(FavoritePriceResolver.STALE_CACHE_MS, FavoritePriceResolver.FRESH_CACHE_MS),
                source.cacheMaxAges);
    }

    @Test
    public void noCache_fetchesOnce() {
        FakeSource source = new FakeSource();
        source.fetched = detail(10, price(FuelType.GASOLINE, 1700), price(FuelType.DIESEL, 1600));
        List<String> addresses = new ArrayList<>();
        FavoritePrice result = FavoritePriceResolver.resolve(FAVORITE, FuelType.DIESEL, null, true, source,
                (favorite, d) -> addresses.add(d.getAddress()));

        assertEquals(FavoritePrice.Source.FETCHED, result.getSource());
        assertEquals(1600, result.getPrice());
        assertEquals(1, source.fetchCount);
        assertEquals(Collections.singletonList("서울 강남구 1"), addresses);
    }

    @Test
    public void fuelNotSold() {
        FakeSource source = new FakeSource();
        source.cached = detail(5, price(FuelType.GASOLINE, 1690));
        FavoritePrice result = FavoritePriceResolver.resolve(FAVORITE, FuelType.LPG, null, false, source, null);

        assertEquals(FavoritePrice.Status.NOT_SOLD, result.getStatus());
        assertEquals(FuelType.LPG, result.getFuelType());
        assertEquals(0, source.fetchCount);
    }

    @Test
    public void stationNotFound() {
        FakeSource source = new FakeSource();
        source.fetchError = new StationNotFoundException("없음");
        FavoritePrice result = FavoritePriceResolver.resolve(FAVORITE, FuelType.GASOLINE, null, false, source, null);

        assertEquals(FavoritePrice.Status.NOT_FOUND, result.getStatus());
        assertNull(result.getSource());
    }

    @Test
    public void dailyLimit_networkAndOpinetErrors() {
        FakeSource source = new FakeSource();
        source.fetchError = new DailyLimitReachedException(300);
        assertEquals(FavoritePrice.Status.DAILY_LIMIT,
                FavoritePriceResolver.resolve(FAVORITE, FuelType.GASOLINE, null, false, source, null).getStatus());

        source.fetchError = new IOException("timeout");
        assertEquals(FavoritePrice.Status.NETWORK,
                FavoritePriceResolver.resolve(FAVORITE, FuelType.GASOLINE, null, false, source, null).getStatus());

        source.fetchError = new OpinetException("HTTP 500");
        assertEquals(FavoritePrice.Status.OPINET,
                FavoritePriceResolver.resolve(FAVORITE, FuelType.GASOLINE, null, false, source, null).getStatus());

        source.fetchError = new OpinetKeyMissingException();
        assertEquals(FavoritePrice.Status.API_KEY,
                FavoritePriceResolver.resolve(FAVORITE, FuelType.GASOLINE, null, false, source, null).getStatus());
    }
}
