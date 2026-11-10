package com.example.findcheapgas.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.findcheapgas.data.remote.kakao.KakaoException;
import com.example.findcheapgas.data.remote.kakao.KakaoLocalDataSource;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 좌표의 동 이름 (카카오 로컬 API 좌표 → 행정구역). "이 지역에서 다시 검색"을 눌렀을 때 조회 위치 이름("역삼1동 기준")에 쓴다.
 * 이름은 보여주기용이라 실패해도(키 오류, 네트워크 등) 오류를 알리지 않고 null을 돌려준다. 주변 조회는 이름과 상관없이 따로 한다.
 * 가짜 데이터 모드에서도 실제 카카오 API를 쓴다. 오피넷 호출이 아니므로 ApiCallCounter와 관계없다.
 * 결과는 메인 스레드에서 Callback으로 돌려준다.
 */
public class RegionNameRepository {

    private static final String TAG = "RegionNameRepo";

    public interface Callback {
        /** @param regionName 동 이름. 얻지 못했으면 null */
        void onResult(@Nullable String regionName);
    }

    private final KakaoLocalDataSource dataSource = new KakaoLocalDataSource();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void find(double latitude, double longitude, Callback callback) {
        executor.execute(() -> {
            String name = null;
            try {
                name = dataSource.regionName(latitude, longitude);
            } catch (IOException | KakaoException | RuntimeException e) {
                // 메시지에는 HTTP 코드만 들어 있고 키나 헤더는 없다.
                Log.w(TAG, "동 이름 조회 실패: " + e.getMessage());
            }
            String result = name;
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /** ViewModel이 사라질 때 호출한다. */
    public void shutdown() {
        executor.shutdownNow();
    }
}
