package com.example.findcheapgas.data.local;

/** 오늘 오피넷 실제 호출이 하루 한도(ApiCallCounter.DAILY_LIMIT)에 도달해서 호출하지 않았을 때 */
public class DailyLimitReachedException extends Exception {

    /** 오피넷 하루 호출 한도. ApiCallCounter.DAILY_LIMIT이 이 값을 쓴다. */
    public static final int DAILY_LIMIT = 300;

    public DailyLimitReachedException(int todayCount) {
        super("오늘 오피넷 호출 " + todayCount + "회로 하루 한도 " + DAILY_LIMIT + "회에 도달했습니다.");
    }
}
