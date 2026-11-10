package com.example.findcheapgas.util;

import androidx.annotation.Nullable;

import com.example.findcheapgas.model.BrandSetting;

import java.math.BigDecimal;

/**
 * 내 브랜드 수정 화면 입력칸의 글자를 검사하고 숫자로 바꾼다.
 * - 리터당 할인: 0~500원, 정수. 비우면 0 (할인 없음)
 * - 1회 할인 한도: 0~100,000원, 정수. 비우면 한도 없음 (화면에는 "한도 미적용")
 * 숫자 사이의 쉼표(3,000)는 지우고 읽는다. 범위를 먼저 보고, 범위 안이면 정수인지 본다.
 */
public final class BrandSettingValidator {

    public enum Error {
        NOT_NUMBER,
        OUT_OF_RANGE,
        NOT_INTEGER
    }

    /** 검사 결과. 통과하면 error가 null이다. 비워 둔 한도는 value가 null이다. */
    public static final class Result {
        @Nullable
        private final Integer value;
        @Nullable
        private final Error error;

        private Result(@Nullable Integer value, @Nullable Error error) {
            this.value = value;
            this.error = error;
        }

        public boolean isValid() {
            return error == null;
        }

        @Nullable
        public Integer getValue() {
            return value;
        }

        @Nullable
        public Error getError() {
            return error;
        }
    }

    private BrandSettingValidator() {
    }

    /** "100" → 100, "" → 0 */
    public static Result parsePerLiter(String text) {
        Result result = parse(text, BrandSetting.MAX_PER_LITER);
        if (result.isValid() && result.getValue() == null) {
            return new Result(0, null);
        }
        return result;
    }

    /** "3000", "3,000" → 3000, "" → null (한도 없음) */
    public static Result parseCap(String text) {
        return parse(text, BrandSetting.MAX_CAP);
    }

    private static Result parse(String text, int max) {
        String s = text == null ? "" : text.trim().replace(",", "");
        if (s.isEmpty()) {
            return new Result(null, null);
        }
        if (!s.matches("\\d*\\.?\\d*") || s.equals(".")) {
            return new Result(null, Error.NOT_NUMBER);
        }
        BigDecimal number = new BigDecimal(s);
        if (number.compareTo(BigDecimal.valueOf(max)) > 0) {
            return new Result(null, Error.OUT_OF_RANGE);
        }
        if (number.stripTrailingZeros().scale() > 0) {
            return new Result(null, Error.NOT_INTEGER);
        }
        return new Result(number.intValue(), null);
    }
}
