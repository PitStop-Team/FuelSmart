package com.example.findcheapgas.data.remote;

/**
 * 오피넷 상세정보에 그 주유소가 없을 때 (RESULT.OIL이 빈 배열). 폐업했거나 ID가 바뀐 경우다.
 * 즐겨찾기 탭은 이 경우 "찾을 수 없음"으로 알리고 삭제할 수 있게 한다.
 */
public class StationNotFoundException extends OpinetException {

    public StationNotFoundException(String message) {
        super(message);
    }

    public StationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
