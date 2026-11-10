package com.example.findcheapgas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.data.local.ApiCallCounter;
import com.example.findcheapgas.data.local.AppDatabase;
import com.example.findcheapgas.data.local.DailyLimitReachedException;
import com.example.findcheapgas.data.local.StationCacheDao;
import com.example.findcheapgas.data.local.StationCacheEntity;
import com.example.findcheapgas.data.mock.MockOpinetDataSource;
import com.example.findcheapgas.data.remote.OpinetAroundResponse;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.data.remote.OpinetKeyMissingException;
import com.example.findcheapgas.data.remote.OpinetRemoteDataSource;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.util.CoordinateConverter;
import com.example.findcheapgas.util.PlaceCompareCachePolicy;
import com.example.findcheapgas.util.StationDistances;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 주변 주유소를 어디서 가져올지 정한다.
 * 1. Room 캐시 (같은 위치·유종으로 20분 안에 조회한 결과. 집·회사 기준 비교는 탭을 열 때 3시간 안의 결과)
 * 2. 캐시가 없으면 USE_MOCK_DATA에 따라 가짜 데이터(assets) 또는 실제 오피넷 API
 * 조회 위치는 현재 위치나 검색한 장소(StationQuery의 위치)이고, 거리는 이 위치 기준이다.
 * 오피넷에는 항상 최대 반경 5km로 요청한다. 반경을 줄이거나 정렬을 바꾸는 것은 API를 다시 부르지 않고
 * StationListBuilder가 이 결과로 처리하므로, 여기서 돌려주는 목록은 정렬하지 않은 5km 안 전체 목록이다.
 * 결과는 메인 스레드에서 Callback으로 돌려준다.
 * 조회 위치에는 집·회사 위치도 들어오므로 좌표가 들어 있는 캐시 키나 조회 위치는 로그에 남기지 않는다.
 */
public class StationRepository {

    private static final String TAG = "StationRepository";

    // 지도 탭이 그대로 쓰는 캐시의 최대 나이
    private static final long CACHE_TTL_MS = 20 * 60 * 1000;
    // 캐시를 지우지 않고 남겨 두는 시간. 집·회사 기준 비교가 탭을 열 때 3시간 안의 캐시를 쓴다.
    private static final long CACHE_KEEP_MS = PlaceCompareCachePolicy.OPEN_MAX_AGE_MS;

    private static final Type STATION_LIST_TYPE = new TypeToken<List<GasStation>>() {
    }.getType();

    public interface Callback {
        void onSuccess(List<GasStation> stations);

        void onError(StationError error);
    }

    /** 집·회사 기준 비교용. 가격을 가져온 시각(캐시면 저장한 시각)도 같이 돌려준다. */
    public interface TimedCallback {
        void onSuccess(List<GasStation> stations, long fetchedAt);

        void onError(StationError error);
    }

    /** 5km 안 전체 목록과 가져온 시각(캐시면 저장한 시각) */
    public static final class Loaded {
        public final List<GasStation> stations;
        public final long fetchedAt;

        Loaded(List<GasStation> stations, long fetchedAt) {
            this.stations = stations;
            this.fetchedAt = fetchedAt;
        }
    }

