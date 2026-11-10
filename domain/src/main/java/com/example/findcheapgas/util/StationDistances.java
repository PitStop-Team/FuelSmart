package com.example.findcheapgas.util;

import com.example.findcheapgas.model.GasStation;

import java.util.ArrayList;
import java.util.List;

/** 주유소 목록의 거리를 조회 위치 기준 직선거리로 다시 계산한다. */
public final class StationDistances {

    private StationDistances() {
    }

    /** 입력 목록은 바꾸지 않고, 거리만 조회 위치(WGS84)에서 잰 값으로 바꾼 새 목록을 돌려준다. */
    public static List<GasStation> fromOrigin(List<GasStation> stations, double latitude, double longitude) {
        CoordinateConverter.Katec origin = CoordinateConverter.toKatec(latitude, longitude);
        List<GasStation> result = new ArrayList<>(stations.size());
        for (GasStation station : stations) {
            result.add(station.withDistance(
                    CoordinateConverter.distanceMeters(origin, station.getKatecX(), station.getKatecY())));
        }
        return result;
    }
}
