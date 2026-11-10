package com.example.findcheapgas.util;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 길찾기 외부 앱(카카오맵, 티맵)을 여는 주소를 만든다. Android에 의존하지 않아서 단위 테스트로 확인한다.
 * 이 앱은 목적지를 넘겨 외부 앱을 여는 것까지만 하고, 경로 표시와 길안내는 각 앱이 한다. 출발지는 넘기지 않는다(각 앱이 현재 위치에서 출발).
 * 좌표는 오피넷 KATEC이 아니라 변환한 WGS84 위도·경도다.
 * - 카카오맵: kakaomap://route?ep={위도},{경도}&by=CAR
 * - 티맵: tmap://route?goalname={주유소명}&goalx={경도}&goaly={위도} (주유소명은 URL 인코딩)
 * - 설치 페이지: market://details?id={패키지}, 열 수 없으면 https://play.google.com/store/apps/details?id={패키지}
 */
public final class RouteLinks {

    /** 길찾기에 쓸 수 있는 외부 앱. AndroidManifest.xml의 queries에 같은 패키지를 선언해 둔다. */
    public enum App {
        KAKAO_MAP("net.daum.android.map"),
        TMAP("com.skt.tmap.ku");

        private final String packageName;

        App(String packageName) {
            this.packageName = packageName;
        }

        public String getPackageName() {
            return packageName;
        }
    }

    // 소수점 6자리면 약 10cm 단위라 충분하다.
    private static final int COORDINATE_SCALE = 6;

    private RouteLinks() {
    }

    /** 앱에서 목적지까지 길찾기를 여는 주소 */
    public static String routeUri(App app, String name, double latitude, double longitude) {
        if (app == App.KAKAO_MAP) {
            return "kakaomap://route?ep=" + coordinate(latitude) + "," + coordinate(longitude) + "&by=CAR";
        }
        return "tmap://route?goalname=" + encode(name)
                + "&goalx=" + coordinate(longitude)
                + "&goaly=" + coordinate(latitude);
    }

    /** 플레이스토어 앱의 설치 페이지 */
    public static String marketUri(App app) {
        return "market://details?id=" + app.getPackageName();
    }

    /** 플레이스토어 앱이 없을 때 브라우저로 여는 설치 페이지 */
    public static String webStoreUri(App app) {
        return "https://play.google.com/store/apps/details?id=" + app.getPackageName();
    }

    /**
     * 차례로 열어 볼 주소. 앞의 주소를 열 수 없으면(ActivityNotFoundException) 다음 주소를 연다.
     * - 설치됨: 길찾기 → 플레이스토어 → 웹 (설치 확인 뒤 지워졌거나 스킴을 처리하지 못하는 경우까지)
     * - 설치 안 됨: 플레이스토어 → 웹
     */
    public static List<String> launchOrder(App app, boolean installed, String name, double latitude,
                                           double longitude) {
        List<String> uris = new ArrayList<>(3);
        if (installed) {
            uris.add(routeUri(app, name, latitude, longitude));
        }
        uris.add(marketUri(app));
        uris.add(webStoreUri(app));
        return Collections.unmodifiableList(uris);
    }

    /**
     * URL 인코딩 (UTF-8). 공백은 "+"가 아니라 "%20"으로 바꿔서 쿼리를 "+" 그대로 읽는 앱에서도 공백으로 보이게 한다.
     * 이름이 없으면 빈 문자열
     */
    static String encode(String text) {
        if (text == null) {
            return "";
        }
        try {
            return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8을 쓸 수 없습니다.", e); // 모든 Java 환경에서 UTF-8은 있다.
        }
    }

    /** 37.497900 → "37.4979". 지수 표기(1.0E-4)가 나오지 않게 한다. */
    static String coordinate(double value) {
        return BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
