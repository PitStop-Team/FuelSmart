package com.example.findcheapgas.vehicle;

import java.util.OptionalDouble;

/** 에뮬레이터용 모의 구현. 생성할 때 준 값을 그대로 돌려주고, {@link #unsupported()}는 모든 조회가 empty다. */
public final class MockVehicleDataSource implements VehicleDataSource {

    private final boolean supported;
    private final OptionalDouble fuelAmountLiters;
    private final OptionalDouble fuelEfficiency;

    private MockVehicleDataSource(boolean supported, OptionalDouble fuelAmountLiters, OptionalDouble fuelEfficiency) {
        this.supported = supported;
        this.fuelAmountLiters = fuelAmountLiters;
        this.fuelEfficiency = fuelEfficiency;
    }

    public static MockVehicleDataSource of(double fuelAmountLiters, double fuelEfficiencyKmPerLiter) {
        return new MockVehicleDataSource(true, OptionalDouble.of(fuelAmountLiters),
                OptionalDouble.of(fuelEfficiencyKmPerLiter));
    }

    public static MockVehicleDataSource unsupported() {
        return new MockVehicleDataSource(false, OptionalDouble.empty(), OptionalDouble.empty());
    }

    @Override
    public boolean isFuelDataSupported() {
        return supported;
    }

    @Override
    public OptionalDouble getFuelAmountLiters() {
        return fuelAmountLiters;
    }

    @Override
    public OptionalDouble getFuelEfficiencyKmPerLiter() {
        return fuelEfficiency;
    }
}
