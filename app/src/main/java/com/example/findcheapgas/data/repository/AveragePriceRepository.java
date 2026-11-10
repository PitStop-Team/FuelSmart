package com.example.findcheapgas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.data.local.ApiCallCounter;
import com.example.findcheapgas.data.local.AveragePriceCache;
import com.example.findcheapgas.data.local.DailyLimitReachedException;
import com.example.findcheapgas.data.mock.MockOpinetDataSource;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.data.remote.OpinetKeyMissingException;
import com.example.findcheapgas.data.remote.OpinetRemoteDataSource;
import com.example.findcheapgas.model.AveragePrice;
import com.example.findcheapgas.model.AveragePrices;
import com.example.findcheapgas.model.DailyAveragePrice;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.util.AverageCachePolicy;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 평균가(전국, 시도별)와 최근 7일 전국 평균가를 어디서 가져올지 정한다. 상세 화면을 열 때만 부른다.
 * 1. SharedPreferences 캐시 (전국·시도 평균가는 3시간, 7일 추이는 오늘 받은 것)
 * 2. 캐시가 없으면 USE_MOCK_DATA에 따라 가짜 데이터(assets/mock/avg_*.json) 또는 실제 오피넷 API
 * 세 API 모두 유종(과 시도)을 빼고 불러서 4개 유종을 한 번에 받고, 어떤 유종을 보든 같은 결과를 쓴다.
 * 실제 호출은 모두 ApiCallCounter.acquire()를 거친다 (하루 300회에 도달하면 부르지 않는다).
 * 실패한 결과는 캐시에 넣지 않는다. 다음에 상세 화면을 열 때 다시 시도한다.
 *
 * 앱 전체에서 하나만 쓰고 요청을 한 스레드에서 차례로 처리한다. 상세 화면을 연달아 열어 요청이 겹쳐도
 * 뒤 요청은 앞 요청이 저장한 캐시를 읽고 API를 다시 부르지 않는다. 결과는 메인 스레드에서 돌려준다.
 */
public class AveragePriceRepository {

    private static final String TAG = "AveragePriceRepo";

    private static final String NATIONAL = "national";
    private static final String SIDO = "sido";
    private static final String RECENT = "recent";

