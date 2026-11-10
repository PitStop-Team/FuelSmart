package com.example.findcheapgas.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.example.findcheapgas.model.SavedPlace;

/**
 * 홈 화면 위젯 (집 또는 회사 근처의 이득 1위 주유소).
 * 여기서는 저장해 둔 내용을 바로 다시 그리기만 하고, 가격 조회와 계산은 WidgetUpdateWorker가 백그라운드에서 한다.
 * - 첫 위젯을 놓으면 3시간 주기 갱신을 등록하고, 마지막 위젯을 지우면 취소한다.
 * - 새로고침 버튼: 20분이 지난 것만 다시 가져온다 (WidgetRefreshMode.REFRESH).
 * - 위젯을 지우면 그 위젯이 고른 위치(집 / 회사) 기억도 지운다.
 */
public class GasWidgetProvider extends AppWidgetProvider {

    static final String ACTION_REFRESH = "com.example.findcheapgas.widget.ACTION_REFRESH";

    @Override
    public void onEnabled(Context context) {
        WidgetScheduler.ensurePeriodic(context);
    }

    /** 기기를 다시 켰거나 앱을 업데이트했을 때 등. 저장한 내용으로 그리고, 캐시로 다시 계산한다. */
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        WidgetPreferences prefs = new WidgetPreferences(context);
        boolean configured = false;
        for (int id : widgetIds) {
            SavedPlace.Kind kind = prefs.getKind(id);
            if (kind == null) {
                continue; // 구성 화면에서 위치를 고르기 전에는 그리지 않는다 (구성 화면이 처음 그린다).
            }
            configured = true;
            WidgetRenderer.update(context, manager, id, kind, prefs.getContent(kind));
        }
        WidgetScheduler.ensurePeriodic(context);
        if (configured) {
            WidgetScheduler.requestUpdate(context, WidgetRefreshMode.REDRAW);
        }
    }

    /** 크기를 바꿨을 때 (Android 11 이하는 여기서 작은 / 큰 화면을 다시 고른다) */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int widgetId,
                                          Bundle newOptions) {
        SavedPlace.Kind kind = new WidgetPreferences(context).getKind(widgetId);
        if (kind != null) {
            WidgetRenderer.update(context, manager, widgetId, kind, new WidgetPreferences(context).getContent(kind));
        }
    }

    @Override
    public void onDeleted(Context context, int[] widgetIds) {
        WidgetPreferences prefs = new WidgetPreferences(context);
        for (int id : widgetIds) {
            prefs.removeWidget(id);
        }
    }

    @Override
    public void onDisabled(Context context) {
        WidgetScheduler.cancelAll(context);
        new WidgetPreferences(context).clearAll();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            onRefreshClick(context);
            return;
        }
        super.onReceive(context, intent);
    }

    /** 새로고침 버튼. 위젯들은 같은 위치의 결과를 같이 쓰므로 모두 함께 갱신한다. */
    private void onRefreshClick(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        WidgetPreferences prefs = new WidgetPreferences(context);
        for (int id : WidgetScheduler.widgetIds(context)) {
            SavedPlace.Kind kind = prefs.getKind(id);
            if (kind != null) {
                WidgetRenderer.update(context, manager, id, kind, prefs.getContent(kind), true);
            }
        }
        WidgetScheduler.requestUpdate(context, WidgetRefreshMode.REFRESH);
    }
}
