package com.example.findcheapgas.data.remote;

import android.util.Log;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.model.AveragePrice;
import com.example.findcheapgas.model.DailyAveragePrice;
import com.example.findcheapgas.model.StationDetail;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.model.StationSearchResult;
import com.example.findcheapgas.util.CoordinateConverter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * 실제 오피넷 API를 호출한다. 백그라운드 스레드에서만 부른다.
 * 요청 URL에 API 키(code 파라미터)가 들어가므로 HTTP 로깅 인터셉터를 붙이지 않고, 요청 URL이나
 * Request, Response 객체를 로그나 예외 메시지에 넣지 않는다. 디버그 빌드에서 상세, 상호 검색, 평균가 응답 본문만 로그로 남긴다.
 */
public class OpinetRemoteDataSource {

    private static final String TAG = "OpinetRemote";
    // 디버그 빌드에서 상세정보, 상호 검색, 평균가 응답 본문을 남기는 태그. 실제 필드 이름을 확인할 때 Logcat에서 이 태그로 거른다.
    private static final String BODY_LOG_TAG = "OpinetBody";
    // Logcat 한 줄 길이 제한(약 4,000자)보다 짧게 나눠서 찍는다.
    private static final int LOG_CHUNK = 3000;

    private static final String BASE_URL = "https://www.opinet.co.kr/api/";
    private static final long TIMEOUT_SECONDS = 10;
    // 정렬은 앱에서 다시 하므로 오피넷 정렬은 아무 값이나 상관없다. 1 가격순, 2 거리순
    private static final int OPINET_SORT_BY_PRICE = 1;

    private final OpinetApi api;

    public OpinetRemoteDataSource() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        api = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .build()
                .create(OpinetApi.class);
    }

    /**
     * @throws IOException     네트워크 연결 실패, 시간 초과
     * @throws OpinetException 오피넷 오류 응답
     */
    public List<OpinetAroundResponse.Oil> fetchAround(StationQuery query, CoordinateConverter.Katec katec)
            throws IOException, OpinetException {
        String body = execute(api.getAroundStations(
                BuildConfig.OPINET_API_KEY,
                String.format(Locale.US, "%.2f", katec.x),
                String.format(Locale.US, "%.2f", katec.y),
                StationQuery.FETCH_RADIUS_METERS,
                query.getFuelType().getCode(),
                OPINET_SORT_BY_PRICE,
                "json"));
        return OpinetAroundResponse.parse(body);
    }

    /**
     * @throws IOException     네트워크 연결 실패, 시간 초과
     * @throws OpinetException 오피넷 오류 응답, 해당 주유소 없음
     */
    public StationDetail fetchDetail(String stationId) throws IOException, OpinetException {
        String body = execute(api.getStationDetail(BuildConfig.OPINET_API_KEY, stationId, "json"));
        if (BuildConfig.DEBUG) {
            logBody("detailById " + stationId, body);
        }
        return OpinetDetailResponse.parse(body);
    }

    /**
     * 상호로 주유소 검색. 응답 필드 이름을 확인할 수 있게 디버그 빌드에서 본문을 로그로 남긴다.
     *
     * @throws IOException     네트워크 연결 실패, 시간 초과
     * @throws OpinetException 오피넷 오류 응답
     */
    public List<StationSearchResult> searchByName(String keyword) throws IOException, OpinetException {
        String body = execute(api.searchByName(BuildConfig.OPINET_API_KEY, keyword, "json"));
        if (BuildConfig.DEBUG) {
            logBody("searchByName", body);
        }
        return OpinetSearchResponse.parse(body);
    }

    /**
     * 전국 평균가 (4개 유종을 한 번에).
     *
     * @throws IOException     네트워크 연결 실패, 시간 초과
     * @throws OpinetException 오피넷 오류 응답
     */
    public List<AveragePrice> fetchNationalAverages() throws IOException, OpinetException {
        String body = execute(api.getNationalAverage(BuildConfig.OPINET_API_KEY, "json"));
        if (BuildConfig.DEBUG) {
            logBody("avgAllPrice", body);
        }
        return OpinetAverageResponse.parseNational(body);
    }

    /**
     * 시도별 평균가 (모든 시도, 4개 유종을 한 번에).
     *
     * @throws IOException     네트워크 연결 실패, 시간 초과
     * @throws OpinetException 오피넷 오류 응답
     */
    public List<AveragePrice> fetchSidoAverages() throws IOException, OpinetException {
        String body = execute(api.getSidoAverage(BuildConfig.OPINET_API_KEY, "json"));
        if (BuildConfig.DEBUG) {
            logBody("avgSidoPrice", body);
        }
        return OpinetAverageResponse.parseSido(body);
    }

    /**
     * 최근 7일 전국 일일 평균가 (4개 유종을 한 번에).
     *
     * @throws IOException     네트워크 연결 실패, 시간 초과
     * @throws OpinetException 오피넷 오류 응답
     */
    public List<DailyAveragePrice> fetchRecentAverages() throws IOException, OpinetException {
        String body = execute(api.getRecentAverage(BuildConfig.OPINET_API_KEY, "json"));
        if (BuildConfig.DEBUG) {
            logBody("avgRecentPrice", body);
        }
        return OpinetAverageResponse.parseRecent(body);
    }

    /** 요청을 보내고 본문을 문자열로 돌려준다. 실패 메시지에는 HTTP 코드만 넣는다. */
    private static String execute(Call<ResponseBody> call) throws IOException, OpinetException {
        Response<ResponseBody> response = call.execute();
        try (ResponseBody body = response.isSuccessful() ? response.body() : response.errorBody()) {
            if (!response.isSuccessful()) {
                throw new OpinetException("HTTP " + response.code());
            }
            if (body == null) {
                throw new OpinetException("빈 응답");
            }
            return body.string();
        }
    }

    /** 응답 본문만 찍는다. 본문에는 API 키가 들어 있지 않다. */
    private static void logBody(String label, String body) {
        Log.d(BODY_LOG_TAG, label + " 응답 " + body.length() + "자");
        for (int start = 0; start < body.length(); start += LOG_CHUNK) {
            Log.d(BODY_LOG_TAG, body.substring(start, Math.min(body.length(), start + LOG_CHUNK)));
        }
    }
}
