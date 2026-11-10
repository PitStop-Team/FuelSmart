package com.example.findcheapgas.util;

import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationSaving;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 받아 둔 전체 목록(최대 반경 5km)으로 화면에 보여줄 목록을 만든다. API는 부르지 않는다.
 * 처리 순서: 전체 목록 → 반경 필터 → 숨긴 브랜드 제외 → 브랜드 빠른 필터
 *           → 예상 이득 계산(브랜드 할인 적용) → 선호 브랜드 표시 → 정렬 → 화면
 * 예상 이득은 필터를 거친 목록 안에서 계산하므로, 기준 주유소도 화면에 보이는 주유소 중 가장 가까운 곳이 된다.
 * 기준 주유소의 브랜드에 할인이 있으면 기준에도 할인을 적용한다.
 */
public final class StationListBuilder {

    /** 화면 목록이 비었을 때 그 이유. 안내 문구를 고르는 데 쓴다. */
    public enum EmptyReason {
        NONE,           // 비어 있지 않음
        RADIUS,         // 반경 안에 주유소가 없음
        HIDDEN_BRANDS,  // 반경 안에 있지만 모두 숨긴 브랜드
        BRAND_FILTER    // 반경 안에 있지만 빠른 필터에서 고른 브랜드가 없음
    }

    private StationListBuilder() {
    }

    /**
     * 입력 목록은 바꾸지 않고 새 목록을 돌려준다.
     * brandFilter는 저장해 둔 값을 그대로 넘기면 되고, 여기서 지금 브랜드 설정에 맞게 정리(effective)한다.
     */
    public static List<StationSaving> build(List<GasStation> all, SearchRadius radius, SortOrder sortOrder,
                                            CarInfo carInfo, BrandSettings brandSettings, BrandFilter brandFilter) {
        List<GasStation> stations = filterByBrandFilter(
                excludeHidden(filterByRadius(all, radius), brandSettings), brandSettings, brandFilter);
        List<StationSaving> calculated = SavingCalculator.calculate(stations, carInfo, brandSettings);
        List<StationSaving> result = new ArrayList<>(calculated.size());
        for (StationSaving saving : calculated) {
            result.add(saving.withPreferred(brandSettings.isPreferred(saving.getStation().getBrandCode())));
        }
        Collections.sort(result, sortOrder.getComparator());
        return Collections.unmodifiableList(result);
    }

    /** build()의 결과가 비었을 때 어느 단계에서 모두 빠졌는지 */
    public static EmptyReason emptyReason(List<GasStation> all, SearchRadius radius,
                                          BrandSettings brandSettings, BrandFilter brandFilter) {
        List<GasStation> inRadius = filterByRadius(all, radius);
        if (inRadius.isEmpty()) {
            return EmptyReason.RADIUS;
        }
        List<GasStation> visible = excludeHidden(inRadius, brandSettings);
        if (visible.isEmpty()) {
            return EmptyReason.HIDDEN_BRANDS;
        }
        if (filterByBrandFilter(visible, brandSettings, brandFilter).isEmpty()) {
            return EmptyReason.BRAND_FILTER;
        }
        return EmptyReason.NONE;
    }

    private static List<GasStation> filterByRadius(List<GasStation> all, SearchRadius radius) {
        List<GasStation> result = new ArrayList<>(all.size());
        for (GasStation station : all) {
            if (station.getDistance() <= radius.getMeters()) {
                result.add(station);
            }
        }
        return result;
    }

    private static List<GasStation> excludeHidden(List<GasStation> stations, BrandSettings brandSettings) {
        List<GasStation> result = new ArrayList<>(stations.size());
        for (GasStation station : stations) {
            if (!brandSettings.isHidden(station.getBrandCode())) {
                result.add(station);
            }
        }
        return result;
    }

    private static List<GasStation> filterByBrandFilter(List<GasStation> stations, BrandSettings brandSettings,
                                                        BrandFilter brandFilter) {
        BrandFilter filter = brandFilter.effective(brandSettings);
        if (filter.isAll()) {
            return stations;
        }
        List<GasStation> result = new ArrayList<>(stations.size());
        for (GasStation station : stations) {
            if (filter.matches(station, brandSettings)) {
                result.add(station);
            }
        }
        return result;
    }
}
