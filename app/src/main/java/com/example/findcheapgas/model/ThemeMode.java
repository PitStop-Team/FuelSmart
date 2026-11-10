package com.example.findcheapgas.model;

import android.app.UiModeManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * 설정 탭의 화면 테마. 시스템 설정 따르기(기본) / 밝게 / 어둡게.
 * SharedPreferences에는 storedValue("system" 등)로 저장하고, 적용할 때는 AppCompat과 시스템(UiModeManager)의 값으로 바꾼다.
 */
public enum ThemeMode {
    SYSTEM("system", "시스템 설정 따르기",
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, UiModeManager.MODE_NIGHT_AUTO),
    LIGHT("light", "밝게",
            AppCompatDelegate.MODE_NIGHT_NO, UiModeManager.MODE_NIGHT_NO),
    DARK("dark", "어둡게",
            AppCompatDelegate.MODE_NIGHT_YES, UiModeManager.MODE_NIGHT_YES);

    public static final ThemeMode DEFAULT = SYSTEM;

    private final String storedValue;
    private final String label;
    private final int appCompatNightMode;
    private final int uiModeManagerNightMode;

    ThemeMode(String storedValue, String label, int appCompatNightMode, int uiModeManagerNightMode) {
        this.storedValue = storedValue;
        this.label = label;
        this.appCompatNightMode = appCompatNightMode;
        this.uiModeManagerNightMode = uiModeManagerNightMode;
    }

    /** SharedPreferences에 저장하는 값 */
    public String getStoredValue() {
        return storedValue;
    }

    public String getLabel() {
        return label;
    }

    /** AppCompatDelegate.setDefaultNightMode에 넘기는 값 */
    public int getAppCompatNightMode() {
        return appCompatNightMode;
    }

    /** UiModeManager.setApplicationNightMode(Android 12 이상)에 넘기는 값 */
    public int getUiModeManagerNightMode() {
        return uiModeManagerNightMode;
    }

    /** 저장한 값이 없거나 알 수 없는 값이면 기본값(시스템 설정 따르기) */
    public static ThemeMode fromStoredValue(@Nullable String value) {
        for (ThemeMode mode : values()) {
            if (mode.storedValue.equals(value)) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
