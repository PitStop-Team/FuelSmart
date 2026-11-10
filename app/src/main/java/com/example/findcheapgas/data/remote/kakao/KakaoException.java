package com.example.findcheapgas.data.remote.kakao;

/** 카카오 로컬 API가 정상 결과가 아닌 응답을 보냈을 때 (키 오류, HTTP 오류, 형식 오류) */
public class KakaoException extends Exception {

    // HTTP 상태 코드. 형식 오류처럼 HTTP 코드가 없으면 0
    private final int httpCode;

    public KakaoException(String message, int httpCode) {
        super(message);
        this.httpCode = httpCode;
    }

    public KakaoException(String message, Throwable cause) {
        super(message, cause);
        this.httpCode = 0;
    }

    public int getHttpCode() {
        return httpCode;
    }

    /** 키가 없거나 틀렸을 때. 401(키 오류), 403(앱에서 로컬 API를 쓸 수 없음) */
    public boolean isAuthError() {
        return httpCode == 401 || httpCode == 403;
    }
}
