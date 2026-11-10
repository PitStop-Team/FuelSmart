package com.example.findcheapgas.data.repository;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.data.local.BrandPreferences;
import com.example.findcheapgas.model.BrandSetting;
import com.example.findcheapgas.model.BrandSettings;

/**
 * 지금 계산에 쓰는 내 브랜드 설정. API는 부르지 않는다.
 * 앱 전체에서 하나만 쓰고 LiveData로 내보낸다 (CarInfoRepository와 같은 방식). 내 브랜드 화면에서 저장하면
 * 지도 탭은 받아 둔 목록으로 다시 계산하고, 설정 탭의 요약 문구도 바로 바뀐다.
 * 값은 메인 스레드에서만 바꾼다.
 */
public class BrandSettingsRepository {

    private static volatile BrandSettingsRepository instance;

    private final BrandPreferences preferences;
    private final MutableLiveData<BrandSettings> brandSettings;

    public static BrandSettingsRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (BrandSettingsRepository.class) {
                if (instance == null) {
                    instance = new BrandSettingsRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private BrandSettingsRepository(Context context) {
        preferences = new BrandPreferences(context);
        brandSettings = new MutableLiveData<>(preferences.getBrandSettings());
    }

    /** 항상 값이 있다 (설정하지 않았으면 BrandSettings.EMPTY와 같은 내용). */
    public LiveData<BrandSettings> getBrandSettings() {
        return brandSettings;
    }

    public BrandSettings getCurrent() {
        return brandSettings.getValue();
    }

    @MainThread
    public void save(String brandCode, BrandSetting setting) {
        preferences.setBrandSetting(brandCode, setting);
        brandSettings.setValue(getCurrent().with(brandCode, setting));
    }
}
