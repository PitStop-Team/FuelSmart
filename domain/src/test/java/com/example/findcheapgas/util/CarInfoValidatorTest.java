package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.util.CarInfoValidator.Error;
import com.example.findcheapgas.util.CarInfoValidator.Result;

import org.junit.Test;

public class CarInfoValidatorTest {

    @Test
    public void fuelEfficiency_valid_returnsTenths() {
        assertValid(120, CarInfoValidator.parseFuelEfficiency("12"));
        assertValid(125, CarInfoValidator.parseFuelEfficiency("12.5"));
        assertValid(125, CarInfoValidator.parseFuelEfficiency("12.50"));
        assertValid(120, CarInfoValidator.parseFuelEfficiency("12."));
        assertValid(120, CarInfoValidator.parseFuelEfficiency(" 12.0 "));
        assertValid(125, CarInfoValidator.parseFuelEfficiency("12,5"));
    }

    @Test
    public void fuelEfficiency_boundaries() {
        assertValid(30, CarInfoValidator.parseFuelEfficiency("3"));
        assertValid(400, CarInfoValidator.parseFuelEfficiency("40"));
        assertError(Error.OUT_OF_RANGE, CarInfoValidator.parseFuelEfficiency("2.9"));
        assertError(Error.OUT_OF_RANGE, CarInfoValidator.parseFuelEfficiency("40.1"));
        assertError(Error.OUT_OF_RANGE, CarInfoValidator.parseFuelEfficiency("0"));
        assertError(Error.OUT_OF_RANGE, CarInfoValidator.parseFuelEfficiency("100"));
    }

    @Test
    public void fuelEfficiency_tooManyDecimals() {
        assertError(Error.TOO_MANY_DECIMALS, CarInfoValidator.parseFuelEfficiency("12.55"));
        assertError(Error.TOO_MANY_DECIMALS, CarInfoValidator.parseFuelEfficiency("3.05"));
    }

    @Test
    public void fuelEfficiency_emptyOrNotNumber() {
        assertError(Error.EMPTY, CarInfoValidator.parseFuelEfficiency(""));
        assertError(Error.EMPTY, CarInfoValidator.parseFuelEfficiency("  "));
        assertError(Error.EMPTY, CarInfoValidator.parseFuelEfficiency(null));
        assertError(Error.NOT_NUMBER, CarInfoValidator.parseFuelEfficiency("."));
        assertError(Error.NOT_NUMBER, CarInfoValidator.parseFuelEfficiency("12.5.1"));
        assertError(Error.NOT_NUMBER, CarInfoValidator.parseFuelEfficiency("-12"));
        assertError(Error.NOT_NUMBER, CarInfoValidator.parseFuelEfficiency("1e1"));
        assertError(Error.NOT_NUMBER, CarInfoValidator.parseFuelEfficiency("abc"));
    }

    @Test
    public void fuelAmount_valid() {
        assertValid(40, CarInfoValidator.parseFuelAmount("40"));
        assertValid(40, CarInfoValidator.parseFuelAmount("40.0"));
        assertValid(5, CarInfoValidator.parseFuelAmount("5"));
        assertValid(150, CarInfoValidator.parseFuelAmount("150"));
    }

    @Test
    public void fuelAmount_invalid() {
        assertError(Error.OUT_OF_RANGE, CarInfoValidator.parseFuelAmount("4"));
        assertError(Error.OUT_OF_RANGE, CarInfoValidator.parseFuelAmount("151"));
        assertError(Error.NOT_INTEGER, CarInfoValidator.parseFuelAmount("40.5"));
        assertError(Error.EMPTY, CarInfoValidator.parseFuelAmount(""));
        assertError(Error.NOT_NUMBER, CarInfoValidator.parseFuelAmount("마흔"));
    }

    private static void assertValid(int expected, Result result) {
        assertTrue("오류: " + result.getError(), result.isValid());
        assertEquals(expected, result.getValue());
    }

    private static void assertError(Error expected, Result result) {
        assertFalse(result.isValid());
        assertEquals(expected, result.getError());
    }
}
