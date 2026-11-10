package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.data.remote.OpinetAroundResponse;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.model.StationSaving;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 조회 위치를 바꾸면 거리, 이동 비용, 기준 주유소, 예상 이득이 그 위치 기준으로 다시 계산되는지.
 * 가짜 주변 데이터(휘발유)를 강남역과 선릉역 기준으로 각각 읽어 비교한다.
 */
public class OriginRecalculationTest {

    private static final double GANGNAM_LAT = 37.4979;
    private static final double GANGNAM_LNG = 127.0276;
    private static final double SEOLLEUNG_LAT = 37.5045;
    private static final double SEOLLEUNG_LNG = 127.0490;

    /** 가짜 데이터 전체를 GasStation으로 (StationRepository와 같은 변환) */
    private static List<GasStation> mockStations() throws IOException, OpinetException {
        File file = new File("src/main/assets/mock/around_B027.json");
        String body = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        List<GasStation> stations = new ArrayList<>();
        for (OpinetAroundResponse.Oil oil : OpinetAroundResponse.parse(body)) {
            CoordinateConverter.Wgs84 wgs84 = CoordinateConverter.toWgs84(oil.katecX, oil.katecY);
            stations.add(new GasStation(oil.uniId, oil.brandCode, oil.name, oil.price, oil.distance,
                    oil.katecX, oil.katecY, wgs84.latitude, wgs84.longitude));
        }
        return stations;
    }

    /** 조회 위치 기준 거리로 바꾸고, 오피넷처럼 5km 밖은 뺀다. */
    private static List<GasStation> fetchedAt(double lat, double lng) throws IOException, OpinetException {
        List<GasStation> result = new ArrayList<>();
        for (GasStation station : StationDistances.fromOrigin(mockStations(), lat, lng)) {
            if (station.getDistance() <= StationQuery.FETCH_RADIUS_METERS) {
                result.add(station);
            }
        }
        return result;
    }

    private static List<StationSaving> build(List<GasStation> fetched) {
        return StationListBuilder.build(fetched, SearchRadius.KM_3, SortOrder.SAVING, CarInfo.DEFAULT,
                BrandSettings.EMPTY, BrandFilter.ALL);
    }

    private static StationSaving find(List<StationSaving> list, String id) {
        for (StationSaving saving : list) {
            if (saving.getStation().getId().equals(id)) {
                return saving;
            }
        }
        throw new AssertionError(id + " 없음");
    }

    @Test
    public void distances_areMeasuredFromOrigin() throws IOException, OpinetException {
        GasStation station = mockStations().get(0);
        CoordinateConverter.Katec origin = CoordinateConverter.toKatec(SEOLLEUNG_LAT, SEOLLEUNG_LNG);
        double expected = CoordinateConverter.distanceMeters(origin, station.getKatecX(), station.getKatecY());

        GasStation moved = StationDistances.fromOrigin(mockStations(), SEOLLEUNG_LAT, SEOLLEUNG_LNG).get(0);
        assertEquals(expected, moved.getDistance(), 1e-6);
        assertEquals(station.getId(), moved.getId());
        assertEquals(station.getPrice(), moved.getPrice());
    }

    @Test
    public void baseline_isNearestToOrigin() throws IOException, OpinetException {
        List<StationSaving> gangnam = build(fetchedAt(GANGNAM_LAT, GANGNAM_LNG));
        List<StationSaving> seolleung = build(fetchedAt(SEOLLEUNG_LAT, SEOLLEUNG_LNG));

        // 강남역 기준: 역삼셀프주유소(약 0.4km), 선릉역 기준: 선릉셀프주유소
        assertEquals("A0900001", gangnam.get(0).getBaseline().getId());
        assertEquals("A0900008", seolleung.get(0).getBaseline().getId());
        for (StationSaving saving : seolleung) {
            assertTrue(saving.getBaseline().getDistance() <= saving.getStation().getDistance());
        }
    }

    @Test
    public void travelCostAndSaving_changeWithOrigin() throws IOException, OpinetException {
        List<StationSaving> gangnam = build(fetchedAt(GANGNAM_LAT, GANGNAM_LNG));
        List<StationSaving> seolleung = build(fetchedAt(SEOLLEUNG_LAT, SEOLLEUNG_LNG));

        // 테헤란로주유소는 두 조회 위치 모두 3km 안에 있다.
        StationSaving fromGangnam = find(gangnam, "A0900004");
        StationSaving fromSeolleung = find(seolleung, "A0900004");
        assertNotEquals(fromGangnam.getStraightDistance(), fromSeolleung.getStraightDistance(), 1);
        assertNotEquals(fromGangnam.getTravelCost(), fromSeolleung.getTravelCost(), 1);
        assertNotEquals(fromGangnam.getSavingWon(), fromSeolleung.getSavingWon());

        // 이동 비용 = 직선거리 × 1.4 ÷ 연비 × 표시 가격 (조회 위치에서 잰 거리)
        double km = fromSeolleung.getStation().getDistance() / 1000.0;
        double expected = km * SavingCalculator.ROAD_DISTANCE_FACTOR / 12.0 * fromSeolleung.getStation().getPrice();
        assertEquals(expected, fromSeolleung.getTravelCost(), 0.01);
    }

    @Test
    public void listsDiffer_betweenOrigins() throws IOException, OpinetException {
        List<StationSaving> gangnam = build(fetchedAt(GANGNAM_LAT, GANGNAM_LNG));
        List<StationSaving> seolleung = build(fetchedAt(SEOLLEUNG_LAT, SEOLLEUNG_LNG));
        List<String> gangnamIds = new ArrayList<>();
        for (StationSaving saving : gangnam) {
            gangnamIds.add(saving.getStation().getId());
        }
        List<String> seolleungIds = new ArrayList<>();
        for (StationSaving saving : seolleung) {
            seolleungIds.add(saving.getStation().getId());
        }
        assertNotEquals(gangnamIds, seolleungIds);
    }
}
