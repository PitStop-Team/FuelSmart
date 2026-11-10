package com.example.findcheapgas.data.repository;

import static org.junit.Assert.assertEquals;

import com.example.findcheapgas.data.local.DailyLimitReachedException;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.data.remote.OpinetKeyMissingException;
import com.example.findcheapgas.model.StationError;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

/** 주변 조회와 상세 조회가 같이 쓰는 실패 원인 구분 */
public class StationErrorsTest {

    @Test
    public void dailyLimit() {
        assertEquals(StationError.DAILY_LIMIT, StationErrors.from(new DailyLimitReachedException(300)));
    }

    @Test
    public void network() {
        assertEquals(StationError.NETWORK, StationErrors.from(new IOException("연결 없음")));
        assertEquals(StationError.NETWORK, StationErrors.from(new SocketTimeoutException()));
    }

    @Test
    public void missingApiKey_isKeyProblem_notGenericOpinetError() {
        try {
            OpinetKeyMissingException.requireKey("");
        } catch (OpinetKeyMissingException e) {
            assertEquals(StationError.API_KEY, StationErrors.from(e));
            return;
        }
        throw new AssertionError("빈 키인데 예외가 나지 않음");
    }

    @Test
    public void opinetAndUnknown() {
        assertEquals(StationError.OPINET, StationErrors.from(new OpinetException("한도 초과 응답")));
        assertEquals(StationError.OPINET, StationErrors.from(new IllegalStateException()));
    }
}
