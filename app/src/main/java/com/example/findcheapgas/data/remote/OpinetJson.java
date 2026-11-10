package com.example.findcheapgas.data.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * 오피넷 응답을 한 칸씩 꺼내 읽는 도구. 상세정보와 상호 검색 응답 해석이 같이 쓴다.
 * 필드가 없거나 비어 있어도 멈추지 않고 빈 문자열, 0으로 읽는다.
 */
final class OpinetJson {

    private OpinetJson() {
    }

    /** {"RESULT": {"OIL": [...]}}의 OIL 배열. 형식이 다르면 OpinetException */
    static JsonArray oilArray(String body) throws OpinetException {
        JsonElement root;
        try {
            root = JsonParser.parseString(body == null ? "" : body);
        } catch (JsonParseException e) {
            throw new OpinetException("JSON이 아닌 응답: " + preview(body), e);
        }
        JsonObject result = object(root, "RESULT");
        JsonArray oils = result != null ? array(result, "OIL") : null;
        if (oils == null) {
            throw new OpinetException("RESULT.OIL이 없는 응답: " + preview(body));
        }
        return oils;
    }

    static JsonObject object(JsonElement parent, String key) {
        if (parent == null || !parent.isJsonObject()) {
            return null;
        }
        JsonElement value = parent.getAsJsonObject().get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    /** 문자열이나 숫자를 문자열로. 없거나 null, 객체면 빈 문자열 */
    static String text(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return "";
        }
        return value.getAsString().trim();
    }

    /** 숫자나 숫자 문자열("1,659" 포함). 읽을 수 없으면 0 */
    static double number(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return 0;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return primitive.getAsDouble();
        }
        try {
            return Double.parseDouble(primitive.getAsString().replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String firstNonEmpty(String first, String second) {
        return first.isEmpty() ? second : first;
    }

    static String preview(String body) {
        if (body == null) {
            return "(없음)";
        }
        String trimmed = body.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
    }
}
