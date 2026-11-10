package com.example.findcheapgas.vehicle;

import java.util.OptionalDouble;

/**
 * 차량에서 읽는 연료 데이터. 차량이나 SDK가 지원하지 않으면 값을 주지 못한다.
 * 호출하는 쪽은 먼저 {@link #isFuelDataSupported()}를 확인하고, 값이 empty면 사용자 입력값(CarInfo)으로 대체한다.
 */
public interface VehicleDataSource {

    /** 이 차량/구현이 연료 데이터를 제공하는지. false면 아래 조회는 항상 empty */
    boolean isFuelDataSupported();

    /** 현재 남은 연료량(L). 미지원이거나 읽지 못하면 empty */
    OptionalDouble getFuelAmountLiters();

    /** 연비(km/L). 미지원이거나 읽지 못하면 empty */
    OptionalDouble getFuelEfficiencyKmPerLiter();
}
