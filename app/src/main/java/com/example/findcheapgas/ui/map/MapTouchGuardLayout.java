package com.example.findcheapgas.ui.map;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 카카오맵 MapView를 담는 틀. 지도에 들어가는 손짓을 먼저 보고 MapGestureGuard 규칙대로
 * 시트를 올린 동안의 누르기는 지도에 취소로 보내고(마커 선택 없음), 시트를 내리라고 알린다.
 */
public class MapTouchGuardLayout extends FrameLayout {

    /** 지도 탭이 시트 상태를 알려주고, 시트를 내리라는 요청을 받는다. */
    public interface Callback {
        @NonNull
        MapGestureGuard.SheetPosition currentSheetPosition();

        void collapseSheetFromMap();
    }

    private final MapGestureGuard guard = new MapGestureGuard();
    private final int touchSlop;
    @Nullable
    private Callback callback;
    private float downX;
    private float downY;

    public MapTouchGuardLayout(@NonNull Context context) {
        this(context, null);
    }

    public MapTouchGuardLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        MapGestureGuard.Result result = MapGestureGuard.Result.PASS;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                guard.onDown(callback != null
                        ? callback.currentSheetPosition() : MapGestureGuard.SheetPosition.COLLAPSED);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                result = guard.onSecondPointer();
                break;
            case MotionEvent.ACTION_MOVE:
                result = guard.onMove(event.getX() - downX, event.getY() - downY, touchSlop);
                break;
            case MotionEvent.ACTION_UP:
                result = guard.onUp();
                break;
            case MotionEvent.ACTION_CANCEL:
                guard.onCancel();
                break;
            default:
                break;
        }

        if (result == MapGestureGuard.Result.PASS) {
            return super.dispatchTouchEvent(event);
        }
        if (callback != null) {
            callback.collapseSheetFromMap();
        }
        if (result == MapGestureGuard.Result.COLLAPSE_AND_PASS) {
            return super.dispatchTouchEvent(event);
        }
        // 누르기: 지도에는 뗌 대신 취소를 보내서 마커를 누른 것으로 보지 않게 한다.
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        return true;
    }
}
