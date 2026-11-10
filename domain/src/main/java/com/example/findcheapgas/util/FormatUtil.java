package com.example.findcheapgas.util;

import com.example.findcheapgas.model.ApiUsage;
import com.example.findcheapgas.model.AverageComparison;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.DiscountRule;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.StationSaving;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** 가격, 계산 금액, 예상 이득, 할인·내 브랜드 요약, 예상 총비용과 차이, 가격을 가져온 시각, 오늘 오피넷 사용량, 거리, 연비·주유량, 계산 기준(위젯용 짧은 문구 포함), 위젯 갱신 시각, 가격 기준 일시, 평균가 비교, 7일 추이 요약·짧은 날짜 표시 형식 */
public final class FormatUtil {

    private FormatUtil() {
    }

    /** 1659 → "1,659원" */
    public static String formatPrice(int price) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(price) + "원";
    }

    /** 계산한 금액(실수)을 원 단위로 반올림해서 표시한다. 68099.17 → "68,099원" */
    public static String formatWon(double amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(Math.round(amount)) + "원";
    }

    /**
     * 주인공 숫자 문구 (지도 탭의 이득 1위 카드, 상세 화면 맨 위 카드). 과장하지 않도록 구간마다 다르게 쓴다.
     * - 1,000원 이상 이득: "약 2,583원 아낄 수 있어요"
     * - 1,000원 이상 손해: "약 1,200원 더 들어요"
     * - 그 사이: "가장 가까운 곳과 비슷해요". 이득 1위인데 0원 이하면(더 나은 곳이 없음) "가장 가까운 곳에서 넣는 게 좋아요"
     * - 기준 주유소 자신: 이득 1위면 "가장 가까운 곳에서 넣는 게 좋아요", 아니면 "가장 가까운 주유소예요"
     * 구간은 SavingLevel(±1,000원)을 따라서 숫자 색과 맞는다.
     */
    public static String formatSavingHeadline(StationSaving saving) {
        long won = saving.getSavingWon();
        if (saving.isBaseline()) {
            return saving.isBest() ? "가장 가까운 곳에서 넣는 게 좋아요" : "가장 가까운 주유소예요";
        }
        switch (saving.getLevel()) {
            case GAIN:
                return "약 " + formatWon(won) + " 아낄 수 있어요";
            case LOSS:
                return "약 " + formatWon(-won) + " 더 들어요";
            default:
                return saving.isBest() && won <= 0 ? "가장 가까운 곳에서 넣는 게 좋아요" : "가장 가까운 곳과 비슷해요";
        }
    }

    /**
     * 지도 탭 목록 한 줄의 가격 아래 설명. 짧게 쓰고 구간은 SavingLevel(±1,000원)을 따른다.
     * 이득: "약 2,583원 아껴요", 비슷: "비슷해요", 손해: "약 1,200원 더 들어요", 기준 주유소: "가장 가까운 곳"
     */
    public static String formatSavingNote(StationSaving saving) {
        if (saving.isBaseline()) {
            return "가장 가까운 곳";
        }
        long won = saving.getSavingWon();
        switch (saving.getLevel()) {
            case GAIN:
                return "약 " + formatWon(won) + " 아껴요";
            case LOSS:
                return "약 " + formatWon(-won) + " 더 들어요";
            default:
                return "비슷해요";
        }
    }

    /**
     * 목록과 상세 화면의 예상 이득 문구. 추정치이므로 "약"을 붙인다.
     * 기준 주유소: "기준 (가장 가까운 곳)", 이득: "약 1,925원 이득", 손해: "약 300원 손해",
     * 원 단위로 차이가 없으면 "기준과 거의 같음"
     */
    public static String formatSaving(StationSaving saving) {
        if (saving.isBaseline()) {
            return "기준 (가장 가까운 곳)";
        }
        long won = saving.getSavingWon();
        if (won == 0) {
            return "기준과 거의 같음";
        }
        String amount = formatWon(Math.abs(won));
        return won > 0 ? "약 " + amount + " 이득" : "약 " + amount + " 손해";
    }

    /**
     * 내 브랜드 목록 한 줄의 할인 요약.
     * "리터당 100원 할인 · 1회 한도 3,000원", 한도를 비워 두면 "리터당 100원 할인 · 한도 미적용", 할인이 없으면 "할인 없음"
     */
    public static String formatDiscountSummary(DiscountRule rule) {
        if (!rule.hasDiscount()) {
            return "할인 없음";
        }
        String perLiter = "리터당 " + formatPrice(rule.getPerLiter()) + " 할인";
        Integer cap = rule.getCap();
        return cap != null ? perLiter + " · 1회 한도 " + formatPrice(cap) : perLiter + " · 한도 미적용";
    }

    /** 설정 탭 "내 브랜드" 요약. 0곳인 항목은 뺀다. "할인 2곳 · 선호 1곳 · 숨김 1곳", 아무것도 없으면 "설정한 브랜드 없음" */
    public static String formatBrandSummary(BrandSettings settings) {
        List<String> parts = new ArrayList<>();
        if (settings.discountCount() > 0) {
            parts.add("할인 " + settings.discountCount() + "곳");
        }
        if (settings.preferredCount() > 0) {
            parts.add("선호 " + settings.preferredCount() + "곳");
        }
        if (settings.hiddenCount() > 0) {
            parts.add("숨김 " + settings.hiddenCount() + "곳");
        }
        if (parts.isEmpty()) {
            return "설정한 브랜드 없음";
        }
        // String.join은 API 26부터라서 직접 잇는다.
        StringBuilder summary = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            summary.append(" · ").append(parts.get(i));
        }
        return summary.toString();
    }

    /** 집·회사 기준 비교 카드의 예상 총비용. 66174.3 → "예상 총비용 약 66,174원" */
    public static String formatTotalCost(double totalCost) {
        return "예상 총비용 약 " + formatWon(totalCost);
    }

    /** 집·회사 기준 비교에서 가장 저렴한 위치보다 더 드는 금액. 1200 → "약 1,200원 더 들어요" (지도 탭 목록과 같은 말) */
    public static String formatExtraCost(long won) {
        return "약 " + formatWon(won) + " 더 들어요";
    }

    /** 추정 금액. 66174.3 → "약 66,174원" (집·회사 기준 비교의 예상 총비용) */
    public static String formatApproxWon(double amount) {
        return "약 " + formatWon(amount);
    }

    /**
     * 가격을 가져온 지 얼마나 됐는지. 1분 안이면 "방금 가져온 가격", 1시간 안이면 "약 25분 전 가격",
     * 그 뒤로는 시간 단위로 반올림해서 "약 1시간 전 가격". 시계가 거꾸로 가 있으면 "방금 가져온 가격"
     */
    public static String formatPriceAge(long fetchedAt, long now) {
        long minutes = Math.max(0, now - fetchedAt) / 60_000L;
        if (minutes < 1) {
            return "방금 가져온 가격";
        }
        if (minutes < 60) {
            return "약 " + minutes + "분 전 가격";
        }
        return "약 " + Math.round(minutes / 60.0) + "시간 전 가격";
    }

    /** 설정 탭 오늘 오피넷 사용량. "오늘 12회 / 300회" */
    public static String formatApiUsageCount(ApiUsage usage) {
        NumberFormat number = NumberFormat.getNumberInstance(Locale.KOREA);
        return "오늘 " + number.format(usage.getCount()) + "회 / " + number.format(usage.getLimit()) + "회";
    }

    /**
     * 설정 탭 오늘 오피넷 사용량의 상태 문구. 250회부터 "한도에 가까워졌어요",
     * 300회면 "오늘 한도에 도달했어요. 저장된 결과만 볼 수 있어요", 그 아래는 null
     */
    public static String formatApiUsageStatus(ApiUsage usage) {
        switch (usage.getLevel()) {
            case LIMIT_REACHED:
                return "오늘 한도에 도달했어요. 저장된 결과만 볼 수 있어요";
            case NEAR_LIMIT:
                return "한도에 가까워졌어요";
            default:
                return null;
        }
    }

    /** 1234.5(m) → "1.2km" */
    public static String formatDistance(double meters) {
        return String.format(Locale.KOREA, "%.1fkm", meters / 1000.0);
    }

    /** 0.1km/L 단위 연비 → 정수면 소수점 없이, 아니면 한 자리까지. 120 → "12", 125 → "12.5" */
    public static String formatFuelEfficiencyNumber(int tenths) {
        if (tenths % 10 == 0) {
            return String.valueOf(tenths / 10);
        }
        return (tenths / 10) + "." + (tenths % 10);
    }

    /** 120 → "12km/L", 125 → "12.5km/L" */
    public static String formatFuelEfficiency(int tenths) {
        return formatFuelEfficiencyNumber(tenths) + "km/L";
    }

    /** 40 → "40L" */
    public static String formatFuelAmount(int liters) {
        return liters + "L";
    }

    /**
     * 계산 기준 문구. 목록 위쪽, 상세 계산 내역에 쓴다.
     * 기본값: "연비 12km/L · 40L 기준 (기본값)", 직접 입력: "내 차 기준 · 연비 15km/L · 50L"
     */
    public static String formatCalculationBasis(CarInfo carInfo) {
        String efficiency = formatFuelEfficiency(carInfo.getFuelEfficiencyTenths());
        String amount = formatFuelAmount(carInfo.getFuelAmount());
        if (carInfo.isDefault()) {
            return "연비 " + efficiency + " · " + amount + " 기준 (기본값)";
        }
        return "내 차 기준 · 연비 " + efficiency + " · " + amount;
    }

    /**
     * 홈 화면 위젯의 짧은 계산 기준 문구.
     * 기본값: "12km/L · 40L (기본값)", 직접 입력: "11km/L · 50L"
     */
    public static String formatShortCalculationBasis(CarInfo carInfo) {
        String basis = formatFuelEfficiency(carInfo.getFuelEfficiencyTenths()) + " · "
                + formatFuelAmount(carInfo.getFuelAmount());
        return carInfo.isDefault() ? basis + " (기본값)" : basis;
    }

    /**
     * 홈 화면 위젯의 갱신 시각 (가격을 가져온 시각). 위젯은 몇 시간에 한 번만 다시 그려지므로
     * "약 2시간 전"처럼 지나면 틀려지는 문구 대신 시각을 쓴다.
     * 같은 날이면 "오후 3:20 갱신", 다른 날이면 "11월 5일 오후 3:20 갱신"
     */
    public static String formatUpdatedTime(long fetchedAt, long now, TimeZone timeZone) {
        Calendar fetched = Calendar.getInstance(timeZone, Locale.KOREA);
        fetched.setTimeInMillis(fetchedAt);
        Calendar today = Calendar.getInstance(timeZone, Locale.KOREA);
        today.setTimeInMillis(now);
        int hour = fetched.get(Calendar.HOUR_OF_DAY);
        int hour12 = hour % 12 == 0 ? 12 : hour % 12;
        String time = (hour < 12 ? "오전 " : "오후 ") + hour12 + ":"
                + String.format(Locale.US, "%02d", fetched.get(Calendar.MINUTE));
        boolean sameDay = fetched.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && fetched.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        if (!sameDay) {
            time = (fetched.get(Calendar.MONTH) + 1) + "월 " + fetched.get(Calendar.DAY_OF_MONTH) + "일 " + time;
        }
        return time + " 갱신";
    }

    /**
     * 오피넷 가격 기준 일시. ("20261104", "153000") → "11월 4일 15:30"
     * 시각을 읽을 수 없으면 날짜만, 날짜를 읽을 수 없으면 null을 돌려준다.
     */
    public static String formatTradeTime(String date, String time) {
        if (date == null || !date.matches("\\d{8}")) {
            return null;
        }
        int month = Integer.parseInt(date.substring(4, 6));
        int day = Integer.parseInt(date.substring(6, 8));
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        String result = month + "월 " + day + "일";
        if (time != null && time.matches("\\d{4,6}")) {
            int hour = Integer.parseInt(time.substring(0, 2));
            int minute = Integer.parseInt(time.substring(2, 4));
            if (hour < 24 && minute < 60) {
                result += String.format(Locale.KOREA, " %02d:%02d", hour, minute);
            }
        }
        return result;
    }

    /**
     * 평균가 비교 한 줄 (표시 가격 기준, 할인 전).
     * "전국 평균 1,712원보다 리터당 41원 저렴해요", "서울 평균 1,768원보다 리터당 12원 비싸요", "전국 평균 1,712원 · 평균과 같아요"
     */
    public static String formatAverageComparison(AverageComparison.Line line) {
        String average = line.getRegionLabel() + " 평균 " + formatPrice(line.getAverageWon());
        int difference = line.getDifferencePerLiter();
        if (difference == 0) {
            return average + " · 평균과 같아요";
        }
        return average + "보다 리터당 " + formatPrice(Math.abs(difference)) + (difference < 0 ? " 저렴해요" : " 비싸요");
    }

    /**
     * 평균가 비교 줄 아래의 내 주유량 환산. "40L 기준 약 1,640원 저렴해요", "40L 기준 약 480원 비싸요".
     * 차이가 없으면 null (보여주지 않는다).
     */
    public static String formatAverageForFuelAmount(AverageComparison.Line line) {
        int difference = line.getDifferenceForFuelAmount();
        if (difference == 0) {
            return null;
        }
        return formatFuelAmount(line.getFuelAmount()) + " 기준 약 " + formatPrice(Math.abs(difference))
                + (difference < 0 ? " 저렴해요" : " 비싸요");
    }

    /** 오피넷 날짜 "20261031" → "10/31". 읽을 수 없으면 그대로 */
    public static String formatShortDate(String date) {
        if (date == null || !date.matches("\\d{8}")) {
            return date;
        }
        return Integer.parseInt(date.substring(4, 6)) + "/" + Integer.parseInt(date.substring(6, 8));
    }

    /**
     * 7일 추이 요약 한 줄. 지난 사실만 적고 앞으로의 값은 말하지 않는다.
     * "6일 전(10/31)보다 약 12원 올랐어요", "6일 전(10/31)보다 약 6원 내렸어요", "6일 전(10/31)과 같아요"
     * 날짜를 읽을 수 없으면 "첫날(…)"로 쓴다.
     */
    public static String formatTrendChange(PriceTrend.Summary summary) {
        String since = (summary.getDays() > 0 ? summary.getDays() + "일 전" : "첫날")
                + "(" + formatShortDate(summary.getFirstDate()) + ")";
        int change = summary.getChange();
        if (change == 0) {
            return since + "과 같아요";
        }
        return since + "보다 약 " + formatPrice(Math.abs(change)) + (change > 0 ? " 올랐어요" : " 내렸어요");
    }

    /** 추이 그래프 접근성 설명. "최근 7일 전국 평균 휘발유, 최저 1,656원, 최고 1,668원, 마지막 1,668원" */
    public static String formatTrendDescription(FuelType fuelType, PriceTrend.Summary summary) {
        return "최근 7일 전국 평균 " + fuelType.getLabel()
                + ", 최저 " + formatPrice(summary.getMin())
                + ", 최고 " + formatPrice(summary.getMax())
                + ", 마지막 " + formatPrice(summary.getLast());
    }
}
