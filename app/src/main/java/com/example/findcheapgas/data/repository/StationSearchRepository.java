package com.example.findcheapgas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.data.local.ApiCallCounter;
import com.example.findcheapgas.data.local.AppDatabase;
import com.example.findcheapgas.data.local.DailyLimitReachedException;
import com.example.findcheapgas.data.local.StationSearchCacheDao;
import com.example.findcheapgas.data.local.StationSearchCacheEntity;
import com.example.findcheapgas.data.mock.MockOpinetDataSource;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.data.remote.OpinetKeyMissingException;
import com.example.findcheapgas.data.remote.OpinetRemoteDataSource;
import com.example.findcheapgas.model.SearchError;
import com.example.findcheapgas.model.StationSearchResult;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 주유소 이름 검색(오피넷 상호 검색) 결과를 어디서 가져올지 정한다. 사용자가 검색했을 때만 부른다.
 * 1. Room 캐시 (같은 검색어로 30분 안에 검색한 결과)
 * 2. 캐시가 없으면 USE_MOCK_DATA에 따라 가짜 데이터(assets/mock/detail에서 상호로 찾기) 또는 실제 오피넷 API
 * 두 글자보다 짧은 검색어는 부르지 않는다. 캐시와 두 글자 규칙은 StationNameSearch에 있다.
 * 돌려주는 목록은 숨긴 브랜드를 빼기 전 전체 목록이다 (StationSearchResults.visible로 거른다).
 * 결과는 메인 스레드에서 Callback으로 돌려준다.
 */
public class StationSearchRepository {

    private static final String TAG = "StationSearchRepo";

    private static final Type RESULT_LIST_TYPE = new TypeToken<List<StationSearchResult>>() {
    }.getType();

    public interface Callback {
        void onSuccess(List<StationSearchResult> results);

        void onError(SearchError error);
    }

    private final StationNameSearch search;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public StationSearchRepository(Context context) {
        StationSearchCacheDao cacheDao = AppDatabase.getInstance(context).stationSearchCacheDao();
        ApiCallCounter callCounter = new ApiCallCounter(context);
        MockOpinetDataSource mockDataSource = new MockOpinetDataSource(context);
        OpinetRemoteDataSource remoteDataSource = new OpinetRemoteDataSource();

        StationNameSearch.Cache cache = new StationNameSearch.Cache() {
            @Override
            public List<StationSearchResult> find(String key, long minSavedAt) {
                StationSearchCacheEntity entity = cacheDao.find(key, minSavedAt);
                if (entity == null) {
                    return null;
                }
                try {
                    Log.d(TAG, "캐시 사용: " + key);
                    return gson.fromJson(entity.resultsJson, RESULT_LIST_TYPE);
                } catch (JsonParseException e) {
                    Log.w(TAG, "캐시를 읽지 못해 새로 검색합니다.", e);
                    return null;
                }
            }

            @Override
            public void save(String key, List<StationSearchResult> results, long savedAt) {
                cacheDao.save(new StationSearchCacheEntity(key, gson.toJson(results, RESULT_LIST_TYPE), savedAt));
            }

            @Override
            public void deleteOlderThan(long minSavedAt) {
                cacheDao.deleteOlderThan(minSavedAt);
            }
        };

        StationNameSearch.Source source = keyword -> {
            if (BuildConfig.USE_MOCK_DATA) {
                List<StationSearchResult> results = mockDataSource.searchByName(keyword);
                Log.d(TAG, "가짜 데이터 사용: " + results.size() + "곳");
                return results;
            }
            OpinetKeyMissingException.requireKey(BuildConfig.OPINET_API_KEY);
            // 오늘 300회를 이미 불렀으면 여기서 멈추고 호출하지 않는다 (캐시와 가짜 데이터는 영향 없음).
            int count = callCounter.acquire();
            Log.i(TAG, "오피넷 API 호출 (상호 검색): 오늘 " + count + "회 / " + ApiCallCounter.DAILY_LIMIT + "회");
            return remoteDataSource.searchByName(keyword);
        };

        search = new StationNameSearch(cache, source, BuildConfig.USE_MOCK_DATA ? "mock" : "api",
                System::currentTimeMillis);
    }

    /** 검색어가 두 글자 이상인지 (앞뒤 공백 제외) */
    public static boolean isSearchable(String keyword) {
        return StationNameSearch.isSearchable(keyword);
    }

    public static String normalize(String keyword) {
        return StationNameSearch.normalize(keyword);
    }

    public void search(String keyword, Callback callback) {
        executor.execute(() -> {
            try {
                List<StationSearchResult> results = search.search(keyword);
                mainHandler.post(() -> callback.onSuccess(results));
            } catch (IOException | OpinetException | DailyLimitReachedException | RuntimeException e) {
                Log.w(TAG, "주유소 이름 검색 실패: " + e.getMessage(), e);
                SearchError error = SearchErrors.from(e);
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    /** ViewModel이 사라질 때 호출한다. */
    public void shutdown() {
        executor.shutdownNow();
    }
}
