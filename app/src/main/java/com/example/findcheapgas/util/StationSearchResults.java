package com.example.findcheapgas.util;

import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.StationSearchResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 주유소 이름 검색 결과 중 화면에 보여줄 것: 숨긴 브랜드를 빼고 최대 30곳 */
public final class StationSearchResults {

    public static final int MAX_RESULTS = 30;

    private StationSearchResults() {
    }

    /** 응답 순서를 그대로 두고, 숨긴 브랜드를 뺀 뒤 앞에서 30곳까지 */
    public static List<StationSearchResult> visible(List<StationSearchResult> all, BrandSettings brandSettings) {
        List<StationSearchResult> result = new ArrayList<>();
        for (StationSearchResult station : all) {
            if (result.size() >= MAX_RESULTS) {
                break;
            }
            if (!brandSettings.isHidden(station.getBrandCode())) {
                result.add(station);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 숨긴 브랜드를 뺀 결과가 30곳보다 많아서 잘렸는지 */
    public static boolean isTruncated(List<StationSearchResult> all, BrandSettings brandSettings) {
        int count = 0;
        for (StationSearchResult station : all) {
            if (!brandSettings.isHidden(station.getBrandCode()) && ++count > MAX_RESULTS) {
                return true;
            }
        }
        return false;
    }
}
