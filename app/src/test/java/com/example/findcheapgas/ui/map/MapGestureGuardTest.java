package com.example.findcheapgas.ui.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.ui.map.MapGestureGuard.Result;
import com.example.findcheapgas.ui.map.MapGestureGuard.SheetPosition;

import org.junit.Test;

/** 시트를 올린 동안 지도 누르기·끌기 처리 */
public class MapGestureGuardTest {

    private static final float SLOP = 8f;

    @Test
    public void guards_onlyExpandedOrMoving() {
        assertTrue(MapGestureGuard.guards(SheetPosition.EXPANDED));
        assertTrue(MapGestureGuard.guards(SheetPosition.MOVING));
        assertFalse(MapGestureGuard.guards(SheetPosition.COLLAPSED));
        assertFalse(MapGestureGuard.guards(SheetPosition.HIDDEN));
    }

    @Test
    public void collapsedSheet_tapGoesToMap() {
        MapGestureGuard guard = new MapGestureGuard();
        guard.onDown(SheetPosition.COLLAPSED);

        assertEquals(Result.PASS, guard.onMove(2f, 1f, SLOP));
        assertEquals(Result.PASS, guard.onUp());
    }

    @Test
    public void hiddenForKeyboard_tapGoesToMap() {
        MapGestureGuard guard = new MapGestureGuard();
        guard.onDown(SheetPosition.HIDDEN);

        assertEquals(Result.PASS, guard.onUp());
    }

    @Test
    public void expandedSheet_tapOnlyCollapses() {
        MapGestureGuard guard = new MapGestureGuard();
        guard.onDown(SheetPosition.EXPANDED);

        // 손가락이 조금 흔들린 정도는 누르기다.
        assertEquals(Result.PASS, guard.onMove(3f, -4f, SLOP));
        assertEquals(Result.COLLAPSE_AND_CANCEL, guard.onUp());
    }

    @Test
    public void movingSheet_tapOnlyCollapses() {
        MapGestureGuard guard = new MapGestureGuard();
        guard.onDown(SheetPosition.MOVING);

        assertEquals(Result.COLLAPSE_AND_CANCEL, guard.onUp());
    }

    @Test
    public void expandedSheet_dragCollapsesOnceAndMovesMap() {
        MapGestureGuard guard = new MapGestureGuard();
        guard.onDown(SheetPosition.EXPANDED);

        assertEquals(Result.COLLAPSE_AND_PASS, guard.onMove(0f, 20f, SLOP));
        assertEquals(Result.PASS, guard.onMove(0f, 60f, SLOP));
        assertEquals(Result.PASS, guard.onUp());
    }

    @Test
    public void expandedSheet_secondFingerIsDrag() {
        MapGestureGuard guard = new MapGestureGuard();
        guard.onDown(SheetPosition.EXPANDED);

        assertEquals(Result.COLLAPSE_AND_PASS, guard.onSecondPointer());
        assertEquals(Result.PASS, guard.onUp());
    }

    @Test
    public void cancelledGesture_doesNothingOnUp() {
        MapGestureGuard guard = new MapGestureGuard();
        guard.onDown(SheetPosition.EXPANDED);
        guard.onCancel();

        assertEquals(Result.PASS, guard.onUp());
    }

    @Test
    public void eachGestureUsesSheetStateAtDown() {
        MapGestureGuard guard = new MapGestureGuard();
        guard.onDown(SheetPosition.EXPANDED);
        assertEquals(Result.COLLAPSE_AND_CANCEL, guard.onUp());

        // 시트를 내린 뒤의 다음 누름은 지도가 받는다 (마커 선택).
        guard.onDown(SheetPosition.COLLAPSED);
        assertEquals(Result.PASS, guard.onUp());
    }
}
