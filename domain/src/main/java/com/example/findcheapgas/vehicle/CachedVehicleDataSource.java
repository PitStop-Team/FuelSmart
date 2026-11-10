package com.example.findcheapgas.vehicle;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * 차량 SDK 콜백으로 받은 마지막 값을 들고 있다가 동기 방식으로 돌려주는 VehicleDataSource.
 * 차량 SDK 연동 코드(app)가 값을 채우고, 계산 쪽은 이 인터페이스로만 읽는다.
 * 차량 SDK의 현재 연비는 L/100km 단위라서 여기서 km/L로 바꾼다.
 * 여러 스레드에서 쓸 수 있다.
 */
public final class CachedVehicleDataSource implements VehicleDataSource {

    private Integer fuelPercent;
    private Float fuelEconomyLitersPer100Km;

    /** 연료 잔량(%). 값을 못 읽었으면 null */
    public synchronized void setFuelPercent(Integer percent) {
        this.fuelPercent = (percent != null && percent >= 0 && percent <= 100) ? percent : null;
    }

    /** 현재 연비(L/100km). 값을 못 읽었거나 0 이하면 지운다. */
    public synchronized void setFuelEconomyLitersPer100Km(Float litersPer100Km) {
        boolean usable = litersPer100Km != null && !litersPer100Km.isNaN()
                && !litersPer100Km.isInfinite() && litersPer100Km > 0;
        this.fuelEconomyLitersPer100Km = usable ? litersPer100Km : null;
    }

    /** 차량이 지원하지 않거나 읽기에 실패했을 때: 받아 둔 값을 모두 지운다. */
    public synchronized void clear() {
        fuelPercent = null;
        fuelEconomyLitersPer100Km = null;
    }

    @Override
    public synchronized boolean isFuelDataSupported() {
        return fuelPercent != null || fuelEconomyLitersPer100Km != null;
    }

    @Override
    public OptionalDouble getFuelAmountLiters() {
        // 차량 SDK는 잔량을 %로만 준다. 연료통 용량을 모르므로 리터는 모른다.
        return OptionalDouble.empty();
    }

    @Override
    public synchronized OptionalInt getFuelPercent() {
        return fuelPercent != null ? OptionalInt.of(fuelPercent) : OptionalInt.empty();
    }

    @Override
    public synchronized OptionalDouble getFuelEfficiencyKmPerLiter() {
        return fuelEconomyLitersPer100Km != null
                ? OptionalDouble.of(100.0 / fuelEconomyLitersPer100Km) : OptionalDouble.empty();
    }
}
