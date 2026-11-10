package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** 길찾기 외부 앱 주소: 좌표 순서, 주유소 이름 인코딩, 설치 여부에 따른 순서 */
public class RouteLinksTest {

    private static final double LAT = 37.4979;
    private static final double LNG = 127.0276;

    @Test
    public void kakaoMap_epIsLatitudeThenLongitude() {
        assertEquals("kakaomap://route?ep=37.4979,127.0276&by=CAR",
                RouteLinks.routeUri(RouteLinks.App.KAKAO_MAP, "강남주유소", LAT, LNG));
    }

    @Test
    public void tmap_goalxIsLongitude_goalyIsLatitude() {
        String uri = RouteLinks.routeUri(RouteLinks.App.TMAP, "SK", LAT, LNG);
        assertEquals("tmap://route?goalname=SK&goalx=127.0276&goaly=37.4979", uri);
    }

    @Test
    public void tmap_encodesKoreanSpaceAndSpecialCharacters() {
        String uri = RouteLinks.routeUri(RouteLinks.App.TMAP, "GS칼텍스 강남&역삼 #1", LAT, LNG);
        // "칼텍스" = %EC%B9%BC %ED%85%8D %EC%8A%A4, "강남" = %EA%B0%95 %EB%82%A8, "역삼" = %EC%97%AD %EC%82%BC
        assertEquals("tmap://route?goalname=GS%EC%B9%BC%ED%85%8D%EC%8A%A4%20%EA%B0%95%EB%82%A8%26"
                + "%EC%97%AD%EC%82%BC%20%231&goalx=127.0276&goaly=37.4979", uri);
        // 이름 안의 &, 공백, #이 그대로 남아 쿼리를 깨뜨리지 않는다.
        assertFalse(uri.contains(" "));
        assertFalse(uri.contains("#"));
        assertFalse(uri.contains("+"));
        assertEquals(2, uri.split("&").length - 1);
    }

    @Test
    public void encode_plusSignAndNull() {
        assertEquals("A%2BB%20C", RouteLinks.encode("A+B C"));
        assertEquals("", RouteLinks.encode(null));
    }

    @Test
    public void coordinate_roundsToSixDigitsWithoutExponent() {
        assertEquals("37.497942", RouteLinks.coordinate(37.49794213));
        assertEquals("127", RouteLinks.coordinate(127.0));
        assertEquals("0.0001", RouteLinks.coordinate(0.0001));
    }

    @Test
    public void storeUris() {
        assertEquals("market://details?id=net.daum.android.map", RouteLinks.marketUri(RouteLinks.App.KAKAO_MAP));
        assertEquals("https://play.google.com/store/apps/details?id=com.skt.tmap.ku",
                RouteLinks.webStoreUri(RouteLinks.App.TMAP));
    }

    @Test
    public void launchOrder_installed_routeThenMarketThenWeb() {
        List<String> order = RouteLinks.launchOrder(RouteLinks.App.KAKAO_MAP, true, "강남주유소", LAT, LNG);
        assertEquals(Arrays.asList(
                "kakaomap://route?ep=37.4979,127.0276&by=CAR",
                "market://details?id=net.daum.android.map",
                "https://play.google.com/store/apps/details?id=net.daum.android.map"), order);
    }

    @Test
    public void launchOrder_notInstalled_marketThenWeb() {
        List<String> order = RouteLinks.launchOrder(RouteLinks.App.TMAP, false, "강남주유소", LAT, LNG);
        assertEquals(Arrays.asList(
                "market://details?id=com.skt.tmap.ku",
                "https://play.google.com/store/apps/details?id=com.skt.tmap.ku"), order);
        for (String uri : order) {
            assertFalse(uri.startsWith("tmap://"));
        }
    }

    @Test
    public void packages_matchManifestQueries() {
        assertEquals("net.daum.android.map", RouteLinks.App.KAKAO_MAP.getPackageName());
        assertEquals("com.skt.tmap.ku", RouteLinks.App.TMAP.getPackageName());
        assertTrue(RouteLinks.routeUri(RouteLinks.App.TMAP, "", LAT, LNG).startsWith("tmap://route?goalname=&"));
    }
}
