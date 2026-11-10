package com.example.findcheapgas.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.findcheapgas.model.FuelPrice;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.StationDetail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class OpinetDetailResponseTest {

    private static String wrap(String oil) {
        return "{\"RESULT\":{\"OIL\":[" + oil + "]}}";
    }

    private static List<FuelType> fuelTypes(StationDetail detail) {
        List<FuelType> result = new ArrayList<>();
        for (FuelPrice price : detail.getPrices()) {
            result.add(price.getFuelType());
        }
        return result;
    }

    @Test
    public void parse_fullResponse() throws OpinetException {
        StationDetail detail = OpinetDetailResponse.parse(wrap("{"
                + "\"UNI_ID\":\"A0000001\",\"POLL_DIV_CO\":\"SKE\",\"OS_NM\":\"역삼셀프주유소\","
                + "\"VAN_ADR\":\"서울특별시 강남구 역삼동 1-1\",\"NEW_ADR\":\"서울특별시 강남구 테헤란로 1\","
                + "\"TEL\":\"02-0000-0001\",\"LPG_YN\":\"N\",\"MAINT_YN\":\"Y\",\"CAR_WASH_YN\":\"Y\","
                + "\"KPETRO_YN\":\"N\",\"CVS_YN\":\"N\",\"GIS_X_COOR\":314565.51,\"GIS_Y_COOR\":544215.38,"
                + "\"OIL_PRICE\":["
                + "{\"PRODCD\":\"D047\",\"PRICE\":1519,\"TRADE_DT\":\"20261104\",\"TRADE_TM\":\"081512\"},"
                + "{\"PRODCD\":\"B027\",\"PRICE\":1659,\"TRADE_DT\":\"20261104\",\"TRADE_TM\":\"081512\"}]}"));

        assertEquals("A0000001", detail.getId());
        assertEquals("SKE", detail.getBrandCode());
        assertEquals("서울특별시 강남구 테헤란로 1", detail.getAddress());
        assertEquals("02-0000-0001", detail.getPhone());
        assertTrue(detail.hasCarWash());
        assertTrue(detail.hasMaintenance());
        assertFalse(detail.hasConvenienceStore());
        assertFalse(detail.isQualityCertified());
        assertEquals(314565.51, detail.getKatecX(), 0.001);
        // 응답 순서와 상관없이 휘발유, 경유 순서
        assertEquals(2, detail.getPrices().size());
        assertEquals(FuelType.GASOLINE, detail.getPrices().get(0).getFuelType());
        assertEquals(1659, detail.getPrices().get(0).getPrice());
        assertEquals("20261104", detail.getPrices().get(0).getTradeDate());
        assertEquals("081512", detail.getPrices().get(0).getTradeTime());
    }

    @Test
    public void parse_missingAndEmptyFields_doNotCrash() throws OpinetException {
        StationDetail detail = OpinetDetailResponse.parse(wrap(
                "{\"UNI_ID\":\"A1\",\"OS_NM\":\"주유소\",\"NEW_ADR\":\"\",\"TEL\":null,\"CAR_WASH_YN\":\"\"}"));
        assertNull(detail.getAddress());
        assertNull(detail.getPhone());
        assertFalse(detail.hasCarWash());
        assertEquals("", detail.getBrandCode());
        assertTrue(detail.getPrices().isEmpty());
        assertEquals(0, detail.getKatecX(), 0);
    }

    @Test
    public void address_fallsBackToLotAddress() throws OpinetException {
        StationDetail detail = OpinetDetailResponse.parse(wrap(
                "{\"NEW_ADR\":\"  \",\"VAN_ADR\":\"서울특별시 서초구 서초동 1-1\"}"));
        assertEquals("서울특별시 서초구 서초동 1-1", detail.getAddress());
    }

    @Test
    public void prices_stringNumbersAndUnknownProducts() throws OpinetException {
        StationDetail detail = OpinetDetailResponse.parse(wrap("{\"OIL_PRICE\":["
                + "{\"PRODCD\":\"C004\",\"PRICE\":1405},"           // 등유: 앱에서 다루지 않는다.
                + "{\"PRODCD\":\"K015\",\"PRICE\":\"1,019\"},"      // 문자열 숫자
                + "{\"PRODCD\":\"B034\",\"PRICE\":\"\"},"           // 가격 없음
                + "{\"PRODCD\":\"B027\",\"PRICE\":0},"              // 가격 0
                + "{\"PRODCD\":\"D047\",\"PRICE\":1519.0},"
                + "{\"PRODCD\":\"D047\",\"PRICE\":1600},"           // 중복은 처음 것만
                + "\"잘못된 항목\"]}"));
        List<FuelType> types = fuelTypes(detail);
        assertEquals(2, types.size());
        assertEquals(FuelType.DIESEL, types.get(0));
        assertEquals(FuelType.LPG, types.get(1));
        assertEquals(1519, detail.getPrices().get(0).getPrice());
        assertEquals(1019, detail.getPrices().get(1).getPrice());
    }

    @Test
    public void brandCode_acceptsAroundFieldName() throws OpinetException {
        StationDetail detail = OpinetDetailResponse.parse(wrap("{\"POLL_DIV_CD\":\"GSC\"}"));
        assertEquals("GSC", detail.getBrandCode());
    }

    @Test
    public void selfService_estimatedFromNameWhenFieldMissing() throws OpinetException {
        StationDetail self = OpinetDetailResponse.parse(wrap("{\"OS_NM\":\"양재셀프주유소\"}"));
        assertTrue(self.isSelfService());
        assertTrue(self.isSelfServiceEstimated());

        StationDetail normal = OpinetDetailResponse.parse(wrap("{\"OS_NM\":\"도곡주유소\"}"));
        assertFalse(normal.isSelfService());
        assertTrue(normal.isSelfServiceEstimated());
    }

    @Test
    public void selfService_fieldOverridesName() throws OpinetException {
        StationDetail detail = OpinetDetailResponse.parse(wrap("{\"OS_NM\":\"셀프주유소\",\"SELF_YN\":\"N\"}"));
        assertFalse(detail.isSelfService());
        assertFalse(detail.isSelfServiceEstimated());
    }

    /** 주유소가 없는 응답(폐업 등)은 다른 오류와 구분한다. */
    @Test(expected = StationNotFoundException.class)
    public void parse_emptyOil_throwsNotFound() throws OpinetException {
        OpinetDetailResponse.parse("{\"RESULT\":{\"OIL\":[]}}");
    }

    @Test
    public void parse_noResult_throwsButNotNotFound() {
        try {
            OpinetDetailResponse.parse("{\"ERROR\":\"한도 초과\"}");
            fail();
        } catch (OpinetException e) {
            assertFalse(e instanceof StationNotFoundException);
        }
    }

    @Test(expected = OpinetException.class)
    public void parse_notJson_throws() throws OpinetException {
        OpinetDetailResponse.parse("<html><body>error</body></html>");
    }

    @Test(expected = OpinetException.class)
    public void parse_emptyBody_throws() throws OpinetException {
        OpinetDetailResponse.parse("");
    }
}
