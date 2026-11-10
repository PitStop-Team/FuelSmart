package com.example.findcheapgas.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/** 오피넷 하루 호출 한도 (300회에 도달하면 더 이상 호출하지 않음) */
public class ApiCallCounterTest {

    /** 메모리에 저장하는 가짜 저장소 */
    private static final class MemoryStorage implements ApiCallCounter.Storage {
        String date;
        int count;

        @Override
        public String getDate() {
            return date;
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public void save(String date, int count) {
            this.date = date;
            this.count = count;
        }
    }

    private final MemoryStorage storage = new MemoryStorage();
    private String today = "2026-11-04";
    private final ApiCallCounter counter = new ApiCallCounter(storage, () -> today);

    @Test
    public void acquire_countsUpTo300() throws DailyLimitReachedException {
        for (int i = 1; i <= ApiCallCounter.DAILY_LIMIT; i++) {
            assertEquals(i, counter.acquire());
        }
        assertEquals(300, counter.getTodayCount());
    }

    @Test
    public void acquire_blocksAt300AndDoesNotCount() {
        storage.save(today, 300);
        try {
            counter.acquire();
            fail("300회에 도달하면 호출을 막아야 합니다.");
        } catch (DailyLimitReachedException expected) {
            // 막힌 호출은 세지 않는다.
            assertEquals(300, counter.getTodayCount());
        }
    }

    @Test
    public void acquire_allows300thCall() throws DailyLimitReachedException {
        storage.save(today, 299);
        assertEquals(300, counter.acquire());
    }

    @Test
    public void newDay_resetsCount() throws DailyLimitReachedException {
        storage.save("2026-11-03", 300);
        assertEquals(0, counter.getTodayCount());
        assertEquals(1, counter.acquire());
        assertEquals(today, storage.date);
    }

    @Test
    public void dateChangesAfterReachingLimit_allowsAgain() throws DailyLimitReachedException {
        storage.save(today, 300);
        today = "2026-11-05";
        assertEquals(1, counter.acquire());
    }
}
