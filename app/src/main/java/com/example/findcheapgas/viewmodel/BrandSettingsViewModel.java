package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.findcheapgas.data.repository.BrandSettingsRepository;
import com.example.findcheapgas.model.BrandSetting;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.util.BrandSettingValidator;

/**
 * 내 브랜드 화면과 수정 시트의 상태. 시트는 Activity의 이 ViewModel을 함께 쓴다.
 * 저장하면 BrandSettingsRepository의 LiveData가 바뀌고, 지도 탭은 API 없이 다시 계산한다.
 */
public class BrandSettingsViewModel extends AndroidViewModel {

    /** 저장 결과. 입력칸마다 오류가 없으면 null이다. 둘 다 null이면 저장된 것이다. */
    public static final class SaveResult {
        @Nullable
        public final BrandSettingValidator.Error perLiterError;
        @Nullable
        public final BrandSettingValidator.Error capError;

        SaveResult(@Nullable BrandSettingValidator.Error perLiterError,
                   @Nullable BrandSettingValidator.Error capError) {
            this.perLiterError = perLiterError;
            this.capError = capError;
        }

        public boolean isSaved() {
            return perLiterError == null && capError == null;
        }
    }

    private final BrandSettingsRepository repository;

    public BrandSettingsViewModel(@NonNull Application application) {
        super(application);
        repository = BrandSettingsRepository.getInstance(application);
    }

    public LiveData<BrandSettings> getBrandSettings() {
        return repository.getBrandSettings();
    }

    public BrandSetting getSetting(String brandCode) {
        return repository.getCurrent().get(brandCode);
    }

    /**
     * 입력칸 글자를 검사해서 범위 안이면 저장한다. 하나라도 범위를 벗어나면 아무것도 저장하지 않는다.
     * 선호와 숨기기가 둘 다 켜져 오면 숨기기를 우선한다 (화면에서는 하나를 켜면 다른 하나가 꺼진다).
     */
    public SaveResult save(String brandCode, String perLiterText, String capText,
                           boolean preferred, boolean hidden) {
        BrandSettingValidator.Result perLiter = BrandSettingValidator.parsePerLiter(perLiterText);
        BrandSettingValidator.Result cap = BrandSettingValidator.parseCap(capText);
        SaveResult result = new SaveResult(perLiter.getError(), cap.getError());
        if (result.isSaved()) {
            repository.save(brandCode, new BrandSetting(perLiter.getValue(), cap.getValue(),
                    preferred && !hidden, hidden));
        }
        return result;
    }

    /** 리터당 할인과 1회 할인 한도를 지운다. 선호, 숨기기는 화면에서 고른 값으로 저장한다. */
    public void clearDiscount(String brandCode, boolean preferred, boolean hidden) {
        repository.save(brandCode, new BrandSetting(0, null, preferred && !hidden, hidden));
    }
}
