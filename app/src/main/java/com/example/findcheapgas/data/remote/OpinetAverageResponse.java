package com.example.findcheapgas.data.remote;

import static com.example.findcheapgas.data.remote.OpinetJson.number;
import static com.example.findcheapgas.data.remote.OpinetJson.oilArray;
import static com.example.findcheapgas.data.remote.OpinetJson.text;

import com.example.findcheapgas.model.AveragePrice;
import com.example.findcheapgas.model.DailyAveragePrice;
import com.example.findcheapgas.model.FuelType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 오피넷 평균가 응답 해석. 세 API 모두 {"RESULT": {"OIL": [...]}} 구조이고 유종마다 한 줄씩 온다.
 * <ul>
 *   <li>전국 평균(avgAllPrice.do): TRADE_DT, PRODCD, PRODNM, PRICE, DIFF</li>
 *   <li>시도별 평균(avgSidoPrice.do, 시도·유종 생략 시 전체): SIDOCD, SIDONM, PRODCD, PRICE, DIFF</li>
 *   <li>최근 7일 전국 일일 평균(avgRecentPrice.do, 날짜·유종 생략 시 7일 전체): DATE, PRODCD, PRICE</li>
 * </ul>
 * 상세정보 해석과 같이 한 칸씩 꺼내 읽고, 필드가 없거나 비어 있는 줄, 가격이 0 이하인 줄,
 * 앱이 다루지 않는 유종(실내등유 C004 등)은 그 줄만 뺀다. 같은 키가 여러 번 오면 처음 것을 쓴다.
 * 가짜 데이터(assets/mock/avg_*.json)도 같은 구조라서 이 클래스로 함께 읽는다.
 */
public final class OpinetAverageResponse {

    private OpinetAverageResponse() {
    }

    /** 전국 평균. OIL이 빈 배열이면 빈 목록. 형식이 다르면 OpinetException */
    public static List<AveragePrice> parseNational(String body) throws OpinetException {
        List<AveragePrice> result = new ArrayList<>();
        Set<FuelType> seen = new HashSet<>();
        for (JsonObject oil : objects(oilArray(body))) {
            FuelType type = FuelType.fromCode(text(oil, "PRODCD"), null);
            double price = number(oil, "PRICE");
            if (type == null || price <= 0 || !seen.add(type)) {
                continue;
            }
            result.add(AveragePrice.national(type, price));
        }
        return result;
    }

    /** 시도별 평균. 시도 이름이 없는 줄은 뺀다. */
    public static List<AveragePrice> parseSido(String body) throws OpinetException {
        List<AveragePrice> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonObject oil : objects(oilArray(body))) {
            FuelType type = FuelType.fromCode(text(oil, "PRODCD"), null);
            String name = text(oil, "SIDONM");
            double price = number(oil, "PRICE");
            if (type == null || name.isEmpty() || price <= 0 || !seen.add(name + "|" + type.getCode())) {
                continue;
            }
            result.add(new AveragePrice(type, price, text(oil, "SIDOCD"), name));
        }
        return result;
    }

    /** 최근 7일 전국 일일 평균. 날짜가 yyyyMMdd가 아닌 줄은 빼고, 날짜 오름차순으로 돌려준다. */
    public static List<DailyAveragePrice> parseRecent(String body) throws OpinetException {
        List<DailyAveragePrice> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonObject oil : objects(oilArray(body))) {
            FuelType type = FuelType.fromCode(text(oil, "PRODCD"), null);
            String date = text(oil, "DATE");
            double price = number(oil, "PRICE");
            if (type == null || !date.matches("\\d{8}") || price <= 0 || !seen.add(date + "|" + type.getCode())) {
                continue;
            }
            result.add(new DailyAveragePrice(date, type, price));
        }
        Collections.sort(result, Comparator.comparing(DailyAveragePrice::getDate));
        return result;
    }

    private static List<JsonObject> objects(JsonArray oils) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement item : oils) {
            if (item.isJsonObject()) {
                result.add(item.getAsJsonObject());
            }
        }
        return result;
    }
}
