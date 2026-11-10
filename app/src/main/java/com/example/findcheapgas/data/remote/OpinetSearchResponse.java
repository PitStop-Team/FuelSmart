package com.example.findcheapgas.data.remote;

import static com.example.findcheapgas.data.remote.OpinetJson.firstNonEmpty;
import static com.example.findcheapgas.data.remote.OpinetJson.number;
import static com.example.findcheapgas.data.remote.OpinetJson.oilArray;
import static com.example.findcheapgas.data.remote.OpinetJson.text;

import com.example.findcheapgas.model.StationSearchResult;
import com.example.findcheapgas.util.CoordinateConverter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 상호로 주유소 검색(searchByName.do) 응답 해석.
 * 실제 응답을 아직 확인하지 않아서 아래처럼 가정하고, 필드가 없거나 비어 있어도 읽는다.
 * <pre>{"RESULT": {"OIL": [{"UNI_ID": ..., "POLL_DIV_CD": ..., "OS_NM": ..., "NEW_ADR": ..., "VAN_ADR": ...,
 *   "GIS_X_COOR": ..., "GIS_Y_COOR": ...}]}}</pre>
 * - 브랜드는 POLL_DIV_CD, 없으면 POLL_DIV_CO (상세정보 문서와 같은 이름)
 * - 주소는 NEW_ADR(도로명), 없으면 VAN_ADR(지번)
 * - 가격은 오지 않을 수 있어서 읽지 않는다.
 * UNI_ID나 상호가 없거나, KATEC 좌표가 없어서 지도에 옮길 수 없는 항목은 뺀다.
 * 가짜 상세 데이터(assets/mock/detail)도 같은 필드를 가지고 있어서 이 클래스로 함께 읽는다.
 */
public final class OpinetSearchResponse {

    private OpinetSearchResponse() {
    }

    /** 응답 본문을 검색 결과로 바꾼다. 결과가 없으면 빈 목록, 형식이 다르면 OpinetException */
    public static List<StationSearchResult> parse(String body) throws OpinetException {
        List<StationSearchResult> results = new ArrayList<>();
        for (JsonElement item : oilArray(body)) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject oil = item.getAsJsonObject();
            String id = text(oil, "UNI_ID");
            String name = text(oil, "OS_NM");
            double katecX = number(oil, "GIS_X_COOR");
            double katecY = number(oil, "GIS_Y_COOR");
            if (id.isEmpty() || name.isEmpty() || katecX <= 0 || katecY <= 0) {
                continue;
            }
            CoordinateConverter.Wgs84 wgs84 = CoordinateConverter.toWgs84(katecX, katecY);
            results.add(new StationSearchResult(
                    id,
                    firstNonEmpty(text(oil, "POLL_DIV_CD"), text(oil, "POLL_DIV_CO")),
                    name,
                    firstNonEmpty(text(oil, "NEW_ADR"), text(oil, "VAN_ADR")),
                    wgs84.latitude,
                    wgs84.longitude));
        }
        return results;
    }
}
