package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 지도 탭이 마지막으로 받아 둔 주변 조회 결과. 즐겨찾기 탭이 같은 조회 위치, 유종, 기준 주유소로 계산하려고 쓴다.
 * - all: 오피넷에서 받은 5km 안 전체 목록 (조회 실패면 null)
 * - displayed: 지도 탭 화면 목록 (반경, 숨긴 브랜드, 빠른 필터, 예상 이득 계산 적용)
 */
public final class AroundStations {

    private final SearchOrigin origin;
    private final FuelType fuelType;
    @Nullable
    private final List<GasStation> all;
    private final List<StationSaving> displayed;

    public AroundStations(SearchOrigin origin, FuelType fuelType, @Nullable List<GasStation> all,
                          List<StationSaving> displayed) {
        this.origin = origin;
        this.fuelType = fuelType;
        this.all = all;
        this.displayed = displayed;
    }

    public SearchOrigin getOrigin() {
        return origin;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    /** 조회에 실패해서 목록이 없는지 */
    public boolean isFailed() {
        return all == null;
    }

    /** 5km 안 전체 목록. 조회에 실패했으면 빈 목록 */
    public List<GasStation> getAll() {
        return all != null ? all : Collections.emptyList();
    }

    public List<StationSaving> getDisplayed() {
        return displayed;
    }

    /** 5km 안 전체 목록에 있는 주유소. 없으면 null */
    @Nullable
    public GasStation findInAll(String stationId) {
        for (GasStation station : getAll()) {
            if (station.getId().equals(stationId)) {
                return station;
            }
        }
        return null;
    }

    /** 지도 탭 화면 목록에 있는 주유소의 계산 결과. 없으면 null */
    @Nullable
    public StationSaving findDisplayed(String stationId) {
        for (StationSaving saving : displayed) {
            if (saving.getStation().getId().equals(stationId)) {
                return saving;
            }
        }
        return null;
    }
}
