package com.example.findcheapgas.data.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.data.remote.OpinetAroundResponse;
import com.example.findcheapgas.data.remote.OpinetAverageResponse;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.model.AveragePrice;
import com.example.findcheapgas.model.AveragePrices;
import com.example.findcheapgas.model.DailyAveragePrice;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.util.PriceTrend;
import com.example.findcheapgas.util.SidoMatcher;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 가짜 평균가(assets/mock/avg_*.json)가 실제 응답과 같은 구조로 읽히고,
 * 강남역 주변 가짜 주유소 가격과 어울리는지 확인한다 (평균보다 싼 곳과 비싼 곳이 모두 있음).
 */
public class MockAverageDataTest {

    private static final File MOCK_DIR = new File("src/main/assets/mock");
    private static final String GANGNAM_ADDRESS = "서울특별시 강남구 테헤란로 152";

    private static String read(String name) throws IOException {
        return new String(Files.readAllBytes(new File(MOCK_DIR, name).toPath()), StandardCharsets.UTF_8);
    }

    private static AveragePrices averages() throws IOException, OpinetException {
        return new AveragePrices(OpinetAverageResponse.parseNational(read("avg_all.json")),
                OpinetAverageResponse.parseSido(read("avg_sido.json")));
    }

    private static List<OpinetAroundResponse.Oil> stations(FuelType type) throws IOException, OpinetException {
        return OpinetAroundResponse.parse(read("around_" + type.getCode() + ".json"));
    }

    @Test
    public void everyFuelHasNationalAndSeoulAverage() throws IOException, OpinetException {
        AveragePrices averages = averages();
        for (FuelType type : FuelType.values()) {
            assertNotNull(type.name(), averages.nationalOf(type));
            assertNotNull(type.name(), SidoMatcher.find(GANGNAM_ADDRESS, averages.getSido(), type));
        }
    }

    @Test
    public void mockStationsAreBothCheaperAndPricierThanAverages() throws IOException, OpinetException {
        AveragePrices averages = averages();
        for (FuelType type : FuelType.values()) {
            AveragePrice national = averages.nationalOf(type);
            AveragePrice seoul = SidoMatcher.find(GANGNAM_ADDRESS, averages.getSido(), type);
            for (AveragePrice average : new AveragePrice[]{national, seoul}) {
                int cheaper = 0;
                int pricier = 0;
                for (OpinetAroundResponse.Oil oil : stations(type)) {
                    if (oil.price < average.getPrice()) {
                        cheaper++;
                    } else if (oil.price > average.getPrice()) {
                        pricier++;
                    }
                }
                assertTrue(type + " 평균보다 싼 곳", cheaper > 0);
                assertTrue(type + " 평균보다 비싼 곳", pricier > 0);
            }
        }
    }

    @Test
    public void recentHasSevenDaysForEveryFuel_lastDayMatchesNational() throws IOException, OpinetException {
        List<DailyAveragePrice> recent = OpinetAverageResponse.parseRecent(read("avg_recent.json"));
        AveragePrices averages = averages();
        for (FuelType type : FuelType.values()) {
            List<DailyAveragePrice> points = PriceTrend.forFuel(recent, type);
            assertEquals(type.name(), 7, points.size());
            assertEquals(type.name(), averages.nationalOf(type).getPrice(),
                    points.get(points.size() - 1).getPrice(), 1e-9);
        }
    }
}
