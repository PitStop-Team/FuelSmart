package com.example.findcheapgas.util;

import androidx.annotation.Nullable;

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
import com.example.findcheapgas.model.StationDetail;

import java.io.IOException;

/**
 * 즐겨찾기 주유소의 가격을 어디서 가져올지 정한다. 오피넷 호출을 줄이려고 아래 순서로 확인한다.
 * 1. 지도 탭이 이미 받아 둔 주변 목록(조회 위치 5km, 같은 유종)에 있으면 그 가격 (호출 없음)
 * 2. 상세정보 캐시. 탭을 열 때는 오래된 캐시(STALE_CACHE_MS 이내)도 쓰고,
 *    당겨서 새로고침할 때는 30분 안의 캐시만 쓴다 (호출 없음)
 * 3. 그래도 없으면 상세정보를 새로 가져온다 (DetailSource.fetch: 가짜 데이터 또는 실제 API, 하루 한도 확인)
 * 안드로이드에 의존하지 않아서 단위 테스트로 확인한다.
 */
public final class FavoritePriceResolver {

    /** 상세정보 캐시를 "최신"으로 보는 시간. 상세 화면과 같다. */
    public static final long FRESH_CACHE_MS = 30 * 60 * 1000L;
    /** 탭을 열 때 다시 가져오지 않고 그대로 쓰는 오래된 캐시의 최대 나이 */
    public static final long STALE_CACHE_MS = 24 * 60 * 60 * 1000L;

    /** 상세정보 캐시와 조회. StationDetailRepository가 구현한다. */
    public interface DetailSource {

        /** maxAgeMs 안에 저장된 캐시. 없으면 null */
        @Nullable
        CachedDetail findCached(String stationId, long maxAgeMs);

        /** 캐시를 보지 않고 가져와서 캐시에 저장한다. */
        CachedDetail fetch(String stationId) throws IOException, OpinetException, DailyLimitReachedException;
    }

    /** 상세정보와 그것을 가져온 시각 */
    public static final class CachedDetail {
        final StationDetail detail;
        final long savedAt;

        public CachedDetail(StationDetail detail, long savedAt) {
            this.detail = detail;
            this.savedAt = savedAt;
        }

        public StationDetail getDetail() {
            return detail;
        }
    }

    private FavoritePriceResolver() {
    }

    /**
     * @param around  지도 탭이 받아 둔 주변 목록. 없거나 유종이 다르면 null을 넘긴다.
     * @param refresh 당겨서 새로고침이면 true (30분이 지난 캐시는 다시 가져옴)
     * @param onDetail 상세정보를 쓸 때마다 불린다 (즐겨찾기 주소 채우기). 없으면 null
     */
    public static FavoritePrice resolve(FavoriteStation favorite, FuelType fuelType, @Nullable AroundStations around,
                                        boolean refresh, DetailSource source,
                                        @Nullable DetailListener onDetail) {
        if (around != null && around.getFuelType() == fuelType) {
            GasStation station = around.findInAll(favorite.getId());
            if (station != null) {
                return FavoritePrice.available(FavoritePrice.Source.AROUND, fuelType, station.getPrice(), 0);
            }
        }

        CachedDetail cached = source.findCached(favorite.getId(), refresh ? FRESH_CACHE_MS : STALE_CACHE_MS);
        if (cached != null) {
            notify(onDetail, favorite, cached);
            return fromDetail(cached, FavoritePrice.Source.CACHE, fuelType);
        }

        try {
            CachedDetail fetched = source.fetch(favorite.getId());
            notify(onDetail, favorite, fetched);
            return fromDetail(fetched, FavoritePrice.Source.FETCHED, fuelType);
        } catch (StationNotFoundException e) {
            return FavoritePrice.failed(FavoritePrice.Status.NOT_FOUND, fuelType);
        } catch (DailyLimitReachedException e) {
            return FavoritePrice.failed(FavoritePrice.Status.DAILY_LIMIT, fuelType);
        } catch (IOException e) {
            return FavoritePrice.failed(FavoritePrice.Status.NETWORK, fuelType);
        } catch (OpinetKeyMissingException e) {
            return FavoritePrice.failed(FavoritePrice.Status.API_KEY, fuelType);
        } catch (OpinetException | RuntimeException e) {
            return FavoritePrice.failed(FavoritePrice.Status.OPINET, fuelType);
        }
    }

    /** 상세정보를 받았을 때 알림 */
    public interface DetailListener {
        void onDetail(FavoriteStation favorite, StationDetail detail);
    }

    /** 상세정보의 유종별 가격에서 고른 유종을 찾는다. 없으면 그 유종을 팔지 않는 것이다. */
    static FavoritePrice fromDetail(CachedDetail cached, FavoritePrice.Source source, FuelType fuelType) {
        for (FuelPrice price : cached.detail.getPrices()) {
            if (price.getFuelType() == fuelType && price.getPrice() > 0) {
                return FavoritePrice.available(source, fuelType, price.getPrice(), cached.savedAt);
            }
        }
        return FavoritePrice.notSold(source, fuelType, cached.savedAt);
    }

    private static void notify(@Nullable DetailListener listener, FavoriteStation favorite, CachedDetail cached) {
        if (listener != null) {
            listener.onDetail(favorite, cached.detail);
        }
    }
}
