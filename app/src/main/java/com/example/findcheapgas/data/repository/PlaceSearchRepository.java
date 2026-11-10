package com.example.findcheapgas.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.findcheapgas.data.remote.kakao.KakaoException;
import com.example.findcheapgas.data.remote.kakao.KakaoLocalDataSource;
import com.example.findcheapgas.model.Place;
import com.example.findcheapgas.model.SearchError;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 장소 검색 (카카오 로컬 API: 키워드 → 결과가 없으면 주소). 사용자가 검색했을 때만 부른다.
 * 가짜 데이터 모드에서도 실제 카카오 API를 쓴다. 오피넷 호출이 아니므로 ApiCallCounter와 관계없다.
 * 결과는 메인 스레드에서 Callback으로 돌려준다.
 */
public class PlaceSearchRepository {

    private static final String TAG = "PlaceSearchRepo";

    public interface Callback {
        void onSuccess(List<Place> places);

        void onError(SearchError error);
    }

    private final KakaoLocalDataSource dataSource = new KakaoLocalDataSource();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void search(String query, Callback callback) {
        executor.execute(() -> {
            try {
                List<Place> places = dataSource.search(query);
                mainHandler.post(() -> callback.onSuccess(places));
            } catch (IOException | KakaoException | RuntimeException e) {
                // 메시지에는 HTTP 코드만 들어 있고 키나 헤더는 없다.
                Log.w(TAG, "장소 검색 실패: " + e.getMessage());
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
