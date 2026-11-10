package com.example.findcheapgas.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.findcheapgas.MainActivity;
import com.example.findcheapgas.R;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.ui.place.PlaceSettingsActivity;
import com.example.findcheapgas.util.FormatUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * WidgetContent를 위젯 화면(RemoteViews)으로 그린다.
 * - 크게(기본 4×2): 제목("집 근처"), 상호, 가격, 예상 이득, 브랜드 · 거리, 계산 기준 · 갱신 시각, 새로고침 버튼
 * - 작게(가로 220dp 또는 세로 100dp 미만): 상호, 가격, 예상 이득만
 *   Android 12 이상은 두 크기를 모두 넘겨서 런처가 고르고, 그 아래는 위젯 크기 정보로 골라서 넘긴다.
 * - 누르면: 등록하지 않았으면 집·회사 위치 화면, 그 밖에는 앱의 지도 탭에서 그 위치 기준으로 이득 1위 선택.
 *   PendingIntent는 FLAG_IMMUTABLE이고, 위젯 ID를 requestCode와 data에 넣어서 위젯끼리 겹치지 않는다.
 * 주소 대신 "집 근처", "회사 근처"로만 표시한다.
 */
final class WidgetRenderer {

    private static final float SMALL_MAX_WIDTH_DP = 220f;
    private static final float SMALL_MAX_HEIGHT_DP = 100f;
    // Android 12 이상 런처에 넘기는 크기: 작게 줄인 위젯 / 기본 크기 이상
    private static final SizeF SMALL_SIZE = new SizeF(110f, 40f);
    private static final SizeF LARGE_SIZE = new SizeF(SMALL_MAX_WIDTH_DP, SMALL_MAX_HEIGHT_DP);

    private WidgetRenderer() {
    }

    /** kind는 위젯이 고른 위치. content가 null이면 "가져오는 중"으로 그린다. */
    static void update(Context context, AppWidgetManager manager, int widgetId, SavedPlace.Kind kind,
                       @Nullable WidgetContent content) {
        update(context, manager, widgetId, kind, content, false);
    }

    /** refreshing이면 새로고침 버튼을 누른 직후라 제목 옆에 "갱신 중"을 보여준다. */
    static void update(Context context, AppWidgetManager manager, int widgetId, SavedPlace.Kind kind,
                       @Nullable WidgetContent content, boolean refreshing) {
        // 다른 종류의 내용이면(위치를 바꿔 고른 직후) 쓰지 않고 "가져오는 중"으로 그린다.
        WidgetContent shown = content != null && content.getKind() == kind ? content : null;
        manager.updateAppWidget(widgetId, build(context, manager, widgetId, kind, shown, refreshing));
    }

