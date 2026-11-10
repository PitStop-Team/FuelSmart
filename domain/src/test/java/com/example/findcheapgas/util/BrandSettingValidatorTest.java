package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BrandSettingValidatorTest {

    @Test
    public void perLiter_validRange() {
        assertEquals(Integer.valueOf(0), BrandSettingValidator.parsePerLiter("0").getValue());
        assertEquals(Integer.valueOf(100), BrandSettingValidator.parsePerLiter(" 100 ").getValue());
        assertEquals(Integer.valueOf(500), BrandSettingValidator.parsePerLiter("500").getValue());
    }

    @Test
    public void perLiter_emptyMeansNoDiscount() {
        BrandSettingValidator.Result result = BrandSettingValidator.parsePerLiter("");
        assertTrue(result.isValid());
        assertEquals(Integer.valueOf(0), result.getValue());
    }

    @Test
    public void perLiter_errors() {
        assertEquals(BrandSettingValidator.Error.OUT_OF_RANGE, BrandSettingValidator.parsePerLiter("501").getError());
        assertEquals(BrandSettingValidator.Error.NOT_INTEGER, BrandSettingValidator.parsePerLiter("10.5").getError());
        assertEquals(BrandSettingValidator.Error.NOT_NUMBER, BrandSettingValidator.parsePerLiter("abc").getError());
        assertEquals(BrandSettingValidator.Error.NOT_NUMBER, BrandSettingValidator.parsePerLiter("-1").getError());
    }

    @Test
    public void cap_emptyMeansNoLimit() {
        BrandSettingValidator.Result result = BrandSettingValidator.parseCap("  ");
        assertTrue(result.isValid());
        assertNull(result.getValue());
    }

    @Test
    public void cap_validRangeAndCommas() {
        assertEquals(Integer.valueOf(0), BrandSettingValidator.parseCap("0").getValue());
        assertEquals(Integer.valueOf(3_000), BrandSettingValidator.parseCap("3,000").getValue());
        assertEquals(Integer.valueOf(100_000), BrandSettingValidator.parseCap("100000").getValue());
    }

    @Test
    public void cap_errors() {
        assertEquals(BrandSettingValidator.Error.OUT_OF_RANGE, BrandSettingValidator.parseCap("100001").getError());
        assertEquals(BrandSettingValidator.Error.NOT_INTEGER, BrandSettingValidator.parseCap("3000.5").getError());
    }
}
