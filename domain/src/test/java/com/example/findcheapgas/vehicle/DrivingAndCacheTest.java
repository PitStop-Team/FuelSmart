package com.example.findcheapgas.vehicle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.CarInfo;

import org.junit.Test;

public class DrivingAndCacheTest {

    // ---------- CachedVehicleDataSource ----------

    @Test
    public void emptyCacheIsUnsupported() {
        CachedVehicleDataSource source = new CachedVehicleDataSource();
        assertFalse(source.isFuelDataSupported());
        assertFalse(source.getFuelEfficiencyKmPerLiter().isPresent());
        assertFalse(source.getFuelPercent().isPresent());
    }

    @Test
    public void convertsLitersPer100KmToKmPerLiter() {
        CachedVehicleDataSource source = new CachedVehicleDataSource();
        source.setFuelEconomyLitersPer100Km(8.0f);
        assertTrue(source.isFuelDataSupported());
        assertEquals(12.5, source.getFuelEfficiencyKmPerLiter().getAsDouble(), 1e-9);
    }

    @Test
    public void ignoresUnusableValues() {
        CachedVehicleDataSource source = new CachedVehicleDataSource();
        source.setFuelEconomyLitersPer100Km(0f);
        source.setFuelPercent(120);
        assertFalse(source.isFuelDataSupported());
        source.setFuelEconomyLitersPer100Km(Float.NaN);
        source.setFuelPercent(-1);
        assertFalse(source.isFuelDataSupported());
        source.setFuelPercent(55);
        assertEquals(55, source.getFuelPercent().getAsInt());
        source.clear();
        assertFalse(source.isFuelDataSupported());
    }

    @Test
    public void cachedEfficiencyFeedsCarInfoResolver() {
        CachedVehicleDataSource source = new CachedVehicleDataSource();
        CarInfo user = CarInfo.userEntered(100, 30);
        // 값이 없으면 사용자 입력값
        assertEquals(user, CarInfoResolver.resolve(source, user));
        source.setFuelEconomyLitersPer100Km(8.0f);
        CarInfo resolved = CarInfoResolver.resolve(source, user);
        assertEquals(125, resolved.getFuelEfficiencyTenths());
        assertEquals(30, resolved.getFuelAmount());
    }

    @Test
    public void percentOnlyDoesNotChangeCarInfo() {
        CachedVehicleDataSource source = new CachedVehicleDataSource();
        source.setFuelPercent(40);
        CarInfo user = CarInfo.userEntered(100, 30);
        assertEquals(user, CarInfoResolver.resolve(source, user));
    }

    // ---------- DrivingStateTracker ----------

    @Test
    public void unknownUntilFirstBrakeValue() {
        DrivingStateTracker tracker = new DrivingStateTracker();
        assertEquals(DrivingState.UNKNOWN, tracker.stateAt(0));
        tracker.onUnavailable();
        assertEquals(DrivingState.UNKNOWN, tracker.stateAt(10_000));
    }

    @Test
    public void shortBrakingStaysDriving() {
        DrivingStateTracker tracker = new DrivingStateTracker();
        tracker.onBrakePedal(false, 0);
        tracker.onBrakePedal(true, 1_000);
        assertEquals(DrivingState.DRIVING, tracker.stateAt(1_000));
        assertEquals(DrivingState.DRIVING, tracker.stateAt(3_999));
        assertEquals(2_000, tracker.millisUntilStopped(2_000));
    }

    @Test
    public void heldBrakeBecomesStoppedAtThreshold() {
        DrivingStateTracker tracker = new DrivingStateTracker();
        tracker.onBrakePedal(true, 1_000);
        assertEquals(DrivingState.STOPPED, tracker.stateAt(1_000 + DrivingStateTracker.STOP_HOLD_MILLIS));
        assertEquals(-1, tracker.millisUntilStopped(1_000 + DrivingStateTracker.STOP_HOLD_MILLIS));
    }

    @Test
    public void releasingBrakeReturnsToDrivingAndRestartsTimer() {
        DrivingStateTracker tracker = new DrivingStateTracker();
        tracker.onBrakePedal(true, 0);
        assertEquals(DrivingState.STOPPED, tracker.stateAt(5_000));
        tracker.onBrakePedal(false, 6_000);
        assertEquals(DrivingState.DRIVING, tracker.stateAt(6_000));
        tracker.onBrakePedal(true, 7_000);
        assertEquals(DrivingState.DRIVING, tracker.stateAt(8_000));
    }

    @Test
    public void repeatedPressedEventsDoNotRestartTimer() {
        DrivingStateTracker tracker = new DrivingStateTracker();
        tracker.onBrakePedal(true, 0);
        tracker.onBrakePedal(true, 2_000);
        assertEquals(DrivingState.STOPPED, tracker.stateAt(3_000));
    }

    @Test
    public void losingDataReturnsToUnknown() {
        DrivingStateTracker tracker = new DrivingStateTracker();
        tracker.onBrakePedal(true, 0);
        tracker.onUnavailable();
        assertEquals(DrivingState.UNKNOWN, tracker.stateAt(9_000));
    }
}
