package com.example.findcheapgas.vehicle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.CarInfo;

import org.junit.Test;

public class VehicleDataSourceTest {

    @Test
    public void defaultCarInfoIsTreatedAsUnsupported() {
        VehicleDataSource source = new UserInputVehicleDataSource(CarInfo.DEFAULT);
        assertFalse(source.isFuelDataSupported());
        assertFalse(source.getFuelEfficiencyKmPerLiter().isPresent());
        assertFalse(source.getFuelAmountLiters().isPresent());
    }

    @Test
    public void userEnteredCarInfoGivesEfficiency() {
        VehicleDataSource source = new UserInputVehicleDataSource(CarInfo.userEntered(125, 50));
        assertTrue(source.isFuelDataSupported());
        assertEquals(12.5, source.getFuelEfficiencyKmPerLiter().getAsDouble(), 1e-9);
        assertFalse(source.getFuelAmountLiters().isPresent());
    }

    @Test
    public void mockUnsupportedIsEmpty() {
        VehicleDataSource source = MockVehicleDataSource.unsupported();
        assertFalse(source.isFuelDataSupported());
        assertFalse(source.getFuelAmountLiters().isPresent());
        assertFalse(source.getFuelEfficiencyKmPerLiter().isPresent());
    }

    @Test
    public void resolverPrefersVehicleEfficiencyButKeepsUserFuelAmount() {
        CarInfo user = CarInfo.userEntered(100, 30);
        CarInfo resolved = CarInfoResolver.resolve(MockVehicleDataSource.of(20, 15.0), user);
        assertEquals(150, resolved.getFuelEfficiencyTenths());
        assertEquals(30, resolved.getFuelAmount());
    }

    @Test
    public void resolverFallsBackToUserInput() {
        CarInfo user = CarInfo.userEntered(100, 30);
        assertSame(user, CarInfoResolver.resolve(MockVehicleDataSource.unsupported(), user));
        assertSame(user, CarInfoResolver.resolve(null, user));
        // 범위를 벗어난 차량 값(0.5km/L)은 쓰지 않는다
        assertSame(user, CarInfoResolver.resolve(MockVehicleDataSource.of(20, 0.5), user));
    }
}
