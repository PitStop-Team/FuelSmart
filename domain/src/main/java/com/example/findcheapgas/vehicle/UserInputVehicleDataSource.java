package com.example.findcheapgas.vehicle;

import com.example.findcheapgas.model.CarInfo;

import java.util.OptionalDouble;

/**
 * 사용자가 입력한 내 차 정보(CarInfo)를 VehicleDataSource로 보여 준다.
 * 사용자가 입력하지 않은 기본값(CarInfo.DEFAULT)은 차량 데이터가 아니므로 "미지원"(empty)으로 취급한다.
 * 입력한 주유량은 한 번에 넣는 양이므로 남은 연료량으로는 쓰지 않는다.
 */
public final class UserInputVehicleDataSource implements VehicleDataSource {

    private final CarInfo carInfo;

    public UserInputVehicleDataSource(CarInfo carInfo) {
        this.carInfo = carInfo;
    }

    @Override
    public boolean isFuelDataSupported() {
        return !carInfo.isDefault();
    }

    @Override
    public OptionalDouble getFuelAmountLiters() {
        return OptionalDouble.empty();
    }

    @Override
    public OptionalDouble getFuelEfficiencyKmPerLiter() {
        return carInfo.isDefault() ? OptionalDouble.empty() : OptionalDouble.of(carInfo.getFuelEfficiency());
    }
}
