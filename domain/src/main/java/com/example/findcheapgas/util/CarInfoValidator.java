package com.example.findcheapgas.util;

import com.example.findcheapgas.model.CarInfo;

import java.math.BigDecimal;

/**
 * 내 차 정보 입력칸의 글자를 검사하고 숫자로 바꾼다.
 * - 연비: 3~40km/L, 소수점 한 자리까지 (12, 12.5, 12.50 허용 / 12.55 거부)
 * - 주유량: 5~150L, 정수 (40, 40.0 허용 / 40.5 거부)
 * 범위를 먼저 보고, 범위 안이면 자릿수를 본다.
 */
public final class CarInfoValidator {

    public enum Error {
        EMPTY,
        NOT_NUMBER,
        OUT_OF_RANGE,
        TOO_MANY_DECIMALS,  // 연비: 소수점 둘째 자리 이상
        NOT_INTEGER         // 주유량: 소수
    }

    /** 검사 결과. 통과하면 error가 null이고 value에 값이 들어 있다. */
    public static final class Result {
        private final int value;
        private final Error error;

        private Result(int value, Error error) {
            this.value = value;
            this.error = error;
        }

        private static Result ok(int value) {
            return new Result(value, null);
        }

        private static Result fail(Error error) {
            return new Result(0, error);
        }

        public boolean isValid() {
            return error == null;
        }

        /** 연비는 0.1km/L 단위, 주유량은 L 단위 */
        public int getValue() {
            return value;
        }

        public Error getError() {
            return error;
        }
    }

    private static final BigDecimal MIN_FUEL_EFFICIENCY =
            BigDecimal.valueOf(CarInfo.MIN_FUEL_EFFICIENCY_TENTHS, 1);
    private static final BigDecimal MAX_FUEL_EFFICIENCY =
            BigDecimal.valueOf(CarInfo.MAX_FUEL_EFFICIENCY_TENTHS, 1);
    private static final BigDecimal MIN_FUEL_AMOUNT = BigDecimal.valueOf(CarInfo.MIN_FUEL_AMOUNT);
    private static final BigDecimal MAX_FUEL_AMOUNT = BigDecimal.valueOf(CarInfo.MAX_FUEL_AMOUNT);

    private CarInfoValidator() {
    }

    /** "12.5" → 125 (0.1km/L 단위) */
    public static Result parseFuelEfficiency(String text) {
        String s = normalize(text);
        Error error = checkNumber(s);
        if (error != null) {
            return Result.fail(error);
        }
        BigDecimal number = new BigDecimal(s);
        if (number.compareTo(MIN_FUEL_EFFICIENCY) < 0 || number.compareTo(MAX_FUEL_EFFICIENCY) > 0) {
            return Result.fail(Error.OUT_OF_RANGE);
        }
        if (number.stripTrailingZeros().scale() > 1) {
            return Result.fail(Error.TOO_MANY_DECIMALS);
        }
        return Result.ok(number.movePointRight(1).intValue());
    }

    /** "40" → 40 (L) */
    public static Result parseFuelAmount(String text) {
        String s = normalize(text);
        Error error = checkNumber(s);
        if (error != null) {
            return Result.fail(error);
        }
        BigDecimal number = new BigDecimal(s);
        if (number.compareTo(MIN_FUEL_AMOUNT) < 0 || number.compareTo(MAX_FUEL_AMOUNT) > 0) {
            return Result.fail(Error.OUT_OF_RANGE);
        }
        if (number.stripTrailingZeros().scale() > 0) {
            return Result.fail(Error.NOT_INTEGER);
        }
        return Result.ok(number.intValue());
    }

    /** 앞뒤 공백을 지운다. 쉼표는 소수점으로 본다 (일부 키보드). */
    private static String normalize(String text) {
        return text == null ? "" : text.trim().replace(',', '.');
    }

    /** 숫자와 소수점 하나만 받는다. 통과하면 null */
    private static Error checkNumber(String s) {
        if (s.isEmpty()) {
            return Error.EMPTY;
        }
        if (!s.matches("\\d*\\.?\\d*") || s.equals(".")) {
            return Error.NOT_NUMBER;
        }
        return null;
    }
}
