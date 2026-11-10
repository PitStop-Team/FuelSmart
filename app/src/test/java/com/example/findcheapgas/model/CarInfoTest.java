package com.example.findcheapgas.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CarInfoTest {

    @Test
    public void default_is12kmPerLiterAnd40Liters() {
        assertTrue(CarInfo.DEFAULT.isDefault());
        assertEquals(12.0, CarInfo.DEFAULT.getFuelEfficiency(), 1e-9);
        assertEquals(40, CarInfo.DEFAULT.getFuelAmount());
    }

    @Test
    public void userEntered_isNotDefaultEvenWithDefaultValues() {
        CarInfo same = CarInfo.userEntered(120, 40);
        assertFalse(same.isDefault());
        assertFalse(same.equals(CarInfo.DEFAULT));
    }

    @Test
    public void fromStored_notEntered_returnsDefault() {
        assertSame(CarInfo.DEFAULT, CarInfo.fromStored(false, 0, 0));
        assertSame(CarInfo.DEFAULT, CarInfo.fromStored(false, 150, 50));
    }

    @Test
    public void fromStored_entered_keepsValues() {
        CarInfo info = CarInfo.fromStored(true, 125, 50);
        assertFalse(info.isDefault());
        assertEquals(12.5, info.getFuelEfficiency(), 1e-9);
        assertEquals(50, info.getFuelAmount());
    }

    @Test
    public void fromStored_outOfRange_returnsDefault() {
        assertSame(CarInfo.DEFAULT, CarInfo.fromStored(true, 0, 0));
        assertSame(CarInfo.DEFAULT, CarInfo.fromStored(true, 125, 200));
        assertSame(CarInfo.DEFAULT, CarInfo.fromStored(true, 401, 40));
    }

    @Test(expected = IllegalArgumentException.class)
    public void userEntered_outOfRange_throws() {
        CarInfo.userEntered(29, 40);
    }
}
