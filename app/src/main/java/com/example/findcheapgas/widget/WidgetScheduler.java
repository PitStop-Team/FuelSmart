package com.example.findcheapgas.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * 위젯 갱신 작업 등록 (WorkManager).
 * - 주기 갱신: 3시간마다, 네트워크에 연결됐을 때. 고유 이름으로 한 번만 등록하고(KEEP), 마지막 위젯이 지워지면 취소한다.
 * - 한 번 갱신: 새로고침 버튼, 설정 변경, 위젯 추가. 앞의 작업이 끝난 뒤에 이어서 돌린다(APPEND_OR_REPLACE).
 *   캐시만으로 다시 그릴 수도 있으므로 네트워크 조건은 걸지 않는다 (가져오지 못하면 "갱신 실패"를 보여준다).
 */
public final class WidgetScheduler {

    /** adb로 주기 작업을 바로 돌려 볼 때도 이 이름을 쓴다. */
    public static final String PERIODIC_WORK_NAME = "gas_widget_periodic";
    static final String ONE_TIME_WORK_NAME = "gas_widget_once";
    static final long PERIOD_HOURS = 3;

    private WidgetScheduler() {
    }

    /** 홈 화면에 이 앱의 위젯이 하나라도 있는지 */
    public static boolean hasWidgets(Context context) {
        return widgetIds(context).length > 0;
    }

    static int[] widgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) {
            return new int[0];
        }
        return manager.getAppWidgetIds(new ComponentName(context, GasWidgetProvider.class));
    }

    /** 3시간마다 갱신. 이미 등록되어 있으면 그대로 둔다. */
    static void ensurePeriodic(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(WidgetUpdateWorker.class,
                PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInputData(modeData(WidgetRefreshMode.PERIODIC))
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** 위젯이 있으면 한 번 갱신한다. */
    public static void requestUpdate(Context context, WidgetRefreshMode mode) {
        if (!hasWidgets(context)) {
            return;
        }
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetUpdateWorker.class)
                .setInputData(modeData(mode))
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    /** 마지막 위젯이 지워졌을 때 */
    static void cancelAll(Context context) {
        WorkManager manager = WorkManager.getInstance(context);
        manager.cancelUniqueWork(PERIODIC_WORK_NAME);
        manager.cancelUniqueWork(ONE_TIME_WORK_NAME);
    }

    private static Data modeData(WidgetRefreshMode mode) {
        return new Data.Builder().putString(WidgetUpdateWorker.KEY_MODE, mode.name()).build();
    }
}
