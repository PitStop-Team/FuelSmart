package com.example.findcheapgas.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.findcheapgas.model.AveragePrice;
import com.example.findcheapgas.model.DailyAveragePrice;
import com.example.findcheapgas.model.FuelType;

import org.junit.Test;

import java.util.List;

public class OpinetAverageResponseTest {

    private static String wrap(String oils) {
        return "{\"RESULT\":{\"OIL\":[" + oils + "]}}";
    }

    @Test
    public void parseNational_allFuelsAtOnce_skipsKerosene() throws OpinetException {
        List<AveragePrice> prices = OpinetAverageResponse.parseNational(wrap(
                "{\"TRADE_DT\":\"20261106\",\"PRODCD\":\"B034\",\"PRODNM\":\"고급휘발유\",\"PRICE\":\"1919.44\",\"DIFF\":\"-0.10\"},"
                        + "{\"TRADE_DT\":\"20261106\",\"PRODCD\":\"B027\",\"PRODNM\":\"휘발유\",\"PRICE\":1667.33,\"DIFF\":\"-0.23\"},"
                        + "{\"TRADE_DT\":\"20261106\",\"PRODCD\":\"D047\",\"PRODNM\":\"자동차용경유\",\"PRICE\":\"1532.22\",\"DIFF\":\"+0.39\"},"
                        + "{\"TRADE_DT\":\"20261106\",\"PRODCD\":\"C004\",\"PRODNM\":\"실내등유\",\"PRICE\":\"1295.23\",\"DIFF\":\"-0.72\"},"
                        + "{\"TRADE_DT\":\"20261106\",\"PRODCD\":\"K015\",\"PRODNM\":\"자동차용부탄\",\"PRICE\":\"1052.64\",\"DIFF\":\"-0.07\"}"));

        assertEquals(4, prices.size());
        assertEquals(FuelType.PREMIUM_GASOLINE, prices.get(0).getFuelType());
        assertEquals(1919.44, prices.get(0).getPrice(), 1e-9);
        assertEquals(1667.33, prices.get(1).getPrice(), 1e-9);
        assertNull(prices.get(1).getSidoName());
        assertEquals(FuelType.LPG, prices.get(3).getFuelType());
    }

    @Test
    public void parseNational_missingOrEmptyFieldsSkipOnlyThatRow() throws OpinetException {
        List<AveragePrice> prices = OpinetAverageResponse.parseNational(wrap(
                "{\"PRODCD\":\"B027\"},"                       // 가격 없음
                        + "{\"PRODCD\":\"D047\",\"PRICE\":\"\"},"   // 가격 비어 있음
                        + "{\"PRICE\":\"1500\"},"                   // 유종 없음
                        + "\"문자열\","                             // 객체가 아님
                        + "{\"PRODCD\":\"K015\",\"PRICE\":\" 1,004.18 \"},"
                        + "{\"PRODCD\":\"K015\",\"PRICE\":\"999\"}")); // 같은 유종은 처음 것

        assertEquals(1, prices.size());
        assertEquals(FuelType.LPG, prices.get(0).getFuelType());
        assertEquals(1004.18, prices.get(0).getPrice(), 1e-9);
    }

    @Test
    public void parseNational_emptyOilIsEmptyList() throws OpinetException {
        assertTrue(OpinetAverageResponse.parseNational(wrap("")).isEmpty());
    }

    @Test
    public void parseNational_notJsonThrows() {
        try {
            OpinetAverageResponse.parseNational("<html>오류</html>");
            fail();
        } catch (OpinetException expected) {
            // 형식 오류
        }
    }

    @Test
    public void parseSido_readsNameAndCode_skipsRowsWithoutName() throws OpinetException {
        List<AveragePrice> prices = OpinetAverageResponse.parseSido(wrap(
                "{\"SIDOCD\":\"01\",\"SIDONM\":\"서울\",\"PRODCD\":\"B027\",\"PRICE\":\"1739.63\",\"DIFF\":\"-0.79\"},"
                        + "{\"SIDOCD\":\"01\",\"SIDONM\":\"서울\",\"PRODCD\":\"C004\",\"PRICE\":\"1531.58\"},"
                        + "{\"SIDOCD\":\"04\",\"PRODCD\":\"B027\",\"PRICE\":\"1680\"},"
                        + "{\"SIDOCD\":\"20\",\"SIDONM\":\"전남광주\",\"PRODCD\":\"D047\",\"PRICE\":1530.4}"));

        assertEquals(2, prices.size());
        assertEquals("서울", prices.get(0).getSidoName());
        assertEquals("01", prices.get(0).getSidoCode());
        assertEquals(1739.63, prices.get(0).getPrice(), 1e-9);
        assertEquals("전남광주", prices.get(1).getSidoName());
        assertEquals(FuelType.DIESEL, prices.get(1).getFuelType());
    }

    @Test
    public void parseRecent_sortsByDate_skipsBadDates() throws OpinetException {
        List<DailyAveragePrice> prices = OpinetAverageResponse.parseRecent(wrap(
                "{\"DATE\":\"20261106\",\"PRODCD\":\"B027\",\"PRICE\":\"1668.42\"},"
                        + "{\"DATE\":\"20261031\",\"PRODCD\":\"B027\",\"PRICE\":\"1656.18\"},"
                        + "{\"DATE\":\"2026-11-01\",\"PRODCD\":\"B027\",\"PRICE\":\"1658\"},"
                        + "{\"PRODCD\":\"B027\",\"PRICE\":\"1658\"},"
                        + "{\"DATE\":\"20261101\",\"PRODCD\":\"C004\",\"PRICE\":\"1313\"},"
                        + "{\"DATE\":\"20261101\",\"PRODCD\":\"D047\",\"PRICE\":\"1534.10\"}"));

        assertEquals(3, prices.size());
        assertEquals("20261031", prices.get(0).getDate());
        assertEquals("20261101", prices.get(1).getDate());
        assertEquals(FuelType.DIESEL, prices.get(1).getFuelType());
        assertEquals("20261106", prices.get(2).getDate());
        assertEquals(1668.42, prices.get(2).getPrice(), 1e-9);
    }
}
