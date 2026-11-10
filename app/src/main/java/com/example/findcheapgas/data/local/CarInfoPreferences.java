package com.example.findcheapgas.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.findcheapgas.model.CarInfo;

/**
 * 내 차 정보와 처음 실행 화면 완료 여부 (SharedPreferences).
 * 사용자가 입력하지 않았으면 값을 저장하지 않고 CarInfo.DEFAULT를 돌려준다.
 */
public class CarInfoPreferences {

    private static final String PREFS_NAME = "car_info";
    private static final String KEY_USER_ENTERED = "user_entered";
    private static final String KEY_FUEL_EFFICIENCY_TENTHS = "fuel_efficiency_tenths"; // 12.5km/L → 125
    private static final String KEY_FUEL_AMOUNT = "fuel_amount";                        // L
    // 처음 실행 화면을 저장 또는 건너뛰기로 마쳤는지
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";

    private final SharedPreferences prefs;

    public CarInfoPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public CarInfo getCarInfo() {
        return CarInfo.fromStored(
                prefs.getBoolean(KEY_USER_ENTERED, false),
                prefs.getInt(KEY_FUEL_EFFICIENCY_TENTHS, 0),
                prefs.getInt(KEY_FUEL_AMOUNT, 0));
    }

    public void setCarInfo(CarInfo carInfo) {
        if (carInfo.isDefault()) {
            clearCarInfo();
            return;
        }
        prefs.edit()
                .putBoolean(KEY_USER_ENTERED, true)
                .putInt(KEY_FUEL_EFFICIENCY_TENTHS, carInfo.getFuelEfficiencyTenths())
                .putInt(KEY_FUEL_AMOUNT, carInfo.getFuelAmount())
                .apply();
    }

    /** 기본값으로 되돌린다. */
    public void clearCarInfo() {
        prefs.edit()
                .remove(KEY_USER_ENTERED)
                .remove(KEY_FUEL_EFFICIENCY_TENTHS)
                .remove(KEY_FUEL_AMOUNT)
                .apply();
    }

    public boolean isOnboardingCompleted() {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    public void setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply();
    }
}
