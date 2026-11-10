package com.example.findcheapgas;

import android.app.Application;
import android.util.Log;

import com.example.findcheapgas.data.local.ThemePreferences;
import com.example.findcheapgas.util.KeyHashUtil;
import com.kakao.vectormap.KakaoMapSdk;

public class FindCheapGasApp extends Application {

    private static final String TAG = "FindCheapGasApp";

    // 카카오맵 SDK 초기화 성공 여부. 지도 화면은 이 값을 보고 지도를 띄울지 정한다.
    private static boolean mapAvailable = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // 설정에서 고른 화면 테마(시스템 / 밝게 / 어둡게)를 첫 화면이 만들어지기 전에 적용한다.
        // 화면을 그린 뒤에 적용하면 밝은 화면이 잠깐 보였다가 바뀐다.
        new ThemePreferences(this).applySaved();

        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isEmpty()) {
            Log.w(TAG, "local.properties에 KAKAO_NATIVE_APP_KEY가 없어 지도가 표시되지 않습니다.");
        }
        // 지도를 띄우기 전에 한 번만 초기화하면 된다.
        // 네이티브 라이브러리(.so)가 없는 기기 등에서는 Error가 던져지므로 Throwable로 잡는다.
        try {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY);
            mapAvailable = true;
        } catch (Throwable t) {
            mapAvailable = false;
            Log.e(TAG, "카카오맵 SDK 초기화 실패. 지도를 사용할 수 없습니다.", t);
        }

        // 카카오 디벨로퍼스에 등록할 키 해시. 디버그 빌드에서만 출력한다.
        if (BuildConfig.DEBUG) {
            KeyHashUtil.logKeyHash(this);
        }
    }

    /** local.properties에 카카오 네이티브 앱 키가 있는지. 없으면 지도를 띄우지 않고 지도 자리에 이유를 보여준다. */
    public static boolean hasMapKey() {
        return !BuildConfig.KAKAO_NATIVE_APP_KEY.trim().isEmpty();
    }

    public static boolean isMapAvailable() {
        return mapAvailable;
    }
}
