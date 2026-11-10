package com.example.findcheapgas.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.findcheapgas.R;
import com.example.findcheapgas.data.repository.SavedPlaceRepository;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * 위젯 구성 화면. 위젯을 놓을 때(또는 Android 12 이상에서 길게 눌러 다시 고를 때) 집 / 회사 중 하나를 고른다.
 * 고른 값은 위젯 ID별로 기억한다 (WidgetPreferences). 등록하지 않은 위치를 골라도 되고,
 * 그때 위젯은 "앱에서 집 위치를 등록하세요"를 보여주다가 등록하면 바로 다시 그려진다.
 * 고르지 않고 닫으면 위젯을 놓지 않는다.
 */
public class WidgetConfigActivity extends AppCompatActivity {

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent.getExtras() != null) {
            widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        // 고르지 않고 뒤로 가면 위젯을 놓지 않는다.
        setResult(RESULT_CANCELED, resultIntent());
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        setContentView(R.layout.activity_widget_config);
        MaterialToolbar toolbar = findViewById(R.id.widget_config_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.widget_config_home).setOnClickListener(v -> choose(SavedPlace.Kind.HOME));
        findViewById(R.id.widget_config_work).setOnClickListener(v -> choose(SavedPlace.Kind.WORK));
        TextView homeStatus = findViewById(R.id.widget_config_home_status);
        TextView workStatus = findViewById(R.id.widget_config_work_status);
        SavedPlaceRepository.getInstance(this).getPlaces().observe(this, places -> {
            homeStatus.setText(status(places, SavedPlace.Kind.HOME));
            workStatus.setText(status(places, SavedPlace.Kind.WORK));
        });
    }

    private String status(@Nullable SavedPlaces places, SavedPlace.Kind kind) {
        boolean registered = places != null && places.get(kind) != null;
        return getString(registered ? R.string.widget_config_registered : R.string.widget_config_not_registered);
    }

    private void choose(SavedPlace.Kind kind) {
        WidgetPreferences prefs = new WidgetPreferences(this);
        prefs.setKind(widgetId, kind);
        // 같은 종류로 그려 둔 내용이 있으면 바로 보여주고, 작업이 캐시로(없으면 조회해서) 다시 그린다.
        WidgetRenderer.update(this, AppWidgetManager.getInstance(this), widgetId, kind, prefs.getContent(kind));
        WidgetScheduler.ensurePeriodic(this);
        WidgetScheduler.requestUpdate(this, WidgetRefreshMode.REDRAW);
        setResult(RESULT_OK, resultIntent());
        finish();
    }

    private Intent resultIntent() {
        return new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
    }
}
