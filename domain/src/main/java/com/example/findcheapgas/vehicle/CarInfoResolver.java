package com.example.findcheapgas.vehicle;

import com.example.findcheapgas.model.CarInfo;

import java.util.OptionalDouble;

/**
 * 계산에 쓸 CarInfo를 정한다: 차량 연비가 있고 입력 범위 안이면 차량 값, 아니면 사용자 입력값.
 * 주유량은 항상 사용자 입력값이다 (차량이 주는 값은 남은 연료량이라 넣을 양이 아니다).
 */
public final class CarInfoResolver {

    private CarInfoResolver() {
    }

    public static CarInfo resolve(VehicleDataSource vehicle, CarInfo userInput) {
        if (vehicle == null || !vehicle.isFuelDataSupported()) {
            return userInput;
        }
        OptionalDouble efficiency = vehicle.getFuelEfficiencyKmPerLiter();
        if (!efficiency.isPresent()) {
            return userInput;
        }
        long tenths = Math.round(efficiency.getAsDouble() * 10);
        if (tenths < CarInfo.MIN_FUEL_EFFICIENCY_TENTHS || tenths > CarInfo.MAX_FUEL_EFFICIENCY_TENTHS) {
            return userInput;
        }
        return CarInfo.userEntered((int) tenths, userInput.getFuelAmount());
    }
}
