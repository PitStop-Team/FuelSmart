package com.example.findcheapgas.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.example.findcheapgas.R;

/**
 * 코드에서 굵기를 바꾸거나 직접 그리는 글자(지도 마커, 그래프)에 앱 글꼴(Pretendard)을 쓴다.
 * setTypeface(null, BOLD)는 시스템 글꼴로 바뀌므로 쓰지 않는다.
 */
public final class AppFonts {

    private AppFonts() {
    }

    /** 앱 글꼴의 보통(Regular) 또는 굵게(Bold). 글꼴을 읽지 못하면 시스템 글꼴 */
    @NonNull
    public static Typeface of(@NonNull Context context, int style) {
        Typeface family = ResourcesCompat.getFont(context, R.font.app_font);
        return Typeface.create(family, style);
    }

    /** 글자 굵기를 앱 글꼴 안에서 보통 / 굵게로 바꾼다 (목록 재사용 시 이전 굵기가 남지 않음). */
    public static void setBold(@NonNull TextView view, boolean bold) {
        view.setTypeface(of(view.getContext(), bold ? Typeface.BOLD : Typeface.NORMAL));
    }
}
