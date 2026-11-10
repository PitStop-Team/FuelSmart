package com.example.findcheapgas.data.remote;

/** 오피넷이 정상 결과가 아닌 응답을 보냈을 때 (HTTP 오류, 한도 초과, 키 오류, 형식 오류 등) */
public class OpinetException extends Exception {

    public OpinetException(String message) {
        super(message);
    }

    public OpinetException(String message, Throwable cause) {
        super(message, cause);
    }
}
