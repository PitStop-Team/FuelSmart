package com.example.findcheapgas.data.remote;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 반경 내 주유소(aroundAll.do) 응답.
 * <pre>{"RESULT": {"OIL": [{"UNI_ID": ..., "POLL_DIV_CD": ..., "OS_NM": ..., "PRICE": ..., ...}]}}</pre>
 * 가짜 데이터(assets/mock)도 같은 구조라서 이 클래스로 함께 읽는다.
 */
public class OpinetAroundResponse {

    private static final Gson GSON = new Gson();

    @SerializedName("RESULT")
    public Result result;

    public static class Result {
        @SerializedName("OIL")
        public List<Oil> oil;
    }

    public static class Oil {
        @SerializedName("UNI_ID")
        public String uniId;
        @SerializedName("POLL_DIV_CD")
        public String brandCode;
        @SerializedName("OS_NM")
        public String name;
        @SerializedName("PRICE")
        public int price;
        @SerializedName("DISTANCE")
        public double distance;
        @SerializedName("GIS_X_COOR")
        public double katecX;
        @SerializedName("GIS_Y_COOR")
        public double katecY;
    }

    /** 응답 본문을 주유소 목록으로 바꾼다. 형식이 다르면 OpinetException을 던진다. */
    public static List<Oil> parse(String body) throws OpinetException {
        OpinetAroundResponse response;
        try {
            response = GSON.fromJson(body, OpinetAroundResponse.class);
        } catch (JsonParseException e) {
            throw new OpinetException("JSON이 아닌 응답: " + preview(body), e);
        }
        if (response == null || response.result == null || response.result.oil == null) {
            throw new OpinetException("RESULT.OIL이 없는 응답: " + preview(body));
        }
        return response.result.oil;
    }

    private static String preview(String body) {
        if (body == null) {
            return "(없음)";
        }
        String trimmed = body.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
    }
}
