package com.example.findcheapgas.data.remote.kakao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.Place;

import org.junit.Test;

import java.util.List;

/** 카카오 로컬 API 응답 해석. 실제 API는 부르지 않고 응답 모양의 문자열로 확인한다. */
public class KakaoLocalResponseTest {

    private static final String KEYWORD_BODY = "{\"documents\": ["
            + "{\"place_name\": \"강남역 2호선\", \"category_name\": \"교통,수송 > 지하철,철도 > 수도권2호선\","
            + " \"address_name\": \"서울 강남구 역삼동 858\", \"road_address_name\": \"서울 강남구 강남대로 396\","
            + " \"x\": \"127.027610\", \"y\": \"37.497942\"},"
            + "{\"place_name\": \"좌표 없는 곳\", \"address_name\": \"어딘가\", \"x\": \"\", \"y\": \"\"},"
            + "{\"place_name\": \"도로명 없는 곳\", \"category_name\": \"음식점\","
            + " \"address_name\": \"서울 강남구 역삼동 1\", \"road_address_name\": \"\", \"x\": 127.03, \"y\": 37.5}"
            + "], \"meta\": {\"total_count\": 3}}";

    @Test
    public void keyword_readsNameAddressCategoryAndCoordinates() throws KakaoException {
        List<Place> places = KakaoLocalResponse.parseKeyword(KEYWORD_BODY);

        assertEquals(2, places.size()); // 좌표 없는 항목은 뺀다.
        Place gangnam = places.get(0);
        assertEquals("강남역 2호선", gangnam.getName());
        assertEquals("서울 강남구 강남대로 396", gangnam.getAddress()); // 도로명 주소 우선
        assertEquals("수도권2호선", gangnam.getCategory());              // 마지막 분류
        assertEquals(37.497942, gangnam.getLatitude(), 1e-9);           // y가 위도
        assertEquals(127.027610, gangnam.getLongitude(), 1e-9);         // x가 경도

        Place noRoad = places.get(1);
        assertEquals("서울 강남구 역삼동 1", noRoad.getAddress());        // 도로명이 없으면 지번
        assertEquals("음식점", noRoad.getCategory());
        assertEquals(37.5, noRoad.getLatitude(), 1e-9);                 // 숫자로 와도 읽는다.
    }

    @Test
    public void keyword_emptyDocuments_returnsEmptyList() throws KakaoException {
        assertTrue(KakaoLocalResponse.parseKeyword("{\"documents\": [], \"meta\": {}}").isEmpty());
    }

    @Test
    public void address_usesBuildingNameOrAddressAsName() throws KakaoException {
        String body = "{\"documents\": ["
                + "{\"address_name\": \"서울 강남구 역삼동 737\", \"x\": \"127.036\", \"y\": \"37.500\","
                + " \"road_address\": {\"address_name\": \"서울 강남구 테헤란로 152\", \"building_name\": \"강남파이낸스센터\"}},"
                + "{\"address_name\": \"부산 해운대구 우동\", \"x\": \"129.16\", \"y\": \"35.16\", \"road_address\": null}"
                + "]}";
        List<Place> places = KakaoLocalResponse.parseAddress(body);

        assertEquals(2, places.size());
        assertEquals("강남파이낸스센터", places.get(0).getName());
        assertEquals("서울 강남구 테헤란로 152", places.get(0).getAddress());
        assertEquals("주소", places.get(0).getCategory());
        assertEquals("부산 해운대구 우동", places.get(1).getName());
        assertEquals("부산 해운대구 우동", places.get(1).getAddress());
    }

    @Test(expected = KakaoException.class)
    public void notJson_throws() throws KakaoException {
        KakaoLocalResponse.parseKeyword("<html>error</html>");
    }

    @Test(expected = KakaoException.class)
    public void missingDocuments_throws() throws KakaoException {
        KakaoLocalResponse.parseKeyword("{\"errorType\": \"AccessDeniedError\", \"message\": \"...\"}");
    }

    @Test
    public void authError_onlyFor401And403() {
        assertTrue(new KakaoException("HTTP 401", 401).isAuthError());
        assertTrue(new KakaoException("HTTP 403", 403).isAuthError());
        assertFalse(new KakaoException("HTTP 400", 400).isAuthError());
        assertFalse(new KakaoException("HTTP 500", 500).isAuthError());
    }

    @Test
    public void lastCategory_withoutSeparator_isWholeText() {
        assertEquals("카페", KakaoLocalResponse.lastCategory("카페"));
        assertEquals("", KakaoLocalResponse.lastCategory(""));
    }

    // ---------- 좌표 → 행정구역 ----------

    private static String regionDoc(String type, String depth1, String depth2, String depth3) {
        return "{\"region_type\": \"" + type + "\", \"address_name\": \"주소\","
                + " \"region_1depth_name\": \"" + depth1 + "\", \"region_2depth_name\": \"" + depth2 + "\","
                + " \"region_3depth_name\": \"" + depth3 + "\", \"region_4depth_name\": \"\","
                + " \"x\": 127.0364, \"y\": 37.5006}";
    }

    @Test
    public void parseRegionName_prefersAdministrativeDong() throws KakaoException {
        String body = "{\"meta\": {\"total_count\": 2}, \"documents\": ["
                + regionDoc("B", "서울특별시", "강남구", "역삼동") + ","
                + regionDoc("H", "서울특별시", "강남구", "역삼1동") + "]}";
        assertEquals("역삼1동", KakaoLocalResponse.parseRegionName(body));
    }

    @Test
    public void parseRegionName_fallsBackToLegalDongAndUpperRegion() throws KakaoException {
        String onlyLegal = "{\"documents\": [" + regionDoc("B", "서울특별시", "강남구", "역삼동") + "]}";
        assertEquals("역삼동", KakaoLocalResponse.parseRegionName(onlyLegal));

        String noDong = "{\"documents\": [" + regionDoc("H", "세종특별자치시", "", "") + "]}";
        assertEquals("세종특별자치시", KakaoLocalResponse.parseRegionName(noDong));

        String gu = "{\"documents\": [" + regionDoc("H", "서울특별시", "강남구", "") + "]}";
        assertEquals("강남구", KakaoLocalResponse.parseRegionName(gu));
    }

    @Test
    public void parseRegionName_emptyDocuments_isNull() throws KakaoException {
        assertNull(KakaoLocalResponse.parseRegionName("{\"meta\": {\"total_count\": 0}, \"documents\": []}"));
    }

    @Test(expected = KakaoException.class)
    public void parseRegionName_invalidBody_throws() throws KakaoException {
        KakaoLocalResponse.parseRegionName("<html>error</html>");
    }
}
