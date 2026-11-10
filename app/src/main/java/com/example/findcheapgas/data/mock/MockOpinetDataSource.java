package com.example.findcheapgas.data.mock;

import android.content.Context;

import com.example.findcheapgas.data.remote.OpinetAroundResponse;
import com.example.findcheapgas.data.remote.OpinetAverageResponse;
import com.example.findcheapgas.data.remote.OpinetDetailResponse;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.data.remote.OpinetSearchResponse;
import com.example.findcheapgas.data.remote.StationNotFoundException;
import com.example.findcheapgas.model.AveragePrice;
import com.example.findcheapgas.model.DailyAveragePrice;
import com.example.findcheapgas.model.StationDetail;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.model.StationSearchResult;
import com.example.findcheapgas.util.CoordinateConverter;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 개발용 가짜 데이터 (강남역 주변 주유소).
 * - 주변 주유소: assets/mock/around_{유종코드}.json. 실제 API처럼 요청 위치에서 거리를 다시 계산하고,
 *   최대 반경(5km) 밖 주유소는 뺀다. 화면 반경 필터와 정렬은 StationListBuilder가 한다.
 * - 상세정보: assets/mock/detail/{주유소 ID}.json. 주변 데이터의 모든 주유소가 있고, 가격은 주변 데이터와 같다.
 * - 상호 검색: 상세정보 파일 전체에서 상호에 검색어가 들어간 주유소를 찾는다. 상세 파일이 상호 검색 응답과
 *   같은 필드(UNI_ID, POLL_DIV_CO, OS_NM, NEW_ADR, GIS_X_COOR, GIS_Y_COOR)를 가지고 있어서 같은 해석을 쓴다.
 * - 평균가: assets/mock/avg_all.json(전국), avg_sido.json(시도별), avg_recent.json(최근 7일 전국 일일).
 *   실제 응답처럼 여러 유종이 한 번에 들어 있고, 강남역 주변 가짜 주유소 가격 사이에 오도록 정했다
 *   (평균보다 싼 주유소와 비싼 주유소가 모두 나온다).
 */
public class MockOpinetDataSource {

    // 로딩 표시를 확인할 수 있도록 실제 통신처럼 잠깐 기다린다.
    private static final long FAKE_DELAY_MS = 300;

    // 가짜 주유소는 모두 강남역에서 5km 안에 있다. 조회 위치가 이보다 멀면 주유소가 적거나 없다.
    public static final double CENTER_LATITUDE = 37.4979;
    public static final double CENTER_LONGITUDE = 127.0276;
    private static final double AREA_RADIUS_METERS = 5000;

    private static final String DETAIL_DIR = "mock/detail";
    private static final String NATIONAL_AVERAGE_FILE = "mock/avg_all.json";
    private static final String SIDO_AVERAGE_FILE = "mock/avg_sido.json";
    private static final String RECENT_AVERAGE_FILE = "mock/avg_recent.json";

    private final Context appContext;

    public MockOpinetDataSource(Context context) {
        appContext = context.getApplicationContext();
    }

    public List<OpinetAroundResponse.Oil> fetchAround(StationQuery query, CoordinateConverter.Katec katec)
            throws IOException, OpinetException {
        try {
            Thread.sleep(FAKE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String fileName = "mock/around_" + query.getFuelType().getCode() + ".json";
        List<OpinetAroundResponse.Oil> all = OpinetAroundResponse.parse(readAsset(fileName));

        List<OpinetAroundResponse.Oil> inRadius = new ArrayList<>();
        for (OpinetAroundResponse.Oil oil : all) {
            double distance = CoordinateConverter.distanceMeters(katec, oil.katecX, oil.katecY);
            if (distance <= StationQuery.FETCH_RADIUS_METERS) {
                oil.distance = distance;
                inRadius.add(oil);
            }
        }
        return inRadius;
    }

    public StationDetail fetchDetail(String stationId) throws IOException, OpinetException {
        try {
            Thread.sleep(FAKE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String body;
        try {
            body = readAsset(DETAIL_DIR + "/" + stationId + ".json");
        } catch (FileNotFoundException e) {
            // 실제 API에서 없는 주유소를 조회한 것과 같게 오피넷 오류로 처리한다.
            throw new StationNotFoundException("가짜 상세 데이터 없음: " + stationId, e);
        }
        return OpinetDetailResponse.parse(body);
    }

    public List<StationSearchResult> searchByName(String keyword) throws IOException, OpinetException {
        try {
            Thread.sleep(FAKE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<StationSearchResult> all = new ArrayList<>();
        String[] files = appContext.getAssets().list(DETAIL_DIR);
        if (files != null) {
            for (String file : files) {
                all.addAll(OpinetSearchResponse.parse(readAsset(DETAIL_DIR + "/" + file)));
            }
        }
        return filterByName(all, keyword);
    }

    public List<AveragePrice> fetchNationalAverages() throws IOException, OpinetException {
        fakeDelay();
        return OpinetAverageResponse.parseNational(readAsset(NATIONAL_AVERAGE_FILE));
    }

    public List<AveragePrice> fetchSidoAverages() throws IOException, OpinetException {
        fakeDelay();
        return OpinetAverageResponse.parseSido(readAsset(SIDO_AVERAGE_FILE));
    }

    public List<DailyAveragePrice> fetchRecentAverages() throws IOException, OpinetException {
        fakeDelay();
        return OpinetAverageResponse.parseRecent(readAsset(RECENT_AVERAGE_FILE));
    }

    private static void fakeDelay() {
        try {
            Thread.sleep(FAKE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 상호에 검색어가 들어간 주유소. 띄어쓰기와 영문 대소문자는 무시한다. */
    public static List<StationSearchResult> filterByName(List<StationSearchResult> all, String keyword) {
        String needle = compact(keyword);
        List<StationSearchResult> result = new ArrayList<>();
        if (needle.isEmpty()) {
            return result;
        }
        for (StationSearchResult station : all) {
            if (compact(station.getName()).contains(needle)) {
                result.add(station);
            }
        }
        return result;
    }

    /** 조회 위치가 가짜 데이터가 있는 곳(강남역 주변 5km) 밖인지 */
    public static boolean isOutsideMockArea(double latitude, double longitude) {
        CoordinateConverter.Katec center = CoordinateConverter.toKatec(CENTER_LATITUDE, CENTER_LONGITUDE);
        CoordinateConverter.Katec point = CoordinateConverter.toKatec(latitude, longitude);
        return CoordinateConverter.distanceMeters(center, point.x, point.y) > AREA_RADIUS_METERS;
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String readAsset(String fileName) throws IOException {
        try (InputStream in = appContext.getAssets().open(fileName);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
