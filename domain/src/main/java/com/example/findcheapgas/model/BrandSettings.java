package com.example.findcheapgas.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 브랜드 전체의 내 설정. 바꿀 때마다 새 객체를 만든다.
 * 할인 정책(DiscountPolicy)으로도 쓴다: 주유소 브랜드의 리터당 할인과 1회 할인 한도를 돌려준다.
 * 브랜드 코드가 없거나 표에 없는 주유소는 자가상표(Brand.OTHER) 설정을 따른다.
 */
public final class BrandSettings implements DiscountPolicy {

    public static final BrandSettings EMPTY = new BrandSettings(Collections.emptyMap());

    // 설정한 브랜드만 들어 있다. 없으면 BrandSetting.NONE
    private final Map<String, BrandSetting> settings;

    public BrandSettings(Map<String, BrandSetting> settings) {
        Map<String, BrandSetting> copy = new HashMap<>();
        for (Map.Entry<String, BrandSetting> entry : settings.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                copy.put(Brand.normalize(entry.getKey()), entry.getValue());
            }
        }
        this.settings = Collections.unmodifiableMap(copy);
    }

    public BrandSetting get(String brandCode) {
        BrandSetting setting = settings.get(Brand.normalize(brandCode));
        return setting != null ? setting : BrandSetting.NONE;
    }

    /** 한 브랜드만 바꾼 새 설정 */
    public BrandSettings with(String brandCode, BrandSetting setting) {
        Map<String, BrandSetting> copy = new HashMap<>(settings);
        copy.put(Brand.normalize(brandCode), setting);
        return new BrandSettings(copy);
    }

    @Override
    public DiscountRule ruleFor(GasStation station) {
        return get(station.getBrandCode()).getDiscountRule();
    }

    public boolean isPreferred(String brandCode) {
        return get(brandCode).isPreferred();
    }

    public boolean isHidden(String brandCode) {
        return get(brandCode).isHidden();
    }

    public boolean hasDiscount(String brandCode) {
        return get(brandCode).hasDiscount();
    }

    /** 할인을 설정한 브랜드 수 (리터당 할인이 0보다 큰 곳) */
    public int discountCount() {
        int count = 0;
        for (BrandSetting setting : settings.values()) {
            if (setting.hasDiscount()) {
                count++;
            }
        }
        return count;
    }

    public int preferredCount() {
        int count = 0;
        for (BrandSetting setting : settings.values()) {
            if (setting.isPreferred()) {
                count++;
            }
        }
        return count;
    }

    public int hiddenCount() {
        int count = 0;
        for (BrandSetting setting : settings.values()) {
            if (setting.isHidden()) {
                count++;
            }
        }
        return count;
    }
}
