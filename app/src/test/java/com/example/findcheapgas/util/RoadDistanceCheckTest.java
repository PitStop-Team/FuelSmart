package com.example.findcheapgas.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.example.findcheapgas.BuildConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 도로 거리 계수(SavingCalculator.ROAD_DISTANCE_FACTOR) 검증용 측정. 앱 기능이 아니다.
 *
 * 강남역에서 반경 5km 안의 실제 주유소(카카오 로컬 API, OL7)를 거리 구간별로 고르게 뽑고,
 * 하버사인 직선거리와 카카오모빌리티 자동차 길찾기 도로 거리를 비교해서 docs/road-distance-check.csv로 저장한다.
 * 오피넷 API는 부르지 않는다.
 *
 * 평소 테스트에서는 건너뛰고, 아래처럼 실행할 때만 외부 API를 부른다.
 * gradlew.bat :app:testDebugUnitTest -ProadDistanceCheck=true --tests "*RoadDistanceCheckTest"
 */
public class RoadDistanceCheckTest {

    // 강남역
    private static final double ORIGIN_LAT = 37.4979;
    private static final double ORIGIN_LNG = 127.0276;

    private static final double EARTH_RADIUS_M = 6_371_000;
    private static final double MAX_DISTANCE_M = 5_000;
    // 거리 구간 경계(m)와 구간별 표본 수
    private static final double[] BUCKET_BOUNDS = {0, 1_000, 3_000, 5_000};
    private static final String[] BUCKET_NAMES = {"0~1km", "1~3km", "3~5km"};
    private static final int SAMPLES_PER_BUCKET = 7;

    private static final String LOCAL_URL = "https://dapi.kakao.com/v2/local/search/category.json";
    private static final String DIRECTIONS_URL = "https://apis-navi.kakaomobility.com/v1/directions";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private String authorization;

    @Test
    public void measureRoadDistanceFactor() throws Exception {
        assumeTrue("-ProadDistanceCheck=true일 때만 실행",
                "true".equals(System.getProperty("roadDistanceCheck")));
        String key = BuildConfig.KAKAO_REST_API_KEY;
        assertFalse("local.properties에 KAKAO_REST_API_KEY가 없습니다", key == null || key.isEmpty());
        authorization = "KakaoAK " + key;

        List<Place> samples = pickSamples(searchStations());
        System.out.println("표본 " + samples.size() + "곳");

        List<Place> measured = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            Place place = samples.get(i);
            try {
                place.roadM = roadDistanceWithRetry(place);
                measured.add(place);
            } catch (PermissionException e) {
                // 첫 길찾기 호출이든 중간이든 권한 오류면 더 부르지 않는다.
                fail("길찾기 API 권한 오류: " + e.getMessage());
            } catch (RouteException e) {
                place.note = e.getMessage();
                excluded.add(place.name + ": " + e.getMessage());
            }
            Thread.sleep(200);
        }

