package com.example.findcheapgas.data.repository;

import com.example.findcheapgas.data.local.DailyLimitReachedException;
import com.example.findcheapgas.data.remote.OpinetKeyMissingException;
import com.example.findcheapgas.data.remote.kakao.KakaoException;
import com.example.findcheapgas.model.SearchError;

import java.io.IOException;

/** 장소 검색, 주유소 이름 검색에서 난 예외를 화면에 보여줄 실패 원인으로 바꾼다. */
final class SearchErrors {

    private SearchErrors() {
    }

    static SearchError from(Exception e) {
        if (e instanceof KakaoException) {
            return ((KakaoException) e).isAuthError() ? SearchError.KAKAO_AUTH : SearchError.KAKAO;
        }
        if (e instanceof DailyLimitReachedException) {
            return SearchError.DAILY_LIMIT;
        }
        if (e instanceof OpinetKeyMissingException) {
            return SearchError.OPINET_KEY;
        }
        // 시간 초과(SocketTimeoutException)도 IOException이라 연결 문제로 본다.
        if (e instanceof IOException) {
            return SearchError.NETWORK;
        }
        // OpinetException, 알 수 없는 RuntimeException
        return SearchError.OPINET;
    }
}
