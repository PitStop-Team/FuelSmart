package com.example.findcheapgas.widget;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.example.findcheapgas.data.local.StationFilterPreferences;
import com.example.findcheapgas.data.repository.BrandSettingsRepository;
import com.example.findcheapgas.data.repository.CarInfoRepository;
import com.example.findcheapgas.data.repository.SavedPlaceRepository;

/**
 * 앱 안에서 위젯 계산에 쓰는 값이 바뀌면 위젯을 다시 그리게 한다 (WidgetRefreshMode.REDRAW).
 * 내 차 정보, 내 브랜드 설정, 집·회사 위치(LiveData)와 유종·반경·브랜드 빠른 필터(SharedPreferences)를 본다.
 * REDRAW는 3시간 안의 캐시로 다시 계산만 하므로, 캐시가 없을 때만 오피넷을 부른다.
 * 앱이 켜질 때 처음 읽은 값은 바뀐 것이 아니므로 건너뛴다. 위젯이 없으면 작업을 넣지 않는다.
 * FindCheapGasApp.onCreate에서 한 번 시작한다.
 */
public final class WidgetSettingsWatcher {

    private static WidgetSettingsWatcher instance;

    private final Context appContext;
    // SharedPreferences는 리스너를 약하게 잡아 두므로 여기서 계속 들고 있는다.
    private final SharedPreferences filterPrefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener filterListener;

    @MainThread
    public static void start(Context context) {
        if (instance == null) {
            instance = new WidgetSettingsWatcher(context.getApplicationContext());
        }
    }

    private WidgetSettingsWatcher(Context appContext) {
        this.appContext = appContext;
        watch(CarInfoRepository.getInstance(appContext).getCarInfo());
        watch(BrandSettingsRepository.getInstance(appContext).getBrandSettings());
        watch(SavedPlaceRepository.getInstance(appContext).getPlaces());

        filterPrefs = appContext.getSharedPreferences(StationFilterPreferences.PREFS_NAME, Context.MODE_PRIVATE);
        filterListener = (prefs, key) -> {
            // 정렬은 이득 1위에 영향이 없으므로 다시 그리지 않는다.
            if (!StationFilterPreferences.KEY_SORT_ORDER.equals(key)) {
                requestRedraw();
            }
        };
        filterPrefs.registerOnSharedPreferenceChangeListener(filterListener);
    }

    private <T> void watch(LiveData<T> source) {
        source.observeForever(new Observer<T>() {
            private boolean first = true;

            @Override
            public void onChanged(T value) {
                if (first) {
                    first = false; // 처음 읽은 값
                    return;
                }
                requestRedraw();
            }
        });
    }

    private void requestRedraw() {
        WidgetScheduler.requestUpdate(appContext, WidgetRefreshMode.REDRAW);
    }
}
