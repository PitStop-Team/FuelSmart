package com.example.findcheapgas;

import android.app.Application;

import com.example.findcheapgas.data.local.ThemePreferences;

public class FindCheapGasApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 설정에서 고른 화면 테마(시스템 / 밝게 / 어둡게)를 첫 화면이 만들어지기 전에 적용한다.
        // 화면을 그린 뒤에 적용하면 밝은 화면이 잠깐 보였다가 바뀐다.
        new ThemePreferences(this).applySaved();
    }
}
