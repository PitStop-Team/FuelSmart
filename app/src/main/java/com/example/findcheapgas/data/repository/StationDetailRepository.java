package com.example.findcheapgas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.data.local.ApiCallCounter;
import com.example.findcheapgas.data.local.AppDatabase;
import com.example.findcheapgas.data.local.DailyLimitReachedException;
import com.example.findcheapgas.data.local.StationDetailCacheDao;
import com.example.findcheapgas.data.local.StationDetailCacheEntity;
import com.example.findcheapgas.data.mock.MockOpinetDataSource;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.data.remote.OpinetKeyMissingException;
import com.example.findcheapgas.data.remote.OpinetRemoteDataSource;
import com.example.findcheapgas.model.AroundStations;
import com.example.findcheapgas.model.FavoritePrice;
import com.example.findcheapgas.model.FavoriteStation;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.StationDetail;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.util.FavoritePriceResolver;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 주유소 상세정보를 어디서 가져올지 정한다. 상세 화면을 열 때만 부른다.
 * 1. Room 캐시 (같은 주유소를 30분 안에 조회한 결과)
 * 2. 캐시가 없으면 USE_MOCK_DATA에 따라 가짜 데이터(assets/mock/detail) 또는 실제 오피넷 API
 * 즐겨찾기 탭의 가격도 여기서 가져온다 (fetchFavoritePrices). 즐겨찾기 탭은 탭을 열 때 30분이 지난 캐시도 쓰므로
 * 캐시는 하루가 지나야 지운다. 상세 화면은 지금처럼 30분 안의 캐시만 쓴다.
 *
 * 앱 전체에서 하나만 쓰고 요청을 한 스레드에서 차례로 처리한다. 그래서 상세 화면을 닫았다가 바로 다시 열어
 * 같은 주유소 요청이 겹쳐도, 뒤 요청은 앞 요청이 저장한 캐시를 읽고 API를 다시 부르지 않는다.
 * 결과는 메인 스레드에서 Callback으로 돌려준다.
 */
public class StationDetailRepository {

    private static final String TAG = "StationDetailRepo";

    // 상세 화면은 30분 안의 캐시만 쓴다.
    private static final long CACHE_TTL_MS = FavoritePriceResolver.FRESH_CACHE_MS;
    // 즐겨찾기 탭은 탭을 열 때 오래된 캐시도 쓰므로, 캐시는 이 시간이 지나야 지운다.
    private static final long CACHE_KEEP_MS = FavoritePriceResolver.STALE_CACHE_MS;

    public interface Callback {
        void onSuccess(StationDetail detail);

        void onError(StationError error);
    }

    /** 즐겨찾기 가격 조회 결과. 주유소 ID → 가격 */
    public interface PricesCallback {
        void onResult(Map<String, FavoritePrice> prices);
    }

    /** 즐겨찾기 가격을 가져오다 상세정보를 받았을 때 (메인 스레드가 아닌 곳에서 불림) */
    public interface DetailReceivedListener {
        void onDetail(FavoriteStation favorite, StationDetail detail);
    }

    private static volatile StationDetailRepository instance;

