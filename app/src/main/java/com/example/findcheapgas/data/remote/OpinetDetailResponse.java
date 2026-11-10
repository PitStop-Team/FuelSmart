package com.example.findcheapgas.data.remote;

import static com.example.findcheapgas.data.remote.OpinetJson.array;
import static com.example.findcheapgas.data.remote.OpinetJson.firstNonEmpty;
import static com.example.findcheapgas.data.remote.OpinetJson.number;
import static com.example.findcheapgas.data.remote.OpinetJson.oilArray;
import static com.example.findcheapgas.data.remote.OpinetJson.preview;
import static com.example.findcheapgas.data.remote.OpinetJson.text;

import com.example.findcheapgas.model.FuelPrice;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.StationDetail;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 주유소 상세정보(detailById.do) 응답 해석.
 * <pre>{"RESULT": {"OIL": [{"UNI_ID": ..., "POLL_DIV_CO": ..., "OS_NM": ..., "NEW_ADR": ..., "TEL": ...,
 *   "CAR_WASH_YN": "Y", ..., "OIL_PRICE": [{"PRODCD": "B027", "PRICE": 1659, "TRADE_DT": ..., "TRADE_TM": ...}]}]}}</pre>
 * 필드 이름이나 값 형식이 문서와 조금 달라도 앱이 멈추지 않도록 Gson 클래스 매핑 대신 한 칸씩 꺼내 읽는다.
 * 없거나 비어 있는 필드는 빈 문자열, false, 0으로 두고, 숫자가 문자열로 와도 읽는다.
 * 가짜 데이터(assets/mock/detail)도 같은 구조라서 이 클래스로 함께 읽는다.
 */
public final class OpinetDetailResponse {

    private OpinetDetailResponse() {
    }

    /**
     * 응답 본문을 상세정보로 바꾼다. 형식이 다르면 OpinetException을, 주유소가 없으면(OIL이 빈 배열)
     * StationNotFoundException을 던진다.
     */
    public static StationDetail parse(String body) throws OpinetException {
        JsonArray oils = oilArray(body);
        if (oils.size() == 0) {
            throw new StationNotFoundException("주유소가 없는 응답: " + preview(body));
        }
        if (!oils.get(0).isJsonObject()) {
            throw new OpinetException("주유소 정보가 없는 응답: " + preview(body));
        }
        JsonObject oil = oils.get(0).getAsJsonObject();

        return new StationDetail(
                text(oil, "UNI_ID"),
                text(oil, "OS_NM"),
                // 문서에는 POLL_DIV_CO, 주변 조회 응답에는 POLL_DIV_CD라서 둘 다 본다.
                firstNonEmpty(text(oil, "POLL_DIV_CO"), text(oil, "POLL_DIV_CD")),
                text(oil, "NEW_ADR"),
                text(oil, "VAN_ADR"),
                text(oil, "TEL"),
                isYes(oil, "CAR_WASH_YN"),
                isYes(oil, "CVS_YN"),
                isYes(oil, "MAINT_YN"),
                isYes(oil, "KPETRO_YN"),
                // 문서에 셀프 여부 필드가 없다. 응답에 SELF_YN이 오면 쓰고, 없으면 상호로 추정한다.
                optionalYes(oil, "SELF_YN"),
                number(oil, "GIS_X_COOR"),
                number(oil, "GIS_Y_COOR"),
                prices(array(oil, "OIL_PRICE")));
    }

    /** 앱이 다루는 4개 유종 중 가격이 있는 것만, FuelType 순서대로. 같은 유종이 여러 번 오면 처음 것을 쓴다. */
    private static List<FuelPrice> prices(JsonArray items) {
        List<FuelPrice> prices = new ArrayList<>();
        if (items == null) {
            return prices;
        }
        for (JsonElement item : items) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject obj = item.getAsJsonObject();
            FuelType type = FuelType.fromCode(text(obj, "PRODCD"), null);
            int price = (int) Math.round(number(obj, "PRICE"));
            if (type == null || price <= 0 || contains(prices, type)) {
                continue; // 등유 같은 다른 유종, 가격이 없는 항목은 뺀다.
            }
            prices.add(new FuelPrice(type, price, text(obj, "TRADE_DT"), text(obj, "TRADE_TM")));
        }
        Collections.sort(prices, Comparator.comparingInt(p -> p.getFuelType().ordinal()));
        return prices;
    }

    private static boolean contains(List<FuelPrice> prices, FuelType type) {
        for (FuelPrice price : prices) {
            if (price.getFuelType() == type) {
                return true;
            }
        }
        return false;
    }

    private static boolean isYes(JsonObject parent, String key) {
        return "Y".equalsIgnoreCase(text(parent, key));
    }

    /** "Y"면 true, "N"이면 false, 없거나 다른 값이면 null */
    private static Boolean optionalYes(JsonObject parent, String key) {
        String value = text(parent, key);
        if ("Y".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("N".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
