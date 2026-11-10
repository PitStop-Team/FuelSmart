package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.data.local.StationFilterPreferences;
import com.example.findcheapgas.data.repository.CarInfoRepository;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.util.CarInfoValidator;

/**
 * 내 차 정보 입력 화면(처음 실행, 수정)의 상태.
 * 입력칸마다 검사 오류를 들고 있어서 화면을 회전해도 오류 안내가 남는다. API는 부르지 않는다.
 */
public class OnboardingViewModel extends AndroidViewModel {

    private final CarInfoRepository carInfoRepository;
    private final StationFilterPreferences filterPreferences;

    // 오류가 없으면 null
    private final MutableLiveData<CarInfoValidator.Error> fuelEfficiencyError = new MutableLiveData<>();
    private final MutableLiveData<CarInfoValidator.Error> fuelAmountError = new MutableLiveData<>();
    // 처음 실행 화면에서 고른 유종. 지도 탭에서 쓰던 유종으로 시작한다.
    private FuelType selectedFuelType;

    public OnboardingViewModel(@NonNull Application application) {
        super(application);
        carInfoRepository = CarInfoRepository.getInstance(application);
        filterPreferences = new StationFilterPreferences(application);
        selectedFuelType = filterPreferences.getFuelType();
    }

    public LiveData<CarInfoValidator.Error> getFuelEfficiencyError() {
        return fuelEfficiencyError;
    }

    public LiveData<CarInfoValidator.Error> getFuelAmountError() {
        return fuelAmountError;
    }

    public CarInfo getCurrentCarInfo() {
        return carInfoRepository.getCurrent();
    }

    public FuelType getSelectedFuelType() {
        return selectedFuelType;
    }

    public void selectFuelType(FuelType fuelType) {
        selectedFuelType = fuelType;
    }

    /**
     * 두 입력칸을 모두 검사하고, 둘 다 통과하면 저장한다.
     * 하나라도 틀리면 저장하지 않고 false. 틀린 칸의 오류는 LiveData로 알린다.
     */
    public boolean saveCarInfo(String fuelEfficiencyText, String fuelAmountText) {
        CarInfoValidator.Result efficiency = CarInfoValidator.parseFuelEfficiency(fuelEfficiencyText);
        CarInfoValidator.Result amount = CarInfoValidator.parseFuelAmount(fuelAmountText);
        fuelEfficiencyError.setValue(efficiency.getError());
        fuelAmountError.setValue(amount.getError());
        if (!efficiency.isValid() || !amount.isValid()) {
            return false;
        }
        carInfoRepository.save(CarInfo.userEntered(efficiency.getValue(), amount.getValue()));
        return true;
    }

    /** 입력칸 글자가 바뀌면 그 칸의 오류 안내를 지운다. */
    public void clearFuelEfficiencyError() {
        if (fuelEfficiencyError.getValue() != null) {
            fuelEfficiencyError.setValue(null);
        }
    }

    public void clearFuelAmountError() {
        if (fuelAmountError.getValue() != null) {
            fuelAmountError.setValue(null);
        }
    }

    /**
     * 처음 실행 화면을 마친다 (저장, 건너뛰기 모두).
     * 고른 유종은 지도 탭의 유종 선택값으로 저장한다. 지도 탭이 열리기 전이라 API를 다시 부르지 않는다.
     */
    public void completeOnboarding() {
        filterPreferences.setFuelType(selectedFuelType);
        carInfoRepository.setOnboardingCompleted();
    }
}
