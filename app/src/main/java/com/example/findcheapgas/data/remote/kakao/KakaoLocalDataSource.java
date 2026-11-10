package com.example.findcheapgas.data.remote.kakao;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.model.Place;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * 카카오 로컬 API로 장소를 찾고, 좌표의 동 이름을 얻는다. 백그라운드 스레드에서만 부른다.
 * 오피넷과 Retrofit, OkHttp 설정을 따로 둔다. 헤더에 REST API 키가 들어가므로 HTTP 로깅 인터셉터를 붙이지 않고,
 * 요청 객체나 헤더를 로그나 예외 메시지에 넣지 않는다. 실패 메시지에는 HTTP 코드만 넣는다.
 * 가짜 데이터 모드에서도 실제 카카오 API를 쓴다 (오피넷 하루 300회 제한과 관계없음).
 */
public class KakaoLocalDataSource {

    private static final String BASE_URL = "https://dapi.kakao.com/";
    private static final long TIMEOUT_SECONDS = 10;
    private static final int KEYWORD_SIZE = 15;
    private static final int ADDRESS_SIZE = 10;

    private final KakaoLocalApi api;

    public KakaoLocalDataSource() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("Authorization", "KakaoAK " + BuildConfig.KAKAO_REST_API_KEY)
                        .build()))
                .build();
        api = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .build()
                .create(KakaoLocalApi.class);
    }

    public static boolean hasApiKey() {
        return !BuildConfig.KAKAO_REST_API_KEY.isEmpty();
    }

    /**
     * 키워드로 찾고, 결과가 없으면 주소로 한 번 더 찾는다.
     *
     * @throws IOException    네트워크 연결 실패, 시간 초과
     * @throws KakaoException 키 오류(401, 403), 그 밖의 오류 응답
     */
    public List<Place> search(String query) throws IOException, KakaoException {
        if (!hasApiKey()) {
            throw new KakaoException("KAKAO_REST_API_KEY가 없습니다.", 401);
        }
        List<Place> places = KakaoLocalResponse.parseKeyword(execute(api.searchKeyword(query, KEYWORD_SIZE)));
        if (!places.isEmpty()) {
            return places;
        }
        return KakaoLocalResponse.parseAddress(execute(api.searchAddress(query, ADDRESS_SIZE)));
    }

    /**
     * 좌표의 동 이름 (행정동 우선, 예: 역삼1동). 이름을 알 수 없는 좌표(바다 등)면 null
     *
     * @throws IOException    네트워크 연결 실패, 시간 초과
     * @throws KakaoException 키 오류(401, 403), 그 밖의 오류 응답
     */
    public String regionName(double latitude, double longitude) throws IOException, KakaoException {
        if (!hasApiKey()) {
            throw new KakaoException("KAKAO_REST_API_KEY가 없습니다.", 401);
        }
        return KakaoLocalResponse.parseRegionName(execute(api.coordToRegion(longitude, latitude)));
    }

    private static String execute(Call<ResponseBody> call) throws IOException, KakaoException {
        Response<ResponseBody> response = call.execute();
        try (ResponseBody body = response.isSuccessful() ? response.body() : response.errorBody()) {
            if (!response.isSuccessful()) {
                throw new KakaoException("HTTP " + response.code(), response.code());
            }
            if (body == null) {
                throw new KakaoException("빈 응답", response.code());
            }
            return body.string();
        }
    }
}
