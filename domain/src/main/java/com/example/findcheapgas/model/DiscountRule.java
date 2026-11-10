package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

import java.io.Serializable;

/**
 * 한 브랜드의 할인 조건: 리터당 할인(원/L)과 1회 할인 한도(원).
 * 한도를 입력하지 않았으면 cap이 null이고, 한도 없이 계산한다 (화면에는 "한도 미적용"으로 표시).
 * 값은 내 브랜드 설정(BrandSetting)에서 채운다. 설정하지 않은 브랜드는 NONE이다.
 */
public final class DiscountRule implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final DiscountRule NONE = new DiscountRule(0, null);

    private final int perLiter;
    @Nullable
    private final Integer cap;

    /** perLiter: 리터당 할인(원, 0 이상), cap: 1회 할인 한도(원, 0 이상). 한도가 없으면 null */
    public DiscountRule(int perLiter, @Nullable Integer cap) {
        if (perLiter < 0 || (cap != null && cap < 0)) {
            throw new IllegalArgumentException("할인과 한도는 0 이상이어야 합니다: " + perLiter + ", " + cap);
        }
        this.perLiter = perLiter;
        this.cap = cap;
    }

    public int getPerLiter() {
        return perLiter;
    }

    @Nullable
    public Integer getCap() {
        return cap;
    }

    /** 할인이 설정되어 있는지 (리터당 할인이 0보다 큰지) */
    public boolean hasDiscount() {
        return perLiter > 0;
    }

    public boolean isCapEntered() {
        return cap != null;
    }

    /** 한도를 넘지 않게 계산하기 전 할인액: 리터당 할인 × 주유량 */
    public double uncappedAmount(double liters) {
        return perLiter * liters;
    }

    /** 할인액 = min(리터당 할인 × 주유량, 1회 할인 한도). 한도가 없으면 리터당 할인 × 주유량 */
    public double amountFor(double liters) {
        double amount = uncappedAmount(liters);
        return cap != null ? Math.min(amount, cap) : amount;
    }

    /** 한도 때문에 할인액이 줄었는지 */
    public boolean isCapAppliedFor(double liters) {
        return cap != null && uncappedAmount(liters) > cap;
    }
}
