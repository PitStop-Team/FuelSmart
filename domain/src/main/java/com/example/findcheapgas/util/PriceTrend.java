package com.example.findcheapgas.util;

import androidx.annotation.Nullable;

import com.example.findcheapgas.model.DailyAveragePrice;
import com.example.findcheapgas.model.FuelType;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * 최근 7일 전국 평균가 추이. 그래프와 요약 문장에 쓸 값만 계산하고, 앞으로의 값은 추정하지 않는다.
 * 금액은 화면에 원 단위로 반올림해 보여주므로 비교도 반올림한 값끼리 한다.
 */
public final class PriceTrend {

    /** 그래프에 그리는 최대 일수 */
    public static final int MAX_DAYS = 7;
    /** 이보다 적으면 그래프 대신 안내 문구를 보여준다. */
    public static final int MIN_POINTS = 2;

    private PriceTrend() {
    }

    /** 그 유종의 날짜별 평균가. 날짜 오름차순, 같은 날짜는 처음 것만, 최근 7일까지 */
    public static List<DailyAveragePrice> forFuel(List<DailyAveragePrice> all, FuelType fuelType) {
        List<DailyAveragePrice> result = new ArrayList<>();
        Set<String> dates = new HashSet<>();
        for (DailyAveragePrice price : all) {
            if (price.getFuelType() == fuelType && dates.add(price.getDate())) {
                result.add(price);
            }
        }
        Collections.sort(result, Comparator.comparing(DailyAveragePrice::getDate));
        if (result.size() > MAX_DAYS) {
            result = new ArrayList<>(result.subList(result.size() - MAX_DAYS, result.size()));
        }
        return result;
    }

    /** 요약. 점이 2개 미만이면 null */
    @Nullable
    public static Summary summarize(List<DailyAveragePrice> points) {
        if (points.size() < MIN_POINTS) {
            return null;
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (DailyAveragePrice point : points) {
            long won = Math.round(point.getPrice());
            min = Math.min(min, won);
            max = Math.max(max, won);
        }
        DailyAveragePrice first = points.get(0);
        DailyAveragePrice last = points.get(points.size() - 1);
        long firstWon = Math.round(first.getPrice());
        long lastWon = Math.round(last.getPrice());
        return new Summary(first.getDate(), last.getDate(), daysBetween(first.getDate(), last.getDate()),
                (int) (lastWon - firstWon), (int) min, (int) max, (int) lastWon);
    }

    /** "20261031", "20261106" → 6. 날짜를 읽을 수 없으면 점 개수와 관계없이 -1 */
    static int daysBetween(String from, String to) {
        Calendar start = calendar(from);
        Calendar end = calendar(to);
        if (start == null || end == null) {
            return -1;
        }
        long diff = end.getTimeInMillis() - start.getTimeInMillis();
        return (int) Math.round(diff / (24.0 * 60 * 60 * 1000));
    }

    @Nullable
    private static Calendar calendar(String date) {
        if (date == null || !date.matches("\\d{8}")) {
            return null;
        }
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(Integer.parseInt(date.substring(0, 4)), Integer.parseInt(date.substring(4, 6)) - 1,
                Integer.parseInt(date.substring(6, 8)));
        return calendar;
    }

    /** 추이 요약 (원 단위) */
    public static final class Summary {
        private final String firstDate;
        private final String lastDate;
        private final int days;
        private final int change;
        private final int min;
        private final int max;
        private final int last;

        Summary(String firstDate, String lastDate, int days, int change, int min, int max, int last) {
            this.firstDate = firstDate;
            this.lastDate = lastDate;
            this.days = days;
            this.change = change;
            this.min = min;
            this.max = max;
            this.last = last;
        }

        /** 첫날 "20261031" */
        public String getFirstDate() {
            return firstDate;
        }

        public String getLastDate() {
            return lastDate;
        }

        /** 첫날부터 마지막 날까지 며칠인지. 날짜를 읽을 수 없으면 -1 */
        public int getDays() {
            return days;
        }

        /** 마지막 날 − 첫날 (원). 양수면 올랐고 음수면 내렸다. */
        public int getChange() {
            return change;
        }

        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }

        public int getLast() {
            return last;
        }
    }
}
