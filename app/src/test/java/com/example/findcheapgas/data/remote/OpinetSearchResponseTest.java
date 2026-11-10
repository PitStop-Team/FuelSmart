package com.example.findcheapgas.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.StationSearchResult;

import org.junit.Test;

import java.util.List;

/** 상호 검색(searchByName.do) 응답 해석. 필드가 없거나 형식이 조금 달라도 읽는다. */
public class OpinetSearchResponseTest {

    @Test
    public void readsAssumedFields() throws OpinetException {
        String body = "{\"RESULT\": {\"OIL\": [{\"UNI_ID\": \"A0900001\", \"POLL_DIV_CD\": \"SKE\","
                + " \"OS_NM\": \"역삼셀프주유소\", \"NEW_ADR\": \"서울특별시 강남구 테헤란로 152\","
                + " \"VAN_ADR\": \"서울특별시 강남구 역삼동 737-12\","
                + " \"GIS_X_COOR\": 314565.51, \"GIS_Y_COOR\": 544215.38}]}}";
        List<StationSearchResult> results = OpinetSearchResponse.parse(body);

        assertEquals(1, results.size());
        StationSearchResult station = results.get(0);
        assertEquals("A0900001", station.getId());
        assertEquals("SKE", station.getBrandCode());
        assertEquals("역삼셀프주유소", station.getName());
        assertEquals("서울특별시 강남구 테헤란로 152", station.getAddress());
        // KATEC → WGS84 (강남역 근처)
        assertEquals(37.50, station.getLatitude(), 0.01);
        assertEquals(127.03, station.getLongitude(), 0.02);
    }

    @Test
    public void fallbacks_brandCo_lotAddress_stringNumbers() throws OpinetException {
        String body = "{\"RESULT\": {\"OIL\": [{\"UNI_ID\": \"A1\", \"POLL_DIV_CO\": \"GSC\","
                + " \"OS_NM\": \"대치주유소\", \"VAN_ADR\": \"서울 강남구 대치동 1\","
                + " \"GIS_X_COOR\": \"316718.81\", \"GIS_Y_COOR\": \"543923.08\"}]}}";
        StationSearchResult station = OpinetSearchResponse.parse(body).get(0);

        assertEquals("GSC", station.getBrandCode());
        assertEquals("서울 강남구 대치동 1", station.getAddress());
        assertEquals(37.5, station.getLatitude(), 0.1);
    }

    @Test
    public void skipsItemsWithoutIdNameOrCoordinates() throws OpinetException {
        String body = "{\"RESULT\": {\"OIL\": ["
                + "{\"OS_NM\": \"ID 없음\", \"GIS_X_COOR\": 314565.51, \"GIS_Y_COOR\": 544215.38},"
                + "{\"UNI_ID\": \"A2\", \"GIS_X_COOR\": 314565.51, \"GIS_Y_COOR\": 544215.38},"
                + "{\"UNI_ID\": \"A3\", \"OS_NM\": \"좌표 없음\"},"
                + "\"문자열 항목\","
                + "{\"UNI_ID\": \"A4\", \"OS_NM\": \"정상\", \"GIS_X_COOR\": 314565.51, \"GIS_Y_COOR\": 544215.38}"
                + "]}}";
        List<StationSearchResult> results = OpinetSearchResponse.parse(body);

        assertEquals(1, results.size());
        assertEquals("A4", results.get(0).getId());
        assertEquals("", results.get(0).getBrandCode());
        assertEquals("", results.get(0).getAddress());
    }

    @Test
    public void emptyOil_returnsEmpty() throws OpinetException {
        assertTrue(OpinetSearchResponse.parse("{\"RESULT\": {\"OIL\": []}}").isEmpty());
    }

    @Test(expected = OpinetException.class)
    public void notJson_throws() throws OpinetException {
        OpinetSearchResponse.parse("API 키가 올바르지 않습니다");
    }

    @Test(expected = OpinetException.class)
    public void missingOil_throws() throws OpinetException {
        OpinetSearchResponse.parse("{\"RESULT\": {}}");
    }
}