    private final StationDetailCacheDao cacheDao;
    private final ApiCallCounter callCounter;
    private final MockOpinetDataSource mockDataSource;
    private final OpinetRemoteDataSource remoteDataSource;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static StationDetailRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (StationDetailRepository.class) {
                if (instance == null) {
                    instance = new StationDetailRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private StationDetailRepository(Context context) {
        cacheDao = AppDatabase.getInstance(context).stationDetailCacheDao();
        callCounter = new ApiCallCounter(context);
        mockDataSource = new MockOpinetDataSource(context);
        remoteDataSource = new OpinetRemoteDataSource();
    }

    public void fetchDetail(String stationId, Callback callback) {
        executor.execute(() -> {
            try {
                StationDetail detail = load(stationId);
                mainHandler.post(() -> callback.onSuccess(detail));
            } catch (IOException | OpinetException | DailyLimitReachedException | RuntimeException e) {
                // 하루 한도 도달, 네트워크, 오피넷 오류를 구분해서 화면에 알려준다.
                Log.w(TAG, "상세 조회 실패: " + e.getMessage(), e);
                StationError error = StationErrors.from(e);
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    /**
     * 즐겨찾기 주유소들의 지금 고른 유종 가격을 가져온다 (FavoritePriceResolver의 순서: 주변 목록 → 캐시 → 조회).
     * 상세 화면과 같은 스레드에서 차례로 처리해서, 같은 주유소를 동시에 두 번 부르지 않는다.
     *
     * @param around  지도 탭이 받아 둔 주변 목록. 없으면 null
     * @param refresh 당겨서 새로고침이면 true
     */
    public void fetchFavoritePrices(List<FavoriteStation> favorites, FuelType fuelType,
                                    @Nullable AroundStations around, boolean refresh,
                                    @Nullable DetailReceivedListener detailListener, PricesCallback callback) {
        List<FavoriteStation> targets = new ArrayList<>(favorites);
        executor.execute(() -> {
            Map<String, FavoritePrice> prices = new HashMap<>();
            for (FavoriteStation favorite : targets) {
                prices.put(favorite.getId(), FavoritePriceResolver.resolve(favorite, fuelType, around, refresh,
                        detailSource, detailListener != null ? detailListener::onDetail : null));
            }
            mainHandler.post(() -> callback.onResult(prices));
        });
    }

    // 즐겨찾기 가격 조회가 쓰는 캐시와 조회. executor 스레드에서만 부른다.
    private final FavoritePriceResolver.DetailSource detailSource = new FavoritePriceResolver.DetailSource() {
        @Nullable
        @Override
        public FavoritePriceResolver.CachedDetail findCached(String stationId, long maxAgeMs) {
            long now = System.currentTimeMillis();
            cacheDao.deleteOlderThan(now - CACHE_KEEP_MS);
            String key = cacheKey(stationId);
            StationDetailCacheEntity entity = cacheDao.find(key, now - maxAgeMs);
            StationDetail detail = entity != null ? parseCache(entity) : null;
            if (detail == null) {
                return null;
            }
            Log.d(TAG, "즐겨찾기 캐시 사용: " + key);
            return new FavoritePriceResolver.CachedDetail(detail, entity.savedAt);
        }

        @Override
        public FavoritePriceResolver.CachedDetail fetch(String stationId)
                throws IOException, OpinetException, DailyLimitReachedException {
            long now = System.currentTimeMillis();
            return new FavoritePriceResolver.CachedDetail(fetchAndCache(stationId, now), now);
        }
    };

    private StationDetail load(String stationId) throws IOException, OpinetException, DailyLimitReachedException {
        long now = System.currentTimeMillis();
        long minSavedAt = now - CACHE_TTL_MS;
        cacheDao.deleteOlderThan(now - CACHE_KEEP_MS);

        String key = cacheKey(stationId);
        StationDetail cached = readCache(key, minSavedAt);
        if (cached != null) {
            Log.d(TAG, "캐시 사용: " + key);
            return cached;
        }
        return fetchAndCache(stationId, now);
    }

    /** 캐시를 보지 않고 가짜 데이터 또는 실제 API에서 가져와 캐시에 저장한다. */
    private StationDetail fetchAndCache(String stationId, long now)
            throws IOException, OpinetException, DailyLimitReachedException {
        String key = cacheKey(stationId);
        StationDetail detail;
        if (BuildConfig.USE_MOCK_DATA) {
            detail = mockDataSource.fetchDetail(stationId);
            Log.d(TAG, "가짜 데이터 사용: " + key);
        } else {
            OpinetKeyMissingException.requireKey(BuildConfig.OPINET_API_KEY);
            // 오늘 300회를 이미 불렀으면 여기서 멈추고 호출하지 않는다 (캐시와 가짜 데이터는 영향 없음).
            int count = callCounter.acquire();
            Log.i(TAG, "오피넷 API 호출 (상세): 오늘 " + count + "회 / " + ApiCallCounter.DAILY_LIMIT + "회");
            detail = remoteDataSource.fetchDetail(stationId);
        }

        cacheDao.save(new StationDetailCacheEntity(key, gson.toJson(detail), now));
        return detail;
    }

    /**
     * 가짜 데이터와 실제 API 결과가 섞이지 않도록 출처를 넣는다.
     * 예: detail|mock|A0900001
     */
    private static String cacheKey(String stationId) {
        return "detail|" + (BuildConfig.USE_MOCK_DATA ? "mock" : "api") + "|" + stationId;
    }

    private StationDetail readCache(String key, long minSavedAt) {
        StationDetailCacheEntity entity = cacheDao.find(key, minSavedAt);
        return entity != null ? parseCache(entity) : null;
    }

    @Nullable
    private StationDetail parseCache(StationDetailCacheEntity entity) {
        try {
            return gson.fromJson(entity.detailJson, StationDetail.class);
        } catch (JsonParseException e) {
            Log.w(TAG, "캐시를 읽지 못해 새로 조회합니다.", e);
            return null;
        }
    }
}