    private final StationCacheDao cacheDao;
    private final ApiCallCounter callCounter;
    private final MockOpinetDataSource mockDataSource;
    private final OpinetRemoteDataSource remoteDataSource;
    private final Gson gson = new Gson();
    // DB, 파일, 네트워크 작업은 모두 이 스레드에서 차례로 처리한다.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public StationRepository(Context context) {
        cacheDao = AppDatabase.getInstance(context).stationCacheDao();
        callCounter = new ApiCallCounter(context);
        mockDataSource = new MockOpinetDataSource(context);
        remoteDataSource = new OpinetRemoteDataSource();
    }

    /** 지도 탭: 20분 안의 캐시를 쓴다. */
    public void fetchAroundStations(StationQuery query, Callback callback) {
        fetch(query, CACHE_TTL_MS, new TimedCallback() {
            @Override
            public void onSuccess(List<GasStation> stations, long fetchedAt) {
                callback.onSuccess(stations);
            }

            @Override
            public void onError(StationError error) {
                callback.onError(error);
            }
        });
    }

    /**
     * 집·회사 기준 비교: maxCacheAgeMs 안의 캐시가 있으면 쓰고 (PlaceCompareCachePolicy), 없으면 가져온다.
     * 실제 API 호출은 다른 조회와 똑같이 하루 한도(ApiCallCounter.acquire)를 거친다.
     */
    public void fetchAroundStations(StationQuery query, long maxCacheAgeMs, TimedCallback callback) {
        fetch(query, maxCacheAgeMs, callback);
    }

    private void fetch(StationQuery query, long maxCacheAgeMs, TimedCallback callback) {
        executor.execute(() -> {
            try {
                Loaded loaded = load(query, maxCacheAgeMs);
                mainHandler.post(() -> callback.onSuccess(loaded.stations, loaded.fetchedAt));
            } catch (IOException | OpinetException | DailyLimitReachedException | RuntimeException e) {
                // 하루 한도 도달, 네트워크, 오피넷 오류를 구분해서 화면에 알려준다. 조회 위치는 남기지 않는다.
                Log.w(TAG, "주유소 조회 실패: " + e.getClass().getSimpleName());
                StationError error = StationErrors.from(e);
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    /**
     * 홈 화면 위젯 작업(WorkManager)용. 이미 백그라운드 스레드에서 돌고 있으므로 이 스레드에서 바로 가져온다.
     * 캐시 규칙과 하루 한도(ApiCallCounter.acquire)는 fetchAroundStations와 같다. 메인 스레드에서 부르면 안 된다.
     */
    @WorkerThread
    public Loaded loadNow(StationQuery query, long maxCacheAgeMs)
            throws IOException, OpinetException, DailyLimitReachedException {
        try {
            return load(query, maxCacheAgeMs);
        } catch (IOException | OpinetException | DailyLimitReachedException | RuntimeException e) {
            Log.w(TAG, "주유소 조회 실패: " + e.getClass().getSimpleName());
            throw e;
        }
    }

    /** ViewModel이 사라질 때 호출한다. */
    public void shutdown() {
        executor.shutdownNow();
    }

    private Loaded load(StationQuery query, long maxCacheAgeMs)
            throws IOException, OpinetException, DailyLimitReachedException {
        long now = System.currentTimeMillis();
        long minSavedAt = now - Math.min(maxCacheAgeMs, CACHE_KEEP_MS);
        cacheDao.deleteOlderThan(now - CACHE_KEEP_MS);

        CoordinateConverter.Katec katec = CoordinateConverter.toKatec(query.getLatitude(), query.getLongitude());
        String key = cacheKey(query);

        StationCacheEntity entity = cacheDao.find(key, minSavedAt);
        List<GasStation> cached = entity != null ? readCache(entity) : null;
        if (cached != null) {
            Log.d(TAG, "캐시 사용 (" + cached.size() + "곳)");
            // 캐시는 약 100m 단위로 묶여 있으므로 거리는 이번 조회 위치 기준으로 다시 계산한다.
            return new Loaded(Collections.unmodifiableList(
                    StationDistances.fromOrigin(cached, query.getLatitude(), query.getLongitude())),
                    entity.savedAt);
        }

        List<OpinetAroundResponse.Oil> oils;
        if (BuildConfig.USE_MOCK_DATA) {
            oils = mockDataSource.fetchAround(query, katec);
            Log.d(TAG, "가짜 데이터 사용 (" + oils.size() + "곳)");
        } else {
            OpinetKeyMissingException.requireKey(BuildConfig.OPINET_API_KEY);
            // 오늘 300회를 이미 불렀으면 여기서 멈추고 호출하지 않는다 (캐시와 가짜 데이터는 영향 없음).
            int count = callCounter.acquire();
            Log.i(TAG, "오피넷 API 호출: 오늘 " + count + "회 / " + ApiCallCounter.DAILY_LIMIT + "회");
            oils = remoteDataSource.fetchAround(query, katec);
            Log.d(TAG, "오피넷 응답 (" + oils.size() + "곳)");
        }

        List<GasStation> stations = toStations(oils);
        cacheDao.save(new StationCacheEntity(key, gson.toJson(stations, STATION_LIST_TYPE), now));
        return new Loaded(Collections.unmodifiableList(stations), now);
    }

    /**
     * 위도·경도를 소수점 셋째 자리(위도 약 111m, 경도 약 88m)로 반올림해서 가까운 위치끼리 같은 캐시를 쓴다.
     * 가짜 데이터와 실제 API 결과가 섞이지 않도록 출처도 넣는다. 반경과 정렬은 앱에서 처리하므로 키에 넣지 않는다.
     * 예: around5km|mock|37.498,127.028|B027
     * 예전 키(mock|37.498,127.028|B027|3000|1)와 앞부분이 달라서 겹치지 않고, 남아 있는 예전 캐시는 3시간이 지나면 지워진다.
     * 키에 좌표가 들어 있으므로 로그에 남기지 않는다.
     */
    private static String cacheKey(StationQuery query) {
        return String.format(Locale.US, "around%dkm|%s|%.3f,%.3f|%s",
                StationQuery.FETCH_RADIUS_METERS / 1000,
                BuildConfig.USE_MOCK_DATA ? "mock" : "api",
                query.getLatitude(), query.getLongitude(),
                query.getFuelType().getCode());
    }

    private List<GasStation> readCache(StationCacheEntity entity) {
        try {
            return gson.fromJson(entity.stationsJson, STATION_LIST_TYPE);
        } catch (JsonParseException e) {
            Log.w(TAG, "캐시를 읽지 못해 새로 조회합니다.", e);
            return null;
        }
    }

    /** 오피넷 응답(KATEC)을 지도에 쓸 수 있게 WGS84 좌표를 붙여 바꾼다. */
    private static List<GasStation> toStations(List<OpinetAroundResponse.Oil> oils) {
        List<GasStation> stations = new ArrayList<>(oils.size());
        for (OpinetAroundResponse.Oil oil : oils) {
            if (oil.uniId == null || oil.price <= 0) {
                continue; // 가격이 없는 항목은 표시하지 않는다.
            }
            CoordinateConverter.Wgs84 wgs84 = CoordinateConverter.toWgs84(oil.katecX, oil.katecY);
            stations.add(new GasStation(oil.uniId, oil.brandCode, oil.name, oil.price, oil.distance,
                    oil.katecX, oil.katecY, wgs84.latitude, wgs84.longitude));
        }
        return stations;
    }
}
