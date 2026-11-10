package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.BrandSetting;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.StationSearchResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 주유소 이름 검색 결과: 숨긴 브랜드 제외, 최대 30곳 */
public class StationSearchResultsTest {

    private static StationSearchResult station(String id, String brand) {
        return new StationSearchResult(id, brand, id, "", 37.5, 127.0);
    }

    private static final BrandSettings HIDE_GSC =
            BrandSettings.EMPTY.with("GSC", new BrandSetting(0, null, false, true));

    @Test
    public void hiddenBrands_areRemoved_orderKept() {
        List<StationSearchResult> all = new ArrayList<>();
        all.add(station("a", "SKE"));
        all.add(station("b", "GSC"));
        all.add(station("c", "RTE"));

        List<StationSearchResult> visible = StationSearchResults.visible(all, HIDE_GSC);
        assertEquals(2, visible.size());
        assertEquals("a", visible.get(0).getId());
        assertEquals("c", visible.get(1).getId());
    }

    @Test
    public void atMost30_countedAfterRemovingHidden() {
        List<StationSearchResult> all = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            all.add(station("hidden" + i, "GSC"));
        }
        for (int i = 0; i < 35; i++) {
            all.add(station("s" + i, "SKE"));
        }
        List<StationSearchResult> visible = StationSearchResults.visible(all, HIDE_GSC);

        assertEquals(30, visible.size());
        assertEquals("s0", visible.get(0).getId());
        assertEquals("s29", visible.get(29).getId());
        assertTrue(StationSearchResults.isTruncated(all, HIDE_GSC));
    }

    @Test
    public void exactly30_isNotTruncated() {
        List<StationSearchResult> all = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            all.add(station("s" + i, "SKE"));
        }
        assertEquals(30, StationSearchResults.visible(all, BrandSettings.EMPTY).size());
        assertFalse(StationSearchResults.isTruncated(all, BrandSettings.EMPTY));
    }
}
