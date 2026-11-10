package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.data.local.ApiCallCounter;
import com.example.findcheapgas.data.local.ThemePreferences;
import com.example.findcheapgas.data.repository.BrandSettingsRepository;
import com.example.findcheapgas.data.repository.CarInfoRepository;
import com.example.findcheapgas.data.repository.SavedPlaceRepository;
import com.example.findcheapgas.model.ApiUsage;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.SavedPlaces;
import com.example.findcheapgas.model.ThemeMode;

/**
 * 설정 탭의 상태. 내 차 정보, 내 브랜드 요약, 집·회사 위치, 화면 테마, 오늘 오피넷 사용량.
 * 기본 유종·반경은 지도 탭과 같은 값이어야 하므로 여기 두지 않고 Activity 범위의 MapViewModel을 쓴다.
 */
public class SettingsViewModel extends AndroidViewModel {

    private final CarInfoRepository carInfoRepository;
    private final BrandSettingsRepository brandSettingsRepository;
    private final SavedPlaceRepository savedPlaceRepository;
    private final ThemePreferences themePreferences;
    private final ApiCallCounter apiCallCounter;

    private final MutableLiveData<ThemeMode> themeMode = new MutableLiveData<>();
    private final MutableLiveData<ApiUsage> apiUsage = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        carInfoRepository = CarInfoRepository.getInstance(application);
        brandSettingsRepository = BrandSettingsRepository.getInstance(application);
        savedPlaceRepository = SavedPlaceRepository.getInstance(application);
        themePreferences = new ThemePreferences(application);
        apiCallCounter = new ApiCallCounter(application);
        themeMode.setValue(themePreferences.getThemeMode());
        refreshApiUsage();
    }

    public LiveData<CarInfo> getCarInfo() {
        return carInfoRepository.getCarInfo();
    }

    public LiveData<BrandSettings> getBrandSettings() {
        return brandSettingsRepository.getBrandSettings();
    }

    /** 등록한 집·회사 위치. 읽기 전에는 null */
    public LiveData<SavedPlaces> getPlaces() {
        return savedPlaceRepository.getPlaces();
    }

    public void resetCarInfoToDefault() {
        carInfoRepository.resetToDefault();
    }

    public LiveData<ThemeMode> getThemeMode() {
        return themeMode;
    }

    /** 저장하고 바로 적용한다. 화면이 새 테마로 다시 만들어져도 ViewModel과 캐시는 그대로라 API를 다시 부르지 않는다. */
    public void setThemeMode(ThemeMode mode) {
        if (mode == themeMode.getValue()) {
            return;
        }
        themeMode.setValue(mode);
        themePreferences.setThemeMode(mode);
    }

    public LiveData<ApiUsage> getApiUsage() {
        return apiUsage;
    }

    /** 오늘 호출 횟수를 ApiCallCounter에서 다시 읽는다. 설정 탭이 보일 때마다 부른다. */
    public void refreshApiUsage() {
        apiUsage.setValue(new ApiUsage(apiCallCounter.getTodayCount(), ApiCallCounter.DAILY_LIMIT,
                BuildConfig.USE_MOCK_DATA));
    }
}
