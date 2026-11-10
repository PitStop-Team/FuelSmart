package com.example.findcheapgas.widget;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.findcheapgas.model.SavedPlace;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/**
 * 위젯 설정과 마지막으로 그린 내용 (SharedPreferences "widget").
 * - 위젯 ID별로 고른 위치 종류(집 / 회사). 위젯을 지우면 그 ID의 값도 지운다.
 * - 종류별로 마지막에 그린 내용(크기를 바꿀 때 다시 그리기용)과 마지막 READY 결과(갱신에 실패했을 때 남겨 둘 결과).
 * 집·회사의 이름, 주소, 좌표는 저장하지 않는다. 이득 1위 주유소 이름은 들어 있어서 백업에서는 뺀다 (backup_rules).
 */
public class WidgetPreferences {

    static final String PREFS_NAME = "widget";
    private static final String KEY_KIND_PREFIX = "kind_";          // kind_{위젯 ID} → HOME / WORK
    private static final String KEY_CONTENT_PREFIX = "content_";    // content_{종류} → 마지막에 그린 내용
    private static final String KEY_READY_PREFIX = "ready_";        // ready_{종류} → 마지막 READY 결과

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public WidgetPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 그 위젯이 고른 종류. 아직 고르지 않았으면 null */
    @Nullable
    public SavedPlace.Kind getKind(int widgetId) {
        String name = prefs.getString(KEY_KIND_PREFIX + widgetId, null);
        if (name == null) {
            return null;
        }
        try {
            return SavedPlace.Kind.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    public void setKind(int widgetId, SavedPlace.Kind kind) {
        // apply는 메모리의 값을 바로 바꾸므로, 이어서 도는 위젯 작업(같은 프로세스)도 새 값을 읽는다.
        prefs.edit().putString(KEY_KIND_PREFIX + widgetId, kind.name()).apply();
    }

    public void removeWidget(int widgetId) {
        prefs.edit().remove(KEY_KIND_PREFIX + widgetId).apply();
    }

    /** 마지막 위젯이 지워졌을 때 */
    public void clearAll() {
        prefs.edit().clear().apply();
    }

    @Nullable
    public WidgetContent getContent(SavedPlace.Kind kind) {
        return read(KEY_CONTENT_PREFIX + kind.name());
    }

    @Nullable
    public WidgetContent getLastReady(SavedPlace.Kind kind) {
        return read(KEY_READY_PREFIX + kind.name());
    }

    /** 그린 내용을 저장한다. 이번에 새로 READY가 됐으면 갱신 실패 때 남겨 둘 결과로도 저장한다. */
    public void saveContent(WidgetContent content) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_CONTENT_PREFIX + content.getKind().name(), gson.toJson(content));
        if (content.getState() == WidgetContent.State.READY && !content.isUpdateFailed()) {
            editor.putString(KEY_READY_PREFIX + content.getKind().name(), gson.toJson(content));
        }
        editor.apply();
    }

    @Nullable
    private WidgetContent read(String key) {
        String json = prefs.getString(key, null);
        if (json == null) {
            return null;
        }
        try {
            WidgetContent content = gson.fromJson(json, WidgetContent.class);
            return content != null && content.getKind() != null && content.getState() != null ? content : null;
        } catch (JsonParseException e) {
            return null;
        }
    }
}
