package com.example.findcheapgas.model;

/**
 * 주유소마다 어떤 할인 조건을 적용할지 정한다.
 * 화면에서는 내 브랜드 설정(BrandSettings)을 쓴다. NONE(모든 브랜드 할인 없음)은 테스트와 기본값용이다.
 */
public interface DiscountPolicy {

    DiscountPolicy NONE = station -> DiscountRule.NONE;

    DiscountRule ruleFor(GasStation station);
}
