package com.example.findcheapgas.data.repository;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.data.local.CarInfoPreferences;
import com.example.findcheapgas.model.CarInfo;

/**
 * 지금 계산에 쓰는 내 차 정보. API는 부르지 않는다.
 * 앱 전체에서 하나만 쓰고 LiveData로 내보낸다. 처음 실행 화면이나 설정 탭에서 값을 바꾸면
 * 지도 탭, 설정 탭처럼 이 LiveData를 보고 있는 화면이 모두 바로 바뀐다.
 * 값은 메인 스레드에서만 바꾼다.
 */
public class CarInfoRepository {

    private static volatile CarInfoRepository instance;

    private final CarInfoPreferences preferences;
    private final MutableLiveData<CarInfo> carInfo;

    public static CarInfoRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (CarInfoRepository.class) {
                if (instance == null) {
                    instance = new CarInfoRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private CarInfoRepository(Context context) {
        preferences = new CarInfoPreferences(context);
        carInfo = new MutableLiveData<>(preferences.getCarInfo());
    }

    /** 항상 값이 있다 (입력하지 않았으면 CarInfo.DEFAULT). */
    public LiveData<CarInfo> getCarInfo() {
        return carInfo;
    }

    public CarInfo getCurrent() {
        return carInfo.getValue();
    }

    @MainThread
    public void save(CarInfo newCarInfo) {
        preferences.setCarInfo(newCarInfo);
        carInfo.setValue(newCarInfo);
    }

    @MainThread
    public void resetToDefault() {
        preferences.clearCarInfo();
        carInfo.setValue(CarInfo.DEFAULT);
    }

    public boolean isOnboardingCompleted() {
        return preferences.isOnboardingCompleted();
    }

    /** 저장하거나 건너뛰면 완료로 본다. 다음 실행부터 처음 실행 화면을 보여주지 않는다. */
    public void setOnboardingCompleted() {
        preferences.setOnboardingCompleted();
    }
}
