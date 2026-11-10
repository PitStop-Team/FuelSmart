package com.example.findcheapgas.data.repository;

import static org.junit.Assert.assertEquals;

import com.example.findcheapgas.data.local.DailyLimitReachedException;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.data.remote.OpinetKeyMissingException;
import com.example.findcheapgas.data.remote.kakao.KakaoException;
import com.example.findcheapgas.model.SearchError;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

public class SearchErrorsTest {

    @Test
    public void kakaoKeyErrors_areAuth() {
        assertEquals(SearchError.KAKAO_AUTH, SearchErrors.from(new KakaoException("HTTP 401", 401)));
        assertEquals(SearchError.KAKAO_AUTH, SearchErrors.from(new KakaoException("HTTP 403", 403)));
    }

    @Test
    public void otherKakaoErrors() {
        assertEquals(SearchError.KAKAO, SearchErrors.from(new KakaoException("HTTP 500", 500)));
        assertEquals(SearchError.KAKAO, SearchErrors.from(new KakaoException("형식", new RuntimeException())));
    }

    @Test
    public void networkDailyLimitOpinet() {
        assertEquals(SearchError.NETWORK, SearchErrors.from(new IOException()));
        assertEquals(SearchError.NETWORK, SearchErrors.from(new SocketTimeoutException()));
        assertEquals(SearchError.DAILY_LIMIT, SearchErrors.from(new DailyLimitReachedException(300)));
        assertEquals(SearchError.OPINET, SearchErrors.from(new OpinetException("HTTP 500")));
        assertEquals(SearchError.OPINET_KEY, SearchErrors.from(new OpinetKeyMissingException()));
        assertEquals(SearchError.OPINET, SearchErrors.from(new IllegalStateException()));
    }
}
