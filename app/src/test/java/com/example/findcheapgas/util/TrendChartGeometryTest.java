package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrendChartGeometryTest {

    private static final float LEFT = 10;
    private static final float TOP = 20;
    private static final float RIGHT = 310;
    private static final float BOTTOM = 120;

    @Test
    public void xIsEvenlySpaced() {
        TrendChartGeometry geometry = TrendChartGeometry.compute(
                new double[]{1656, 1658, 1661, 1661, 1664, 1666, 1668}, LEFT, TOP, RIGHT, BOTTOM);
        assertEquals(7, geometry.size());
        assertEquals(LEFT, geometry.x(0), 1e-3);
        assertEquals(60, geometry.x(1), 1e-3);
        assertEquals(RIGHT, geometry.x(6), 1e-3);
    }

    @Test
    public void yHasPaddingAroundMinAndMax() {
        // 범위 12원 → 여유 max(3, 5) = 5원 → 축 1651~1673
        TrendChartGeometry geometry = TrendChartGeometry.compute(
                new double[]{1656, 1660, 1668}, LEFT, TOP, RIGHT, BOTTOM);
        assertEquals(1651, geometry.getAxisMin(), 1e-9);
        assertEquals(1673, geometry.getAxisMax(), 1e-9);
        // 높은 값일수록 위(작은 y). 최댓값과 최솟값은 영역 끝에 붙지 않는다.
        assertTrue(geometry.y(2) < geometry.y(0));
        assertTrue(geometry.y(2) > TOP);
        assertTrue(geometry.y(0) < BOTTOM);
        assertEquals(TOP + 5.0 / 22 * 100, geometry.y(2), 1e-3);
    }

    @Test
    public void largeRangeUsesRatioPadding() {
        TrendChartGeometry geometry = TrendChartGeometry.compute(new double[]{1600, 1700}, LEFT, TOP, RIGHT, BOTTOM);
        assertEquals(1575, geometry.getAxisMin(), 1e-9);
        assertEquals(1725, geometry.getAxisMax(), 1e-9);
    }

    @Test
    public void labelsMaxMinAndLast() {
        TrendChartGeometry geometry = TrendChartGeometry.compute(
                new double[]{1660, 1670, 1650, 1655, 1658}, LEFT, TOP, RIGHT, BOTTOM);
        assertEquals(1, geometry.getMaxIndex());
        assertEquals(2, geometry.getMinIndex());
        assertEquals(4, geometry.getLastIndex());
        assertFalse(geometry.isLabeled(0));
        assertTrue(geometry.isLabeled(1));
        assertTrue(geometry.isLabeled(2));
        assertFalse(geometry.isLabeled(3));
        assertTrue(geometry.isLabeled(4));
        assertTrue(geometry.isLabelBelow(2));
        assertFalse(geometry.isLabelBelow(1));
        assertFalse(geometry.isLabelBelow(4));
    }

    @Test
    public void lastIsMin_labelBelowOnce() {
        TrendChartGeometry geometry = TrendChartGeometry.compute(
                new double[]{1534, 1532, 1529}, LEFT, TOP, RIGHT, BOTTOM);
        assertEquals(2, geometry.getMinIndex());
        assertTrue(geometry.isLabelBelow(2));
        assertTrue(geometry.isLabeled(0));
        assertFalse(geometry.isLabeled(1));
    }

    @Test
    public void allSame_onlyLastLabeled_middleOfAxis() {
        TrendChartGeometry geometry = TrendChartGeometry.compute(
                new double[]{1004, 1004, 1004}, LEFT, TOP, RIGHT, BOTTOM);
        assertFalse(geometry.isLabeled(0));
        assertFalse(geometry.isLabeled(1));
        assertTrue(geometry.isLabeled(2));
        assertFalse(geometry.isLabelBelow(2));
        assertEquals((TOP + BOTTOM) / 2, geometry.y(0), 1e-3);
    }

    @Test
    public void singlePointIsCentered() {
        TrendChartGeometry geometry = TrendChartGeometry.compute(new double[]{1668}, LEFT, TOP, RIGHT, BOTTOM);
        assertEquals((LEFT + RIGHT) / 2, geometry.x(0), 1e-3);
    }
}
