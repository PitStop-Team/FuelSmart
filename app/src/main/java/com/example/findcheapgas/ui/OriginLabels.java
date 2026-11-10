package com.example.findcheapgas.ui;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.SearchOrigin;

/**
 * 조회 위치 이름: 검색한 장소 이름, 지도 중심의 동 이름(모르면 "지도 중심"), 내 위치, 기본 위치(서울시청).
 * 지도 탭과 즐겨찾기 탭이 같이 쓴다.
 */
public final class OriginLabels {

    private OriginLabels() {
    }

    public static String of(Context context, @Nullable SearchOrigin origin) {
        if (origin != null && origin.isPlace()) {
            return origin.getName();
        }
        if (origin != null && origin.isMapCenter()) {
            String name = origin.getName();
            return name != null ? name : context.getString(R.string.origin_map_center);
        }
        if (origin != null && origin.isDefaultLocation()) {
            return context.getString(R.string.origin_default_location);
        }
        return context.getString(R.string.origin_my_location);
    }
}
