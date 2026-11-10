package com.example.findcheapgas.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

public class BrandSettingsTest {

    private static GasStation station(String brand) {
        return new GasStation("id", brand, "name", 1700, 500, 0, 0, 0, 0);
    }

    @Test
    public void codes_coverAllBrandsInOrder() {
        assertEquals(Arrays.asList("SKE", "GSC", "HDO", "SOL", "RTE", "RTX", "NHO", "E1G", "SKG", "ETC"),
                Brand.codes());
    }

    @Test
    public void discountPolicy_returnsRuleOfStationBrand() {
        DiscountRule rule = new DiscountRule(100, 3_000);
        BrandSettings settings = BrandSettings.EMPTY.with("GSC", new BrandSetting(rule, false, false));
        assertSame(rule, settings.ruleFor(station("GSC")));
        assertSame(DiscountRule.NONE, settings.ruleFor(station("SKE")));
    }

    @Test
    public void unknownOrMissingBrand_usesOtherBrandSetting() {
        BrandSettings settings = BrandSettings.EMPTY.with(Brand.OTHER, new BrandSetting(50, null, false, false));
        assertEquals(50, settings.ruleFor(station(null)).getPerLiter());
        assertEquals(50, settings.ruleFor(station("XYZ")).getPerLiter());
    }

    @Test
    public void counts() {
        BrandSettings settings = BrandSettings.EMPTY
                .with("SKE", new BrandSetting(100, 3_000, true, false))
                .with("GSC", new BrandSetting(50, null, false, false))
                .with("SOL", new BrandSetting(0, null, false, true));
        assertEquals(2, settings.discountCount());
        assertEquals(1, settings.preferredCount());
        assertEquals(1, settings.hiddenCount());
    }

    @Test
    public void with_replacesOnlyThatBrand() {
        BrandSettings before = BrandSettings.EMPTY.with("SKE", new BrandSetting(100, null, false, false));
        BrandSettings after = before.with("SKE", BrandSetting.NONE);
        assertTrue(before.hasDiscount("SKE"));
        assertFalse(after.hasDiscount("SKE"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void preferredAndHidden_cannotBothBeOn() {
        new BrandSetting(0, null, true, true);
    }

    @Test
    public void turningOneOn_turnsOtherOff() {
        BrandSetting hidden = new BrandSetting(0, null, false, true);
        BrandSetting preferred = hidden.withPreferred(true);
        assertTrue(preferred.isPreferred());
        assertFalse(preferred.isHidden());
        BrandSetting hiddenAgain = preferred.withHidden(true);
        assertTrue(hiddenAgain.isHidden());
        assertFalse(hiddenAgain.isPreferred());
    }

    @Test
    public void brandFilter_effectiveDropsHiddenAndUnavailableChoices() {
        BrandSettings settings = BrandSettings.EMPTY.with("SKE", new BrandSetting(0, null, false, true));
        BrandFilter stored = new BrandFilter(true, true, new HashSet<>(Arrays.asList("SKE", "GSC")));
        BrandFilter effective = stored.effective(settings);
        // 할인·선호 브랜드가 없어서 두 선택지는 무시, 숨긴 SK에너지 체크도 무시
        assertFalse(effective.isDiscountOnly());
        assertFalse(effective.isPreferredOnly());
        assertEquals(new HashSet<>(Arrays.asList("GSC")), effective.getBrandCodes());
        assertTrue(BrandFilter.ALL.withBrand("SKE", true).effective(settings).isAll());
    }

    @Test
    public void brandFilter_labels() {
        assertEquals("브랜드: 전체", BrandFilter.ALL.label());
        assertEquals("할인 브랜드", BrandFilter.ALL.withDiscountOnly(true).label());
        assertEquals("선호 브랜드", BrandFilter.ALL.withPreferredOnly(true).label());
        assertEquals("SK", BrandFilter.ALL.withBrand("SKE", true).label());
        assertEquals("SK 외 2", BrandFilter.ALL.withBrand("SOL", true).withBrand("SKE", true)
                .withBrand("GSC", true).label());
        assertEquals("할인 브랜드 외 1", BrandFilter.ALL.withDiscountOnly(true).withBrand("GSC", true).label());
    }

    @Test
    public void brandFilter_chipLabels() {
        assertEquals("브랜드", BrandFilter.ALL.chipLabel());
        assertEquals("할인 브랜드", BrandFilter.ALL.withDiscountOnly(true).chipLabel());
        assertEquals("선호 브랜드", BrandFilter.ALL.withPreferredOnly(true).chipLabel());
        assertEquals("SK", BrandFilter.ALL.withBrand("SKE", true).chipLabel());
        assertEquals("브랜드 2", BrandFilter.ALL.withBrand("SKE", true).withBrand("GSC", true).chipLabel());
        assertEquals("브랜드 3", BrandFilter.ALL.withDiscountOnly(true).withPreferredOnly(true)
                .withBrand("GSC", true).chipLabel());
    }
}
