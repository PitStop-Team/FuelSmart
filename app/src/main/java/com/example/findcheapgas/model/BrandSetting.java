package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

/**
 * 브랜드 한 곳의 내 설정: 리터당 할인, 1회 할인 한도, 선호, 숨기기.
 * 선호와 숨기기는 동시에 켤 수 없다. withPreferred / withHidden은 하나를 켜면 다른 하나를 끈다.
 */
public final class BrandSetting {

    public static final int MAX_PER_LITER = 500;      // 리터당 할인 최대 (원)
    public static final int MAX_CAP = 100_000;        // 1회 할인 한도 최대 (원)

    public static final BrandSetting NONE = new BrandSetting(DiscountRule.NONE, false, false);

    private final DiscountRule discountRule;
    private final boolean preferred;
    private final boolean hidden;

    public BrandSetting(DiscountRule discountRule, boolean preferred, boolean hidden) {
        if (preferred && hidden) {
            throw new IllegalArgumentException("선호와 숨기기는 동시에 켤 수 없습니다.");
        }
        this.discountRule = discountRule;
        this.preferred = preferred;
        this.hidden = hidden;
    }

    /** perLiter: 리터당 할인(원), cap: 1회 할인 한도(원). 한도를 비워 두면 null */
    public BrandSetting(int perLiter, @Nullable Integer cap, boolean preferred, boolean hidden) {
        this(new DiscountRule(perLiter, cap), preferred, hidden);
    }

    public DiscountRule getDiscountRule() {
        return discountRule;
    }

    public boolean hasDiscount() {
        return discountRule.hasDiscount();
    }

    public boolean isPreferred() {
        return preferred;
    }

    public boolean isHidden() {
        return hidden;
    }

    /** 선호를 켜면 숨기기는 꺼진다. */
    public BrandSetting withPreferred(boolean newPreferred) {
        return new BrandSetting(discountRule, newPreferred, newPreferred ? false : hidden);
    }

    /** 숨기기를 켜면 선호는 꺼진다. */
    public BrandSetting withHidden(boolean newHidden) {
        return new BrandSetting(discountRule, newHidden ? false : preferred, newHidden);
    }

    /** 아무것도 설정하지 않은 상태인지 (저장할 필요 없음) */
    public boolean isEmpty() {
        return !discountRule.hasDiscount() && !discountRule.isCapEntered() && !preferred && !hidden;
    }
}