    private static final Type AVERAGE_LIST = new TypeToken<List<AveragePrice>>() {
    }.getType();
    private static final Type DAILY_LIST = new TypeToken<List<DailyAveragePrice>>() {
    }.getType();

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(StationError error);
    }

    /** 캐시가 없을 때 실제로 가져오는 방법 (가짜 데이터 또는 API) */
    private interface Fetcher<T> {
        T fetch() throws IOException, OpinetException;
    }

    private static volatile AveragePriceRepository instance;

    private final AveragePriceCache cache;
    private final ApiCallCounter callCounter;
    private final MockOpinetDataSource mockDataSource;
    private final OpinetRemoteDataSource remoteDataSource;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static AveragePriceRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (AveragePriceRepository.class) {
                if (instance == null) {
                    instance = new AveragePriceRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private AveragePriceRepository(Context context) {
        cache = new AveragePriceCache(context);
        callCounter = new ApiCallCounter(context);
        mockDataSource = new MockOpinetDataSource(context);
        remoteDataSource = new OpinetRemoteDataSource();
    }

    /**
     * 전국과 시도별 평균가. 전국 평균을 가져오지 못하면 시도별은 부르지 않고 실패로 알린다.
     * 시도별만 실패하면 빈 시도 목록으로 성공을 알린다 (화면은 지역 평균 줄만 뺀다).
     */
    public void fetchAverages(Callback<AveragePrices> callback) {
        executor.execute(() -> {
            List<AveragePrice> national;
            try {
                national = loadAverage(NATIONAL, this::fetchNational);
            } catch (IOException | OpinetException | DailyLimitReachedException | RuntimeException e) {
                Log.w(TAG, "전국 평균가 조회 실패: " + e.getMessage(), e);
                StationError error = StationErrors.from(e);
                mainHandler.post(() -> callback.onError(error));
                return;
            }
            List<AveragePrice> sido;
            try {
                sido = loadAverage(SIDO, this::fetchSido);
            } catch (IOException | OpinetException | DailyLimitReachedException | RuntimeException e) {
                Log.w(TAG, "시도별 평균가 조회 실패, 전국 평균만 보여줍니다: " + e.getMessage(), e);
                sido = Collections.emptyList();
            }
            AveragePrices result = new AveragePrices(national, sido);
            mainHandler.post(() -> callback.onSuccess(result));
        });
    }

    /** 최근 7일 전국 일일 평균가 (4개 유종). 하루에 한 번만 가져온다. */
    public void fetchRecentAverages(Callback<List<DailyAveragePrice>> callback) {
        executor.execute(() -> {
            try {
                List<DailyAveragePrice> result = loadRecent();
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (IOException | OpinetException | DailyLimitReachedException | RuntimeException e) {
                Log.w(TAG, "7일 평균가 조회 실패: " + e.getMessage(), e);
                StationError error = StationErrors.from(e);
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    private List<AveragePrice> loadAverage(String name, Fetcher<List<AveragePrice>> fetcher)
            throws IOException, OpinetException, DailyLimitReachedException {
        long now = System.currentTimeMillis();
        String key = cacheKey(name);
        AveragePriceCache.Entry entry = cache.read(key);
        if (entry != null && AverageCachePolicy.isAverageFresh(entry.savedAt, now)) {
            List<AveragePrice> cached = parse(entry.json, AVERAGE_LIST);
            if (cached != null) {
                Log.d(TAG, "캐시 사용: " + key);
                return cached;
            }
        }
        List<AveragePrice> fetched = fetch(name, fetcher);
        cache.save(key, gson.toJson(fetched, AVERAGE_LIST), now, AverageCachePolicy.dayOf(now, TimeZone.getDefault()));
        return fetched;
    }

    private List<DailyAveragePrice> loadRecent() throws IOException, OpinetException, DailyLimitReachedException {
        long now = System.currentTimeMillis();
        TimeZone timeZone = TimeZone.getDefault();
        String key = cacheKey(RECENT);
        AveragePriceCache.Entry entry = cache.read(key);
        if (entry != null && AverageCachePolicy.isTrendFresh(entry.day, entry.savedAt, now, timeZone)) {
            List<DailyAveragePrice> cached = parse(entry.json, DAILY_LIST);
            if (cached != null) {
                Log.d(TAG, "캐시 사용: " + key);
                return cached;
            }
        }
        List<DailyAveragePrice> fetched = fetch(RECENT, this::fetchRecent);
        cache.save(key, gson.toJson(fetched, DAILY_LIST), now, AverageCachePolicy.dayOf(now, timeZone));
        return fetched;
    }

    /** 가짜 데이터 또는 실제 API. 실제 API는 키를 확인하고 오늘 호출 횟수를 하나 쓴다. */
    private <T> T fetch(String name, Fetcher<T> fetcher)
            throws IOException, OpinetException, DailyLimitReachedException {
        if (BuildConfig.USE_MOCK_DATA) {
            Log.d(TAG, "가짜 데이터 사용: " + name);
            return fetcher.fetch();
        }
        OpinetKeyMissingException.requireKey(BuildConfig.OPINET_API_KEY);
        // 오늘 300회를 이미 불렀으면 여기서 멈추고 호출하지 않는다 (캐시와 가짜 데이터는 영향 없음).
        int count = callCounter.acquire();
        Log.i(TAG, "오피넷 API 호출 (평균가 " + name + "): 오늘 " + count + "회 / " + ApiCallCounter.DAILY_LIMIT + "회");
        return fetcher.fetch();
    }

    private List<AveragePrice> fetchNational() throws IOException, OpinetException {
        return BuildConfig.USE_MOCK_DATA
                ? mockDataSource.fetchNationalAverages() : remoteDataSource.fetchNationalAverages();
    }

    private List<AveragePrice> fetchSido() throws IOException, OpinetException {
        return BuildConfig.USE_MOCK_DATA
                ? mockDataSource.fetchSidoAverages() : remoteDataSource.fetchSidoAverages();
    }

    private List<DailyAveragePrice> fetchRecent() throws IOException, OpinetException {
        return BuildConfig.USE_MOCK_DATA
                ? mockDataSource.fetchRecentAverages() : remoteDataSource.fetchRecentAverages();
    }

    /** 가짜 데이터와 실제 API 결과가 섞이지 않도록 출처를 넣는다. 예: api|national */
    private static String cacheKey(String name) {
        return (BuildConfig.USE_MOCK_DATA ? "mock" : "api") + "|" + name;
    }

    @Nullable
    private <T> T parse(String json, Type type) {
        try {
            return gson.fromJson(json, type);
        } catch (JsonParseException e) {
            Log.w(TAG, "캐시를 읽지 못해 새로 조회합니다.", e);
            return null;
        }
    }
}