    private static RemoteViews build(Context context, AppWidgetManager manager, int widgetId, SavedPlace.Kind kind,
                                     @Nullable WidgetContent content, boolean refreshing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<SizeF, RemoteViews> layouts = new HashMap<>();
            layouts.put(SMALL_SIZE, layout(context, widgetId, kind, content, refreshing, true));
            layouts.put(LARGE_SIZE, layout(context, widgetId, kind, content, refreshing, false));
            return new RemoteViews(layouts);
        }
        Bundle options = manager.getAppWidgetOptions(widgetId);
        // 세로 화면 기준 크기: 너비는 MIN_WIDTH, 높이는 MAX_HEIGHT. 크기를 모르면(0) 크게 그린다.
        int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        boolean small = (width > 0 && width < SMALL_MAX_WIDTH_DP) || (height > 0 && height < SMALL_MAX_HEIGHT_DP);
        return layout(context, widgetId, kind, content, refreshing, small);
    }

    private static RemoteViews layout(Context context, int widgetId, SavedPlace.Kind kind,
                                      @Nullable WidgetContent content, boolean refreshing, boolean small) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                small ? R.layout.widget_station_small : R.layout.widget_station);
        WidgetContent.State state = content != null ? content.getState() : WidgetContent.State.LOADING;
        boolean ready = state == WidgetContent.State.READY;

        views.setViewVisibility(R.id.widget_content, ready ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_message, ready ? View.GONE : View.VISIBLE);
        if (ready) {
            views.setTextViewText(R.id.widget_station_name, content.getStationName());
            views.setTextViewText(R.id.widget_price, FormatUtil.formatPrice(content.getPrice()));
            views.setTextViewText(R.id.widget_saving, content.getSavingText());
            setTextColor(context, views, R.id.widget_saving, savingColor(content));
        } else {
            views.setTextViewText(R.id.widget_message, message(context, content));
        }

        if (!small) {
            views.setTextViewText(R.id.widget_title, context.getString(kind == SavedPlace.Kind.HOME
                    ? R.string.widget_title_home : R.string.widget_title_work));
            renderStatus(context, views, content, refreshing);
            if (ready) {
                views.setTextViewText(R.id.widget_detail, context.getString(R.string.widget_detail,
                        content.getBrandName(), FormatUtil.formatDistance(content.getDistanceMeters())));
                views.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_footer,
                        content.getBasis(), FormatUtil.formatUpdatedTime(content.getFetchedAt(),
                                System.currentTimeMillis(), TimeZone.getDefault())));
            }
            views.setViewVisibility(R.id.widget_footer, ready ? View.VISIBLE : View.GONE);
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, widgetId));
        }

        views.setOnClickPendingIntent(android.R.id.background, openIntent(context, widgetId, kind, state));
        return views;
    }

    /** 제목 옆 상태: 새로고침 중 "갱신 중", 이전 결과를 보여주는 중이면 "갱신 실패" */
    private static void renderStatus(Context context, RemoteViews views, @Nullable WidgetContent content,
                                     boolean refreshing) {
        String status = null;
        @ColorRes int statusColor = R.color.widget_update_failed;
        if (refreshing) {
            status = context.getString(R.string.widget_refreshing);
            statusColor = R.color.widget_text_secondary;
        } else if (content != null && content.isUpdateFailed()) {
            StationError error = content.getError();
            if (error == StationError.DAILY_LIMIT) {
                status = context.getString(R.string.widget_update_failed_daily_limit);
            } else if (error == StationError.NETWORK) {
                status = context.getString(R.string.widget_update_failed_network);
            } else if (error == StationError.API_KEY) {
                status = context.getString(R.string.widget_update_failed_api_key);
            } else {
                status = context.getString(R.string.widget_update_failed);
            }
        }
        views.setTextViewText(R.id.widget_status, status != null ? status : "");
        setTextColor(context, views, R.id.widget_status, statusColor);
    }

    private static String message(Context context, @Nullable WidgetContent content) {
        if (content == null) {
            return context.getString(R.string.widget_loading);
        }
        switch (content.getState()) {
            case NOT_REGISTERED:
                return context.getString(content.getKind() == SavedPlace.Kind.HOME
                        ? R.string.widget_not_registered_home : R.string.widget_not_registered_work);
            case OUTSIDE_MOCK_AREA:
                return context.getString(R.string.widget_outside_mock);
            case EMPTY:
                switch (content.getEmptyReason()) {
                    case HIDDEN_BRANDS:
                        return context.getString(R.string.widget_empty_hidden);
                    case BRAND_FILTER:
                        return context.getString(R.string.widget_empty_filter);
                    default:
                        SearchRadius radius = SearchRadius.fromMeters(content.getRadiusMeters(), SearchRadius.KM_3);
                        return context.getString(R.string.widget_empty_radius, radius.getLabel());
                }
            case ERROR:
                if (content.getError() == StationError.DAILY_LIMIT) {
                    return context.getString(R.string.widget_error_daily_limit);
                }
                if (content.getError() == StationError.NETWORK) {
                    return context.getString(R.string.widget_error_network);
                }
                if (content.getError() == StationError.API_KEY) {
                    return context.getString(R.string.widget_error_api_key);
                }
                return context.getString(R.string.widget_error_opinet);
            default:
                return context.getString(R.string.widget_loading);
        }
    }

    @ColorRes
    private static int savingColor(WidgetContent content) {
        if (content.getSavingLevel() == null) {
            return R.color.widget_text_secondary;
        }
        switch (content.getSavingLevel()) {
            case GAIN:
                return R.color.widget_saving_gain;
            case LOSS:
                return R.color.widget_saving_loss;
            default:
                return R.color.widget_saving_similar;
        }
    }

    /**
     * 글자 색을 바꾼다. 위젯은 앱 테마가 아니라 휴대폰 시스템의 다크 모드를 따른다.
     * Android 12 이상은 색 리소스를 넘겨서 런처가 그릴 때 시스템 테마에 맞는 색(values / values-night)을 고르게 한다.
     * 그래서 앱이 따로 어두운 테마를 쓰거나, 위젯을 그린 뒤 시스템 테마가 바뀌어도 위젯 배경과 글자 색이 맞는다.
     * 그 아래 버전은 그리는 시점의 시스템 테마 색 값을 넣는다. 위젯 구성 화면(Activity)에서 불러도 앱 테마가 섞이지 않게
     * Application의 Context로 읽는다.
     */
    private static void setTextColor(Context context, RemoteViews views, int viewId, @ColorRes int res) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColor(viewId, "setTextColor", res);
        } else {
            views.setTextColor(viewId, ContextCompat.getColor(context.getApplicationContext(), res));
        }
    }

    private static PendingIntent openIntent(Context context, int widgetId, SavedPlace.Kind kind,
                                            WidgetContent.State state) {
        // 집·회사 위치 화면은 앱이 열려 있으면 그 위에, 아니면 새로 연다 (열려 있는 앱 화면은 지우지 않는다).
        Intent intent = state == WidgetContent.State.NOT_REGISTERED
                ? PlaceSettingsActivity.createIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                : MainActivity.createWidgetIntent(context, kind);
        intent.setData(widgetUri(widgetId, "open"));
        return PendingIntent.getActivity(context, widgetId, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent refreshIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, GasWidgetProvider.class)
                .setAction(GasWidgetProvider.ACTION_REFRESH)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(widgetUri(widgetId, "refresh"));
        return PendingIntent.getBroadcast(context, widgetId, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** PendingIntent가 위젯마다 따로 만들어지도록 위젯 ID를 넣은 주소 (밖으로 보내지 않는다) */
    private static Uri widgetUri(int widgetId, String action) {
        return Uri.parse("findcheapgas-widget://" + action + "/" + widgetId);
    }
}
