package com.example.findcheapgas.data.remote;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * 오피넷 유가정보 API.
 * 한도 초과 등으로 JSON이 아닌 응답이 올 수 있어서 본문을 그대로 받고 OpinetAroundResponse.parse로 직접 해석한다.
 */
public interface OpinetApi {

    /**
     * 반경 내 주유소.
     *
     * @param x      KATEC X 좌표
     * @param y      KATEC Y 좌표
     * @param radius 반경(m, 최대 5000)
     * @param prodcd 유종 코드
     * @param sort   1 가격순, 2 거리순
     */
    @GET("aroundAll.do")
    Call<ResponseBody> getAroundStations(
            @Query("code") String apiKey,
            @Query("x") String x,
            @Query("y") String y,
            @Query("radius") int radius,
            @Query("prodcd") String prodcd,
            @Query("sort") int sort,
            @Query("out") String out);

    /**
     * 주유소 상세정보. 주유소를 눌러 상세 화면을 열 때만 부른다.
     *
     * @param id 주유소 ID (UNI_ID)
     */
    @GET("detailById.do")
    Call<ResponseBody> getStationDetail(
            @Query("code") String apiKey,
            @Query("id") String id,
            @Query("out") String out);

    /**
     * 상호로 주유소 검색. 사용자가 주유소 이름 검색을 했을 때만 부른다 (두 글자 이상, 같은 검색어는 30분 캐시).
     *
     * @param osnm 상호 (일부만 넣어도 된다)
     */
    @GET("searchByName.do")
    Call<ResponseBody> searchByName(
            @Query("code") String apiKey,
            @Query("osnm") String osnm,
            @Query("out") String out);

    /**
     * 전국 주유소 평균가격(현재). 파라미터 없이 부르면 모든 유종이 한 번에 온다.
     * 응답: TRADE_DT, PRODCD, PRODNM, PRICE, DIFF
     */
    @GET("avgAllPrice.do")
    Call<ResponseBody> getNationalAverage(
            @Query("code") String apiKey,
            @Query("out") String out);

    /**
     * 시도별 주유소 평균가격(현재). sido와 prodcd를 생략하면 모든 시도, 모든 유종이 한 번에 온다.
     * 응답: SIDOCD, SIDONM, PRODCD, PRICE, DIFF
     */
    @GET("avgSidoPrice.do")
    Call<ResponseBody> getSidoAverage(
            @Query("code") String apiKey,
            @Query("out") String out);

    /**
     * 최근 7일간 전국 일일 평균가격. date와 prodcd를 생략하면 최근 7일, 모든 유종이 한 번에 온다.
     * 응답: DATE, PRODCD, PRICE
     */
    @GET("avgRecentPrice.do")
    Call<ResponseBody> getRecentAverage(
            @Query("code") String apiKey,
            @Query("out") String out);
}
