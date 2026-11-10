package com.example.findcheapgas.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.data.local.BrandPreferences;
import com.example.findcheapgas.data.local.CarInfoPreferences;
import com.example.findcheapgas.data.local.StationFilterPreferences;
import com.example.findcheapgas.data.repository.SavedPlaceRepository;
import com.example.findcheapgas.data.repository.StationRepository;
import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.util.PlaceCompareBuilder;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 위젯을 다시 그리는 작업. WorkManager가 백그라운드 스레드에서 돌리므로 Room과 네트워크를 여기서 바로 쓴다.
 * 1. 위젯마다 고른 종류(집 / 회사)를 읽고, 등록한 집·회사 위치를 Room에서 읽는다.
 * 2. 종류마다 한 번만 주변 목록을 가져온다 (WidgetStationLoader). 캐시 나이는 WidgetRefreshMode로 정하고,
 *    캐시가 없으면 가짜 데이터 또는 오피넷(하루 한도 ApiCallCounter.acquire)을 쓴다 (StationRepository).
 * 3. 지금 저장된 유종, 반경, 빠른 필터, 내 차 정보, 내 브랜드로 이득 1위를 고르고 (WidgetContentBuilder) 그린다.
 * 위치 권한은 쓰지 않는다. 집·회사의 이름, 주소, 좌표는 로그에 남기지 않는다.
 */
public class WidgetUpdateWorker extends Worker {

    static final String KEY_MODE = "mode";
    private static final String TAG = "WidgetUpdateWorker";

    // 주기 작업과 한 번 작업이 겹쳐도 차례로 돌아서, 뒤의 작업은 앞의 작업이 저장한 캐시를 쓴다.
    private static final Object LOCK = new Object();

    public WidgetUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        WidgetRefreshMode mode = WidgetRefreshMode.fromName(getInputData().getString(KEY_MODE));
        synchronized (LOCK) {
            updateAll(getApplicationContext(), mode);
        }
        return Result.success();
    }

    private static void updateAll(Context context, WidgetRefreshMode mode) {
        WidgetPreferences widgetPrefs = new WidgetPreferences(context);
        Map<Integer, SavedPlace.Kind> widgets = new LinkedHashMap<>();
        for (int id : WidgetScheduler.widgetIds(context)) {
            SavedPlace.Kind kind = widgetPrefs.getKind(id);
            if (kind != null) {
                widgets.put(id, kind); // 아직 위치를 고르지 않은 위젯(구성 화면 중)은 건너뛴다.
            }
        }
        if (widgets.isEmpty()) {
            return;
        }

        SavedPlaces places = SavedPlaceRepository.getInstance(context).loadNow();
        StationFilterPreferences filterPrefs = new StationFilterPreferences(context);
        FuelType fuelType = filterPrefs.getFuelType();
        SearchRadius radius = filterPrefs.getRadius();
        BrandFilter brandFilter = filterPrefs.getBrandFilter();
        CarInfo carInfo = new CarInfoPreferences(context).getCarInfo();
        BrandSettings brandSettings = new BrandPreferences(context).getBrandSettings();

        StationRepository repository = new StationRepository(context);
        Map<SavedPlace.Kind, PlaceCompareBuilder.Source> sources;
        try {
            sources = WidgetStationLoader.load(widgets.values(), places, fuelType, mode.maxCacheAgeMs(),
                    BuildConfig.USE_MOCK_DATA, (latitude, longitude, fuel, maxAge) -> {
                        StationRepository.Loaded loaded =
                                repository.loadNow(new StationQuery(latitude, longitude, fuel), maxAge);
                        return new WidgetStationLoader.Fetched(loaded.stations, loaded.fetchedAt);
                    });
        } finally {
            repository.shutdown();
        }

        Map<SavedPlace.Kind, WidgetContent> contents = new EnumMap<>(SavedPlace.Kind.class);
        for (SavedPlace.Kind kind : widgets.values()) {
            if (contents.containsKey(kind)) {
                continue;
            }
            WidgetContent content = WidgetContentBuilder.build(kind, places.get(kind), fuelType, sources.get(kind),
                    radius, carInfo, brandSettings, brandFilter, widgetPrefs.getLastReady(kind));
            widgetPrefs.saveContent(content);
            contents.put(kind, content);
        }

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (Map.Entry<Integer, SavedPlace.Kind> widget : widgets.entrySet()) {
            WidgetRenderer.update(context, manager, widget.getKey(), widget.getValue(),
                    contents.get(widget.getValue()));
        }
        Log.d(TAG, "위젯 " + widgets.size() + "개 갱신 (" + mode + ", 위치 " + sources.size() + "곳)");
    }
}
