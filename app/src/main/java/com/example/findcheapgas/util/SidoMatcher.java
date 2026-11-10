package com.example.findcheapgas.util;

import androidx.annotation.Nullable;

import com.example.findcheapgas.model.AveragePrice;
import com.example.findcheapgas.model.FuelType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주유소 주소의 첫 단어로 시도를 정하고, 오피넷 시도별 평균가(avgSidoPrice.do)의 시도 이름(SIDONM)과 맞춘다.
 * 카카오 API를 부르지 않고 주소 글자만 본다.
 * - 주소는 "서울특별시", "충청북도", "강원특별자치도"처럼 긴 이름이나 "서울", "충북"처럼 짧은 이름으로 온다.
 * - 오피넷 시도 이름은 "서울", "충북"처럼 짧은 이름이다. 긴 이름으로 와도 같은 표로 짧은 이름으로 바꿔서 맞춘다.
 * - 오피넷 지역코드 조회 문서 예시에는 광주와 전남이 "전남광주"(코드 20) 하나로 나온다. 그래서 광주·전남 주소는
 *   응답에 "광주"/"전남"이 있으면 그것을, 없으면 "전남광주"를 쓴다.
 * 시도를 찾지 못하면 null이고, 화면은 지역 평균 줄만 빼고 전국 평균만 보여준다.
 */
public final class SidoMatcher {

    /** 광주와 전남을 합쳐서 부르는 오피넷 시도 이름 */
    public static final String JEONNAM_GWANGJU = "전남광주";

    // 주소나 응답에 나올 수 있는 이름 → 오피넷 짧은 이름
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        add("서울", "서울특별시", "서울시");
        add("부산", "부산광역시", "부산시");
        add("대구", "대구광역시", "대구시");
        add("인천", "인천광역시", "인천시");
        add("광주", "광주광역시");
        add("대전", "대전광역시", "대전시");
        add("울산", "울산광역시", "울산시");
        add("세종", "세종특별자치시", "세종시");
        add("경기", "경기도");
        add("강원", "강원특별자치도", "강원도");
        add("충북", "충청북도");
        add("충남", "충청남도");
        add("전북", "전북특별자치도", "전라북도");
        add("전남", "전라남도");
        add("경북", "경상북도");
        add("경남", "경상남도");
        add("제주", "제주특별자치도", "제주도");
    }

    private SidoMatcher() {
    }

    private static void add(String shortName, String... longNames) {
        ALIASES.put(shortName, shortName);
        for (String name : longNames) {
            ALIASES.put(name, shortName);
        }
    }

    /** 주소 첫 단어의 오피넷 짧은 시도 이름 ("서울특별시 강남구 ..." → "서울"). 모르면 null */
    @Nullable
    public static String sidoOf(@Nullable String address) {
        String first = firstWord(address);
        if (first == null) {
            return null;
        }
        if (first.startsWith(JEONNAM_GWANGJU)) {
            return JEONNAM_GWANGJU; // 주소의 시도 이름이 "전남광주..."로 바뀌어도 맞춘다.
        }
        return ALIASES.get(first);
    }

    /** 오피넷 시도 이름을 짧은 이름으로 ("서울특별시" → "서울", 표에 없는 이름은 공백만 지우고 그대로) */
    public static String normalizeSidoName(String sidoName) {
        String compact = sidoName.replaceAll("\\s+", "");
        String alias = ALIASES.get(compact);
        return alias != null ? alias : compact;
    }

    /** 주소의 시도에 맞는 오피넷 시도 이름 후보 (앞이 우선). 시도를 모르면 빈 목록 */
    public static List<String> candidates(@Nullable String address) {
        String sido = sidoOf(address);
        if (sido == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        result.add(sido);
        if (sido.equals("광주") || sido.equals("전남")) {
            result.add(JEONNAM_GWANGJU);
        }
        return result;
    }

    /** 시도별 평균가 중 주소의 시도, 그 유종에 맞는 것. 없으면 null */
    @Nullable
    public static AveragePrice find(@Nullable String address, List<AveragePrice> sidoPrices, FuelType fuelType) {
        for (String candidate : candidates(address)) {
            for (AveragePrice price : sidoPrices) {
                if (price.getFuelType() == fuelType && price.getSidoName() != null
                        && normalizeSidoName(price.getSidoName()).equals(candidate)) {
                    return price;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String firstWord(@Nullable String address) {
        if (address == null) {
            return null;
        }
        String trimmed = address.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.split("\\s+")[0];
    }
}
