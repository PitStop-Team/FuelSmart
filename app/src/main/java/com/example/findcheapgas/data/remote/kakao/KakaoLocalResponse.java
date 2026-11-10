package com.example.findcheapgas.data.remote.kakao;

import com.example.findcheapgas.model.Place;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 카카오 로컬 API 응답 해석. 좌표는 x가 경도, y가 위도이고 문자열로 온다.
 * <pre>키워드: {"documents": [{"place_name": ..., "category_name": "교통,수송 > 지하철,철도 > 수도권2호선",
 *   "address_name": ..., "road_address_name": ..., "x": "127.02", "y": "37.49"}], "meta": {...}}
 * 주소:   {"documents": [{"address_name": ..., "x": ..., "y": ...,
 *   "road_address": {"address_name": ..., "building_name": ...} 또는 null}], "meta": {...}}</pre>
 * 좌표 → 행정구역: {"documents": [{"region_type": "B", "region_3depth_name": "역삼동", ...},
 *   {"region_type": "H", "region_3depth_name": "역삼1동", ...}]}
 * 좌표를 읽을 수 없는 항목은 지도에 옮길 수 없으므로 뺀다.
 */
public final class KakaoLocalResponse {

    private static final String CATEGORY_SEPARATOR = ">";
    private static final String ADDRESS_CATEGORY = "주소";

    private KakaoLocalResponse() {
    }

    /** 키워드 검색 응답 → 장소 목록 */
    public static List<Place> parseKeyword(String body) throws KakaoException {
        List<Place> places = new ArrayList<>();
        for (JsonObject doc : documents(body)) {
            Double latitude = coordinate(doc, "y");
            Double longitude = coordinate(doc, "x");
            String name = text(doc, "place_name");
            if (latitude == null || longitude == null || name.isEmpty()) {
                continue;
            }
            String address = firstNonEmpty(text(doc, "road_address_name"), text(doc, "address_name"));
            places.add(new Place(name, address, lastCategory(text(doc, "category_name")), latitude, longitude));
        }
        return places;
    }

    /** 주소 검색 응답 → 장소 목록. 이름은 건물 이름이 있으면 건물 이름, 없으면 주소 */
    public static List<Place> parseAddress(String body) throws KakaoException {
        List<Place> places = new ArrayList<>();
        for (JsonObject doc : documents(body)) {
            Double latitude = coordinate(doc, "y");
            Double longitude = coordinate(doc, "x");
            String addressName = text(doc, "address_name");
            if (latitude == null || longitude == null || addressName.isEmpty()) {
                continue;
            }
            JsonElement road = doc.get("road_address");
            String roadAddress = "";
            String building = "";
            if (road != null && road.isJsonObject()) {
                roadAddress = text(road.getAsJsonObject(), "address_name");
                building = text(road.getAsJsonObject(), "building_name");
            }
            String name = building.isEmpty() ? addressName : building;
            String address = firstNonEmpty(roadAddress, addressName);
            places.add(new Place(name, address, ADDRESS_CATEGORY, latitude, longitude));
        }
        return places;
    }

    /**
     * 좌표 → 행정구역 응답 → 동 이름. 행정동(H)을 먼저 쓰고, 없으면 법정동(B)을 쓴다.
     * 동 이름이 비어 있으면 구·시 이름(2단계), 그것도 없으면 시·도 이름(1단계)을 쓴다. 아무것도 없으면 null
     */
    public static String parseRegionName(String body) throws KakaoException {
        List<JsonObject> docs = documents(body);
        String name = regionNameOf(docs, "H");
        return name != null ? name : regionNameOf(docs, "B");
    }

    private static String regionNameOf(List<JsonObject> docs, String regionType) {
        for (JsonObject doc : docs) {
            if (!regionType.equals(text(doc, "region_type"))) {
                continue;
            }
            for (String key : new String[]{"region_3depth_name", "region_2depth_name", "region_1depth_name"}) {
                String name = text(doc, key);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        return null;
    }

    /** "교통,수송 > 지하철,철도 > 수도권2호선" → "수도권2호선" */
    static String lastCategory(String category) {
        int index = category.lastIndexOf(CATEGORY_SEPARATOR);
        return index < 0 ? category.trim() : category.substring(index + 1).trim();
    }

    private static List<JsonObject> documents(String body) throws KakaoException {
        JsonElement root;
        try {
            root = JsonParser.parseString(body == null ? "" : body);
        } catch (JsonParseException e) {
            throw new KakaoException("JSON이 아닌 응답", e);
        }
        JsonElement docs = root.isJsonObject() ? root.getAsJsonObject().get("documents") : null;
        if (docs == null || !docs.isJsonArray()) {
            throw new KakaoException("documents가 없는 응답", 0);
        }
        List<JsonObject> result = new ArrayList<>();
        JsonArray array = docs.getAsJsonArray();
        for (JsonElement item : array) {
            if (item.isJsonObject()) {
                result.add(item.getAsJsonObject());
            }
        }
        return result;
    }

    private static String text(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return "";
        }
        return value.getAsString().trim();
    }

    /** 문자열이나 숫자 좌표. 읽을 수 없거나 0이면 null */
    private static Double coordinate(JsonObject parent, String key) {
        String value = text(parent, key);
        try {
            double parsed = Double.parseDouble(value);
            return parsed == 0 || Double.isNaN(parsed) ? null : parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonEmpty(String first, String second) {
        return first.isEmpty() ? second : first;
    }
}
