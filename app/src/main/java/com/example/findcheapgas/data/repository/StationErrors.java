package com.example.findcheapgas.data.repository;

import com.example.findcheapgas.data.local.DailyLimitReachedException;
import com.example.findcheapgas.data.remote.OpinetKeyMissingException;
import com.example.findcheapgas.data.remote.StationNotFoundException;
import com.example.findcheapgas.model.StationError;

import java.io.IOException;

/** 주변 조회와 상세 조회에서 난 예외를 화면에 보여줄 실패 원인으로 바꾼다. 두 Repository가 같이 쓴다. */
public final class StationErrors {

    private StationErrors() {
    }

    public static StationError from(Exception e) {
        if (e instanceof DailyLimitReachedException) {
            return StationError.DAILY_LIMIT;
        }
        if (e instanceof StationNotFoundException) {
            return StationError.NOT_FOUND;
        }
        if (e instanceof OpinetKeyMissingException) {
            return StationError.API_KEY;
        }
        // 시간 초과(SocketTimeoutException)도 IOException이라 연결 문제로 본다.
        if (e instanceof IOException) {
            return StationError.NETWORK;
        }
        // OpinetException, 알 수 없는 RuntimeException
        return StationError.OPINET;
    }
}
