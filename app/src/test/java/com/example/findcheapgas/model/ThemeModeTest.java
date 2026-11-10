package com.example.findcheapgas.model;

import static org.junit.Assert.assertEquals;

import android.app.UiModeManager;

import androidx.appcompat.app.AppCompatDelegate;

import org.junit.Test;

public class ThemeModeTest {

    @Test
    public void storedValue_roundTrips() {
        for (ThemeMode mode : ThemeMode.values()) {
            assertEquals(mode, ThemeMode.fromStoredValue(mode.getStoredValue()));
        }
    }

    @Test
    public void storedValues_areStable() {
        // SharedPreferences에 이미 저장된 값을 계속 읽을 수 있어야 한다.
        assertEquals("system", ThemeMode.SYSTEM.getStoredValue());
        assertEquals("light", ThemeMode.LIGHT.getStoredValue());
        assertEquals("dark", ThemeMode.DARK.getStoredValue());
    }

    @Test
    public void missingOrUnknown_fallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DEFAULT);
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue(null));
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue(""));
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue("DARK"));
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue("night"));
    }

    @Test
    public void appCompatNightMode() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.SYSTEM.getAppCompatNightMode());
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.LIGHT.getAppCompatNightMode());
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.DARK.getAppCompatNightMode());
    }

    @Test
    public void uiModeManagerNightMode() {
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, ThemeMode.SYSTEM.getUiModeManagerNightMode());
        assertEquals(UiModeManager.MODE_NIGHT_NO, ThemeMode.LIGHT.getUiModeManagerNightMode());
        assertEquals(UiModeManager.MODE_NIGHT_YES, ThemeMode.DARK.getUiModeManagerNightMode());
    }

    @Test
    public void labels() {
        assertEquals("시스템 설정 따르기", ThemeMode.SYSTEM.getLabel());
        assertEquals("밝게", ThemeMode.LIGHT.getLabel());
        assertEquals("어둡게", ThemeMode.DARK.getLabel());
    }
}
