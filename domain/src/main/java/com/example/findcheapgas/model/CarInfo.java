package com.example.findcheapgas.model;

import java.io.Serializable;

/**
 * 내 차 정보: 연비와 평소 주유량, 사용자가 직접 입력했는지 여부.
 * 입력하지 않았으면 DEFAULT(연비 12km/L, 40L)를 쓴다. 직접 12km/L, 40L를 입력해도 "직접 입력"으로 본다.
 * 연비는 소수점 한 자리까지만 받으므로 0.1km/L 단위 정수(12.5km/L → 125)로 들고 있다.
 */
public final class CarInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_FUEL_EFFICIENCY_TENTHS = 120;
    public static final int DEFAULT_FUEL_AMOUNT = 40;

    // 입력 범위 (양 끝 포함)
    public static final int MIN_FUEL_EFFICIENCY_TENTHS = 30;
    public static final int MAX_FUEL_EFFICIENCY_TENTHS = 400;
    public static final int MIN_FUEL_AMOUNT = 5;
    public static final int MAX_FUEL_AMOUNT = 150;

    public static final CarInfo DEFAULT =
            new CarInfo(DEFAULT_FUEL_EFFICIENCY_TENTHS, DEFAULT_FUEL_AMOUNT, false);

    private final int fuelEfficiencyTenths;
    private final int fuelAmount;
    private final boolean userEntered;

    private CarInfo(int fuelEfficiencyTenths, int fuelAmount, boolean userEntered) {
        this.fuelEfficiencyTenths = fuelEfficiencyTenths;
        this.fuelAmount = fuelAmount;
        this.userEntered = userEntered;
    }

    /** 사용자가 입력한 값. 범위를 벗어나면 IllegalArgumentException (먼저 CarInfoValidator로 확인한다) */
    public static CarInfo userEntered(int fuelEfficiencyTenths, int fuelAmount) {
        if (!isInRange(fuelEfficiencyTenths, fuelAmount)) {
            throw new IllegalArgumentException("범위를 벗어난 내 차 정보: "
                    + fuelEfficiencyTenths + ", " + fuelAmount);
        }
        return new CarInfo(fuelEfficiencyTenths, fuelAmount, true);
    }

    /** 저장해 둔 값으로 만든다. 입력한 적이 없거나 저장된 값이 범위를 벗어나면 기본값 */
    public static CarInfo fromStored(boolean userEntered, int fuelEfficiencyTenths, int fuelAmount) {
        if (!userEntered || !isInRange(fuelEfficiencyTenths, fuelAmount)) {
            return DEFAULT;
        }
        return new CarInfo(fuelEfficiencyTenths, fuelAmount, true);
    }

    private static boolean isInRange(int fuelEfficiencyTenths, int fuelAmount) {
        return fuelEfficiencyTenths >= MIN_FUEL_EFFICIENCY_TENTHS
                && fuelEfficiencyTenths <= MAX_FUEL_EFFICIENCY_TENTHS
                && fuelAmount >= MIN_FUEL_AMOUNT
                && fuelAmount <= MAX_FUEL_AMOUNT;
    }

    /** 연비 (km/L) */
    public double getFuelEfficiency() {
        return fuelEfficiencyTenths / 10.0;
    }

    /** 연비 (0.1km/L 단위) */
    public int getFuelEfficiencyTenths() {
        return fuelEfficiencyTenths;
    }

    /** 평소 주유량 (L) */
    public int getFuelAmount() {
        return fuelAmount;
    }

    /** 사용자가 입력하지 않아 기본값으로 계산하는 중인지 */
    public boolean isDefault() {
        return !userEntered;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CarInfo)) {
            return false;
        }
        CarInfo other = (CarInfo) o;
        return fuelEfficiencyTenths == other.fuelEfficiencyTenths
                && fuelAmount == other.fuelAmount
                && userEntered == other.userEntered;
    }

    @Override
    public int hashCode() {
        int result = fuelEfficiencyTenths;
        result = 31 * result + fuelAmount;
        result = 31 * result + (userEntered ? 1 : 0);
        return result;
    }
}
