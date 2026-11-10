package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.findcheapgas.model.DailyAveragePrice;
import com.example.findcheapgas.model.FuelType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PriceTrendTest {

    private static DailyAveragePrice gasoline(String date, double price) {
        return new DailyAveragePrice(date, FuelType.GASOLINE, price);
    }

    private static List<DailyAveragePrice> week() {
        return Arrays.asList(
                gasoline("20261031", 1656.18),
                gasoline("20261101", 1658.04),
                gasoline("20261102", 1661.37),
                gasoline("20261103", 1660.92),
                gasoline("20261104", 1663.80),
                gasoline("20261105", 1666.25),
                gasoline("20261106", 1668.42));
    }

    @Test
    public void forFuel_filtersSortsAndDedupes() {
        List<DailyAveragePrice> all = new ArrayList<>(week());
        Collections.reverse(all);
        all.add(new DailyAveragePrice("20261101", FuelType.DIESEL, 1534.10));
        all.add(gasoline("20261106", 9999)); // 같은 날짜는 처음 것

        List<DailyAveragePrice> result = PriceTrend.forFuel(all, FuelType.GASOLINE);
        assertEquals(7, result.size());
        assertEquals("20261031", result.get(0).getDate());
        assertEquals(1668.42, result.get(6).getPrice(), 1e-9);
    }

    @Test
    public void forFuel_keepsLastSevenDays() {
        List<DailyAveragePrice> all = new ArrayList<>(week());
        all.add(gasoline("20261030", 1650));
        List<DailyAveragePrice> result = PriceTrend.forFuel(all, FuelType.GASOLINE);
        assertEquals(7, result.size());
        assertEquals("20261031", result.get(0).getDate());
    }

    @Test
    public void summarize_rose() {
        PriceTrend.Summary summary = PriceTrend.summarize(week());
        assertEquals(6, summary.getDays());
        assertEquals(12, summary.getChange()); // 1668 − 1656
        assertEquals(1656, summary.getMin());
        assertEquals(1668, summary.getMax());
        assertEquals(1668, summary.getLast());
        assertEquals("6일 전(10/31)보다 약 12원 올랐어요", FormatUtil.formatTrendChange(summary));
        assertEquals("최근 7일 전국 평균 휘발유, 최저 1,656원, 최고 1,668원, 마지막 1,668원",
                FormatUtil.formatTrendDescription(FuelType.GASOLINE, summary));
    }

    @Test
    public void summarize_fellAndSame() {
        PriceTrend.Summary fell = PriceTrend.summarize(Arrays.asList(
                gasoline("20261103", 1534.92), gasoline("20261106", 1528.74)));
        assertEquals(3, fell.getDays());
        assertEquals("3일 전(11/3)보다 약 6원 내렸어요", FormatUtil.formatTrendChange(fell));

        PriceTrend.Summary same = PriceTrend.summarize(Arrays.asList(
                gasoline("20261031", 1004.31), gasoline("20261106", 1004.18)));
        assertEquals("6일 전(10/31)과 같아요", FormatUtil.formatTrendChange(same));
    }

    @Test
    public void summarize_acrossMonthAndYear() {
        PriceTrend.Summary summary = PriceTrend.summarize(Arrays.asList(
                gasoline("20261228", 1700), gasoline("20270103", 1690)));
        assertEquals(6, summary.getDays());
        assertEquals("6일 전(12/28)보다 약 10원 내렸어요", FormatUtil.formatTrendChange(summary));
    }

    @Test
    public void summarize_needsTwoPoints() {
        assertNull(PriceTrend.summarize(Collections.singletonList(gasoline("20261106", 1668))));
        assertNull(PriceTrend.summarize(Collections.<DailyAveragePrice>emptyList()));
    }

    @Test
    public void shortDate() {
        assertEquals("11/1", FormatUtil.formatShortDate("20261101"));
        assertEquals("12/31", FormatUtil.formatShortDate("20261231"));
        assertEquals("2026-11-01", FormatUtil.formatShortDate("2026-11-01"));
    }
}
