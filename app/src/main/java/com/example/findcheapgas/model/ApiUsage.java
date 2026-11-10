package com.example.findcheapgas.model;

/**
 * 설정 탭의 "오늘 오피넷 사용량". 오늘 호출 횟수는 ApiCallCounter가 센 값을 그대로 받는다 (따로 세지 않는다).
 * 250회부터 한도에 가까움, 하루 한도(300회)에 닿으면 한도 도달로 본다.
 */
public final class ApiUsage {

    /** 이 횟수부터 "한도에 가까워졌어요"로 경고한다. */
    public static final int WARNING_THRESHOLD = 250;

    public enum Level {
        NORMAL,
        NEAR_LIMIT,
        LIMIT_REACHED
    }

    private final int count;
    private final int limit;
    private final boolean mockData;

    /**
     * @param count    오늘 실제 호출 횟수 (ApiCallCounter.getTodayCount())
     * @param limit    하루 한도 (ApiCallCounter.DAILY_LIMIT)
     * @param mockData 가짜 데이터 모드면 true (오피넷을 호출하지 않음)
     */
    public ApiUsage(int count, int limit, boolean mockData) {
        this.count = Math.max(0, count);
        this.limit = limit;
        this.mockData = mockData;
    }

    public int getCount() {
        return count;
    }

    public int getLimit() {
        return limit;
    }

    public boolean isMockData() {
        return mockData;
    }

    public Level getLevel() {
        if (count >= limit) {
            return Level.LIMIT_REACHED;
        }
        if (count >= WARNING_THRESHOLD) {
            return Level.NEAR_LIMIT;
        }
        return Level.NORMAL;
    }

    /** 진행 막대 값 (0~100). 한도를 넘는 값은 100 */
    public int getProgressPercent() {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(100, count * 100 / limit);
    }
}
