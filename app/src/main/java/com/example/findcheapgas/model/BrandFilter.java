package com.example.findcheapgas.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 지도 위 브랜드 빠른 필터. 아무것도 고르지 않으면 전체다.
 * "할인 브랜드만", "선호 브랜드만", 브랜드별 체크를 함께 고를 수 있고, 고른 것 중 하나라도 맞으면 보여준다.
 *
 * 고른 값은 그대로 저장해 두고, 화면에 적용할 때는 effective()로 지금 브랜드 설정에 맞게 정리한다.
 * - 숨긴 브랜드 체크는 무시한다 (숨긴 브랜드는 메뉴에도 나오지 않는다).
 * - 할인을 설정한 브랜드가 없으면 "할인 브랜드만"을, 선호 브랜드가 없으면 "선호 브랜드만"을 무시한다 (메뉴에서 비활성화).
 * - 정리한 결과 남는 것이 없으면 전체다.
 */
public final class BrandFilter {

    public static final BrandFilter ALL = new BrandFilter(false, false, Collections.emptySet());

    private final boolean discountOnly;
    private final boolean preferredOnly;
    private final Set<String> brandCodes;   // Brand.codes() 순서

    public BrandFilter(boolean discountOnly, boolean preferredOnly, Set<String> brandCodes) {
        this.discountOnly = discountOnly;
        this.preferredOnly = preferredOnly;
        Set<String> ordered = new LinkedHashSet<>();
        for (String code : Brand.codes()) {
            if (brandCodes.contains(code)) {
                ordered.add(code);
            }
        }
        this.brandCodes = Collections.unmodifiableSet(ordered);
    }

    public boolean isAll() {
        return !discountOnly && !preferredOnly && brandCodes.isEmpty();
    }

    public boolean isDiscountOnly() {
        return discountOnly;
    }

    public boolean isPreferredOnly() {
        return preferredOnly;
    }

    public Set<String> getBrandCodes() {
        return brandCodes;
    }

    public boolean contains(String brandCode) {
        return brandCodes.contains(brandCode);
    }

    public BrandFilter withDiscountOnly(boolean value) {
        return new BrandFilter(value, preferredOnly, brandCodes);
    }

    public BrandFilter withPreferredOnly(boolean value) {
        return new BrandFilter(discountOnly, value, brandCodes);
    }

    public BrandFilter withBrand(String brandCode, boolean checked) {
        Set<String> codes = new LinkedHashSet<>(brandCodes);
        if (checked) {
            codes.add(brandCode);
        } else {
            codes.remove(brandCode);
        }
        return new BrandFilter(discountOnly, preferredOnly, codes);
    }

    /** 지금 브랜드 설정으로 실제 적용할 필터 (클래스 설명 참고) */
    public BrandFilter effective(BrandSettings settings) {
        Set<String> codes = new LinkedHashSet<>();
        for (String code : brandCodes) {
            if (!settings.isHidden(code)) {
                codes.add(code);
            }
        }
        return new BrandFilter(discountOnly && settings.discountCount() > 0,
                preferredOnly && settings.preferredCount() > 0, codes);
    }

    /** effective()를 거친 필터에 쓴다. 주유소 브랜드가 고른 것 중 하나에 맞으면 true */
    public boolean matches(GasStation station, BrandSettings settings) {
        if (isAll()) {
            return true;
        }
        String code = Brand.normalize(station.getBrandCode());
        return brandCodes.contains(code)
                || (discountOnly && settings.hasDiscount(code))
                || (preferredOnly && settings.isPreferred(code));
    }

    /**
     * 칩에 쓰는 문구. effective()를 거친 필터에 쓴다.
     * 전체: "브랜드: 전체", 하나: "할인 브랜드", "선호 브랜드", "SK", 여럿: "SK 외 2"
     */
    public String label() {
        List<String> parts = new ArrayList<>();
        if (discountOnly) {
            parts.add("할인 브랜드");
        }
        if (preferredOnly) {
            parts.add("선호 브랜드");
        }
        for (String code : brandCodes) {
            parts.add(Brand.shortNameOf(code));
        }
        if (parts.isEmpty()) {
            return "브랜드: 전체";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return parts.get(0) + " 외 " + (parts.size() - 1);
    }

    /**
     * 지도 필터 줄의 짧은 칩 문구. effective()를 거친 필터에 쓴다.
     * 전체: "브랜드", 하나: "할인 브랜드", "선호 브랜드", "SK", 여럿: "브랜드 3" (고른 개수)
     * 무엇을 골랐는지는 label()로 접근성 설명에 넣는다.
     */
    public String chipLabel() {
        int count = brandCodes.size() + (discountOnly ? 1 : 0) + (preferredOnly ? 1 : 0);
        if (count == 0) {
            return "브랜드";
        }
        if (count == 1) {
            return label();
        }
        return "브랜드 " + count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BrandFilter)) {
            return false;
        }
        BrandFilter other = (BrandFilter) o;
        return discountOnly == other.discountOnly && preferredOnly == other.preferredOnly
                && brandCodes.equals(other.brandCodes);
    }

    @Override
    public int hashCode() {
        return (discountOnly ? 1 : 0) + (preferredOnly ? 2 : 0) + 31 * brandCodes.hashCode();
    }
}