        writeCsv(samples);
        printSummary(measured, excluded);
    }

    // ---------- 표본 수집 ----------

    /** 강남역 기준 가까운 45곳과, 4km 떨어진 네 방향 중심 주변을 검색해서 5km 안 주유소를 모은다. */
    private List<Place> searchStations() throws IOException {
        Map<String, Place> byId = new LinkedHashMap<>();
        for (int page = 1; page <= 3; page++) {
            if (!searchCategory(ORIGIN_LAT, ORIGIN_LNG, 5_000, page, byId)) {
                break;
            }
        }
        double dLat = 4_000 / 111_000.0;
        double dLng = 4_000 / (111_000.0 * Math.cos(Math.toRadians(ORIGIN_LAT)));
        double[][] centers = {
                {ORIGIN_LAT + dLat, ORIGIN_LNG}, {ORIGIN_LAT - dLat, ORIGIN_LNG},
                {ORIGIN_LAT, ORIGIN_LNG + dLng}, {ORIGIN_LAT, ORIGIN_LNG - dLng}};
        for (double[] c : centers) {
            searchCategory(c[0], c[1], 1_500, 1, byId);
        }

        List<Place> inRange = new ArrayList<>();
        for (Place place : byId.values()) {
            if (place.straightM <= MAX_DISTANCE_M) {
                inRange.add(place);
            }
        }
        System.out.println("5km 안 주유소 " + inRange.size() + "곳 수집");
        return inRange;
    }

    /** 카테고리 검색 한 페이지. 다음 페이지가 있으면 true. */
    private boolean searchCategory(double lat, double lng, int radius, int page,
                                   Map<String, Place> out) throws IOException {
        HttpUrl url = HttpUrl.get(LOCAL_URL).newBuilder()
                .addQueryParameter("category_group_code", "OL7")
                .addQueryParameter("x", String.valueOf(lng))
                .addQueryParameter("y", String.valueOf(lat))
                .addQueryParameter("radius", String.valueOf(radius))
                .addQueryParameter("sort", "distance")
                .addQueryParameter("size", "15")
                .addQueryParameter("page", String.valueOf(page))
                .build();
        JsonObject body = getJson(url, "로컬 검색");
        for (JsonElement element : body.getAsJsonArray("documents")) {
            JsonObject doc = element.getAsJsonObject();
            // OL7에는 충전소도 섞여 있어서 주유소만 남긴다.
            if (!doc.get("category_name").getAsString().contains("주유소")) {
                continue;
            }
            Place place = new Place(doc.get("id").getAsString(),
                    doc.get("place_name").getAsString(),
                    doc.get("y").getAsDouble(), doc.get("x").getAsDouble());
            out.put(place.id, place);
        }
        return !body.getAsJsonObject("meta").get("is_end").getAsBoolean();
    }

    /** 구간마다 거리순으로 늘어놓고 처음부터 끝까지 같은 간격으로 뽑는다. */
    static List<Place> pickSamples(List<Place> places) {
        List<Place> samples = new ArrayList<>();
        for (int b = 0; b < BUCKET_NAMES.length; b++) {
            List<Place> bucket = new ArrayList<>();
            for (Place place : places) {
                if (bucketOf(place.straightM) == b) {
                    bucket.add(place);
                }
            }
            Collections.sort(bucket, Comparator.comparingDouble(p -> p.straightM));
            int n = bucket.size();
            int k = Math.min(SAMPLES_PER_BUCKET, n);
            System.out.println(BUCKET_NAMES[b] + ": 후보 " + n + "곳 중 " + k + "곳");
            for (int i = 0; i < k; i++) {
                int index = k == 1 ? 0 : (int) Math.round(i * (n - 1) / (double) (k - 1));
                samples.add(bucket.get(index));
            }
        }
        return samples;
    }

    static int bucketOf(double meters) {
        for (int b = 0; b < BUCKET_NAMES.length; b++) {
            if (meters < BUCKET_BOUNDS[b + 1] || (b == BUCKET_NAMES.length - 1 && meters <= BUCKET_BOUNDS[b + 1])) {
                return b;
            }
        }
        return -1;
    }

    static double haversineM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    // ---------- 길찾기 ----------

    /** 실패하면 한 번만 다시 시도한다. 권한 오류는 다시 시도하지 않는다. */
    private double roadDistanceWithRetry(Place place) throws Exception {
        try {
            return roadDistance(place);
        } catch (RouteException first) {
            Thread.sleep(1_000);
            try {
                return roadDistance(place);
            } catch (RouteException second) {
                throw new RouteException("재시도 실패 (" + second.getMessage() + ")");
            }
        }
    }

    private double roadDistance(Place place) throws PermissionException, RouteException {
        HttpUrl url = HttpUrl.get(DIRECTIONS_URL).newBuilder()
                .addQueryParameter("origin", ORIGIN_LNG + "," + ORIGIN_LAT)
                .addQueryParameter("destination", place.lng + "," + place.lat)
                .build();
        JsonObject body;
        try {
            body = getJson(url, "길찾기");
        } catch (PermissionException e) {
            throw e;
        } catch (IOException e) {
            throw new RouteException(e.getMessage());
        }
        JsonArray routes = body.getAsJsonArray("routes");
        if (routes == null || routes.size() == 0) {
            throw new RouteException("routes 없음");
        }
        JsonObject route = routes.get(0).getAsJsonObject();
        int resultCode = route.get("result_code").getAsInt();
        if (resultCode != 0) {
            throw new RouteException("result_code " + resultCode + " " + route.get("result_msg").getAsString());
        }
        return route.getAsJsonObject("summary").get("distance").getAsDouble();
    }

    // ---------- HTTP ----------

    /** 인증 헤더는 요청에만 쓰고, 오류 메시지에는 상태 코드와 응답 본문만 담는다. */
    private JsonObject getJson(HttpUrl url, String label) throws IOException {
        Request request = new Request.Builder().url(url).header("Authorization", authorization).build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            if (response.code() == 401 || response.code() == 403) {
                throw new PermissionException(label + " HTTP " + response.code() + " " + text);
            }
            if (!response.isSuccessful()) {
                throw new IOException(label + " HTTP " + response.code() + " " + text);
            }
            return JsonParser.parseString(text).getAsJsonObject();
        }
    }

    // ---------- 결과 ----------

    private static void writeCsv(List<Place> samples) throws IOException {
        File file = new File(System.getProperty("roadDistanceCheckOutput", "road-distance-check.csv"));
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IOException("폴더를 만들 수 없음: " + dir);
        }
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
            writer.write("주유소,직선거리(km),도로 거리(km),비율(도로/직선),비고\n");
            for (Place p : samples) {
                if (p.roadM > 0) {
                    writer.write(String.format(Locale.US, "%s,%.3f,%.3f,%.3f,\n",
                            csv(p.name), p.straightM / 1000, p.roadM / 1000, p.roadM / p.straightM));
                } else {
                    writer.write(String.format(Locale.US, "%s,%.3f,,,%s\n",
                            csv(p.name), p.straightM / 1000, csv("제외: " + p.note)));
                }
            }
        }
        System.out.println("CSV 저장: " + file.getAbsolutePath());
    }

    private static String csv(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static void printSummary(List<Place> measured, List<String> excluded) {
        StringBuilder sb = new StringBuilder("\n===== 도로 거리 계수 측정 결과 =====\n");
        sb.append("측정 ").append(measured.size()).append("곳, 제외 ").append(excluded.size()).append("곳\n");
        for (String reason : excluded) {
            sb.append("  제외 ").append(reason).append('\n');
        }
        if (measured.isEmpty()) {
            System.out.println(sb);
            return;
        }
        List<Double> ratios = new ArrayList<>();
        double roadSum = 0;
        double straightSum = 0;
        Place min = measured.get(0);
        Place max = measured.get(0);
        for (Place p : measured) {
            ratios.add(p.ratio());
            roadSum += p.roadM;
            straightSum += p.straightM;
            if (p.ratio() < min.ratio()) {
                min = p;
            }
            if (p.ratio() > max.ratio()) {
                max = p;
            }
        }
        sb.append(String.format(Locale.US, "평균 비율 %.3f%n", mean(ratios)));
        sb.append(String.format(Locale.US, "중앙값 %.3f%n", median(ratios)));
        sb.append(String.format(Locale.US, "합계 기준 비율 %.3f (도로 %.2fkm / 직선 %.2fkm)%n",
                roadSum / straightSum, roadSum / 1000, straightSum / 1000));
        for (int b = 0; b < BUCKET_NAMES.length; b++) {
            List<Double> bucket = new ArrayList<>();
            double bRoad = 0;
            double bStraight = 0;
            for (Place p : measured) {
                if (bucketOf(p.straightM) == b) {
                    bucket.add(p.ratio());
                    bRoad += p.roadM;
                    bStraight += p.straightM;
                }
            }
            if (bucket.isEmpty()) {
                sb.append(BUCKET_NAMES[b]).append(": 표본 없음\n");
            } else {
                sb.append(String.format(Locale.US, "%s: %d곳, 평균 %.3f, 중앙값 %.3f, 합계 기준 %.3f%n",
                        BUCKET_NAMES[b], bucket.size(), mean(bucket), median(bucket), bRoad / bStraight));
            }
        }
        sb.append(String.format(Locale.US, "최솟값 %.3f %s (직선 %.2fkm, 도로 %.2fkm)%n",
                min.ratio(), min.name, min.straightM / 1000, min.roadM / 1000));
        sb.append(String.format(Locale.US, "최댓값 %.3f %s (직선 %.2fkm, 도로 %.2fkm)%n",
                max.ratio(), max.name, max.straightM / 1000, max.roadM / 1000));
        System.out.println(sb);
    }

    private static double mean(List<Double> values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    static final class Place {
        final String id;
        final String name;
        final double lat;
        final double lng;
        final double straightM;
        double roadM;
        String note = "";

        Place(String id, String name, double lat, double lng) {
            this.id = id;
            this.name = name;
            this.lat = lat;
            this.lng = lng;
            this.straightM = haversineM(ORIGIN_LAT, ORIGIN_LNG, lat, lng);
        }

        double ratio() {
            return roadM / straightM;
        }
    }

    /** 401, 403 같은 권한 오류. 다시 시도하지 않고 측정을 멈춘다. */
    static final class PermissionException extends IOException {
        PermissionException(String message) {
            super(message);
        }
    }

    /** 경로를 못 구한 경우. 한 번 다시 시도하고, 그래도 실패하면 그 주유소는 뺀다. */
    static final class RouteException extends Exception {
        RouteException(String message) {
            super(message);
        }
    }
}
