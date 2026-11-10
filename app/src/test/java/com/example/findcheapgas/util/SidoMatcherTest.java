package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.AveragePrice;
import com.example.findcheapgas.model.FuelType;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SidoMatcherTest {

    // 17개 시도: 주소에 쓰이는 긴 이름 → 오피넷 짧은 이름
    private static final String[][] LONG_NAMES = {
            {"서울특별시", "서울"},
            {"부산광역시", "부산"},
            {"대구광역시", "대구"},
            {"인천광역시", "인천"},
            {"광주광역시", "광주"},
            {"대전광역시", "대전"},
            {"울산광역시", "울산"},
            {"세종특별자치시", "세종"},
            {"경기도", "경기"},
            {"강원특별자치도", "강원"},
            {"충청북도", "충북"},
            {"충청남도", "충남"},
            {"전북특별자치도", "전북"},
            {"전라남도", "전남"},
            {"경상북도", "경북"},
            {"경상남도", "경남"},
            {"제주특별자치도", "제주"},
    };

    @Test
    public void all17Sido_longNames() {
        assertEquals(17, LONG_NAMES.length);
        for (String[] pair : LONG_NAMES) {
            assertEquals(pair[0], pair[1], SidoMatcher.sidoOf(pair[0] + " 어딘가구 어딘가로 1"));
        }
    }

    @Test
    public void all17Sido_shortNames() {
        for (String[] pair : LONG_NAMES) {
            assertEquals(pair[1], pair[1], SidoMatcher.sidoOf(pair[1] + " 어딘가구 1"));
        }
    }

    @Test
    public void oldNamesBeforeSpecialSelfGoverningStatus() {
        assertEquals("강원", SidoMatcher.sidoOf("강원도 춘천시 중앙로 1"));
        assertEquals("전북", SidoMatcher.sidoOf("전라북도 전주시 완산구 1"));
        assertEquals("제주", SidoMatcher.sidoOf("제주도 제주시 1"));
        assertEquals("세종", SidoMatcher.sidoOf("세종시 한누리대로 1"));
    }

    @Test
    public void unknownOrBlankAddress() {
        assertNull(SidoMatcher.sidoOf(null));
        assertNull(SidoMatcher.sidoOf("   "));
        assertNull(SidoMatcher.sidoOf("강남구 테헤란로 152"));
        assertNull(SidoMatcher.sidoOf("서울특별시강남구"));
        assertTrue(SidoMatcher.candidates("Seoul").isEmpty());
    }

    @Test
    public void leadingSpacesAreIgnored() {
        assertEquals("서울", SidoMatcher.sidoOf("  서울특별시  강남구"));
    }

    @Test
    public void gwangjuAndJeonnamFallBackToMergedName() {
        assertEquals(Arrays.asList("광주", "전남광주"), SidoMatcher.candidates("광주광역시 북구 1"));
        assertEquals(Arrays.asList("전남", "전남광주"), SidoMatcher.candidates("전라남도 목포시 1"));
        assertEquals(Collections.singletonList("전남광주"), SidoMatcher.candidates("전남광주특별시 북구 1"));
        assertEquals(Collections.singletonList("서울"), SidoMatcher.candidates("서울특별시 강남구"));
    }

    @Test
    public void find_matchesOpinetShortName() {
        List<AveragePrice> sido = Arrays.asList(
                new AveragePrice(FuelType.GASOLINE, 1739.63, "01", "서울"),
                new AveragePrice(FuelType.DIESEL, 1620.41, "01", "서울"),
                new AveragePrice(FuelType.GASOLINE, 1680.00, "04", "충북"));

        assertEquals(1739.63, SidoMatcher.find("서울특별시 강남구", sido, FuelType.GASOLINE).getPrice(), 1e-9);
        assertEquals(1620.41, SidoMatcher.find("서울 강남구", sido, FuelType.DIESEL).getPrice(), 1e-9);
        assertEquals(1680.00, SidoMatcher.find("충청북도 청주시", sido, FuelType.GASOLINE).getPrice(), 1e-9);
        assertNull(SidoMatcher.find("충청북도 청주시", sido, FuelType.DIESEL));
        assertNull(SidoMatcher.find("부산광역시 해운대구", sido, FuelType.GASOLINE));
        assertNull(SidoMatcher.find(null, sido, FuelType.GASOLINE));
    }

    @Test
    public void find_acceptsLongNameInResponse() {
        List<AveragePrice> sido = Collections.singletonList(
                new AveragePrice(FuelType.GASOLINE, 1700, "03", "강원특별자치도"));
        assertEquals(1700, SidoMatcher.find("강원도 춘천시", sido, FuelType.GASOLINE).getPrice(), 1e-9);
    }

    @Test
    public void find_gwangjuUsesMergedEntryOnlyWhenSeparateIsMissing() {
        AveragePrice merged = new AveragePrice(FuelType.GASOLINE, 1650, "20", "전남광주");
        AveragePrice gwangju = new AveragePrice(FuelType.GASOLINE, 1640, "16", "광주");

        assertEquals(1650, SidoMatcher.find("광주광역시 북구", Collections.singletonList(merged),
                FuelType.GASOLINE).getPrice(), 1e-9);
        assertEquals(1650, SidoMatcher.find("전라남도 여수시", Collections.singletonList(merged),
                FuelType.GASOLINE).getPrice(), 1e-9);
        assertEquals(1640, SidoMatcher.find("광주광역시 북구", Arrays.asList(merged, gwangju),
                FuelType.GASOLINE).getPrice(), 1e-9);
    }
}
