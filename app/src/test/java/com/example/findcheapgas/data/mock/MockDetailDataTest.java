package com.example.findcheapgas.data.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.data.remote.OpinetAroundResponse;
import com.example.findcheapgas.data.remote.OpinetDetailResponse;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.model.FuelPrice;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.StationDetail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * 가짜 상세 데이터(assets/mock/detail)가 가짜 주변 데이터(assets/mock/around_*.json)와 맞는지 확인한다.
 * - 주변 데이터의 모든 주유소에 상세 파일이 있다.
 * - 상세의 판매 유종과 가격이 주변 데이터와 같다.
 * - LPG 충전소는 LPG만 판다.
 */
public class MockDetailDataTest {

    // 단위 테스트는 app 모듈 폴더에서 실행된다.
    private static final File MOCK_DIR = new File("src/main/assets/mock");

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** 주유소 ID → (유종 → 가격) */
    private static Map<String, Map<FuelType, Integer>> aroundPrices() throws IOException, OpinetException {
        Map<String, Map<FuelType, Integer>> result = new HashMap<>();
        for (FuelType type : FuelType.values()) {
            File file = new File(MOCK_DIR, "around_" + type.getCode() + ".json");
            for (OpinetAroundResponse.Oil oil : OpinetAroundResponse.parse(read(file))) {
                Map<FuelType, Integer> prices = result.get(oil.uniId);
                if (prices == null) {
                    prices = new HashMap<>();
                    result.put(oil.uniId, prices);
                }
                prices.put(type, oil.price);
            }
        }
        return result;
    }

    @Test
    public void everyAroundStationHasMatchingDetail() throws IOException, OpinetException {
        Map<String, Map<FuelType, Integer>> around = aroundPrices();
        assertFalse(around.isEmpty());

        for (Map.Entry<String, Map<FuelType, Integer>> entry : around.entrySet()) {
            String id = entry.getKey();
            File file = new File(MOCK_DIR, "detail/" + id + ".json");
            assertTrue("상세 파일 없음: " + id, file.exists());

            StationDetail detail = OpinetDetailResponse.parse(read(file));
            assertEquals(id, detail.getId());
            assertNotNull("주소 없음: " + id, detail.getAddress());

            Map<FuelType, Integer> detailPrices = new HashMap<>();
            for (FuelPrice price : detail.getPrices()) {
                detailPrices.put(price.getFuelType(), price.getPrice());
            }
            assertEquals("가격 불일치: " + id, entry.getValue(), detailPrices);

            if (detailPrices.containsKey(FuelType.LPG)) {
                assertEquals("LPG 충전소가 다른 유종을 판다: " + id, 1, detailPrices.size());
            }
        }
    }

    @Test
    public void detailFilesOnlyForAroundStations() throws IOException, OpinetException {
        Map<String, Map<FuelType, Integer>> around = aroundPrices();
        File[] files = new File(MOCK_DIR, "detail").listFiles();
        assertNotNull(files);
        assertEquals(around.size(), files.length);
    }
}
