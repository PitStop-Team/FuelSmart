package com.example.findcheapgas.data.local;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.findcheapgas.model.ThemeMode;

/**
 * 설정 탭에서 고른 화면 테마 (SharedPreferences).
 * 앱이 시작할 때(FindCheapGasApp) 첫 화면보다 먼저 apply()로 적용하고, 설정에서 바꾸면 저장하고 바로 적용한다.
 * 홈 화면 위젯은 런처가 그리므로 이 값이 아니라 휴대폰 시스템의 다크 모드를 따른다.
 */
public class ThemePreferences {

    private static final String PREFS_NAME = "display";
    private static final String KEY_THEME_MODE = "theme_mode";   // ThemeMode.getStoredValue()

    private final Context context;
    private final SharedPreferences prefs;

    public ThemePreferences(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public ThemeMode getThemeMode() {
        return ThemeMode.fromStoredValue(prefs.getString(KEY_THEME_MODE, null));
    }

    /** 저장하고 바로 적용한다. 열려 있는 화면은 새 테마로 다시 만들어진다 (ViewModel은 그대로). */
    public void setThemeMode(ThemeMode mode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.getStoredValue()).apply();
        apply(mode);
    }

    /** 저장된 테마를 적용한다. 앱 시작 시 첫 화면이 그려지기 전에 부른다. */
    public void applySaved() {
        apply(getThemeMode());
    }

    // ThemeMode.getUiModeManagerNightMode()는 UiModeManager.MODE_NIGHT_AUTO / NO / YES만 돌려준다 (ThemeModeTest).
    @SuppressLint("WrongConstant")
    private void apply(ThemeMode mode) {
        // Android 12 이상은 시스템에도 앱 테마를 알려서, 앱을 켤 때 시스템이 그리는 로딩 화면도 같은 테마로 나오게 한다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = context.getSystemService(UiModeManager.class);
            if (uiModeManager != null) {
                uiModeManager.setApplicationNightMode(mode.getUiModeManagerNightMode());
            }
        }
        AppCompatDelegate.setDefaultNightMode(mode.getAppCompatNightMode());
    }
}
