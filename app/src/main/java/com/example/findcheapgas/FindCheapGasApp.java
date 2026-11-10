package com.example.findcheapgas;

import android.app.Application;

import com.example.findcheapgas.data.local.ThemePreferences;
import com.example.findcheapgas.pleos.NaviHelperNavigationController;
import com.example.findcheapgas.pleos.PleosVehicleSession;

public class FindCheapGasApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 설정에서 고른 화면 테마(시스템 / 밝게 / 어둡게)를 첫 화면이 만들어지기 전에 적용한다.
        // 화면을 그린 뒤에 적용하면 밝은 화면이 잠깐 보였다가 바뀐다.
        new ThemePreferences(this).applySaved();

        // Pleos Connect SDK. 차량 서비스가 없으면(폰, 에뮬레이터) 연결에 실패하고 차량 데이터 없이 동작한다.
        PleosVehicleSession.getInstance(this).start();
        NaviHelperNavigationController.getInstance(this).start();
    }
}
