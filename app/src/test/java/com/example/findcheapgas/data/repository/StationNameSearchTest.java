package com.example.findcheapgas.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.StationSearchResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 주유소 이름 검색의 두 글자 규칙과 30분 캐시. 데이터 출처는 가짜로 넣어 호출 횟수를 센다. */
public class StationNameSearchTest {

    private static final long MINUTE = 60 * 1000;

    /** 메모리 캐시 (Room 대신) */
    private static final class MemoryCache implements StationNameSearch.Cache {
        final Map<String, List<StationSearchResult>> results = new HashMap<>();
        final Map<String, Long> savedAt = new HashMap<>();

        @Override
        public List<StationSearchResult> find(String key, long minSavedAt) {
            Long time = savedAt.get(key);
            return time != null && time >= minSavedAt ? results.get(key) : null;
        }

        @Override
        public void save(String key, List<StationSearchResult> list, long time) {
            results.put(key, list);
            savedAt.put(key, time);
        }

        @Override
        public void deleteOlderThan(long minSavedAt) {
            for (String key : new ArrayList<>(savedAt.keySet())) {
                if (savedAt.get(key) < minSavedAt) {
                    savedAt.remove(key);
                    results.remove(key);
                }
            }
        }
    }

    /** 부른 검색어를 기록하는 출처 */
    private static final class CountingSource implements StationNameSearch.Source {
        final List<String> calls = new ArrayList<>();

        @Override
        public List<StationSearchResult> search(String keyword) {
            calls.add(keyword);
            return Collections.singletonList(
                    new StationSearchResult("A1", "SKE", keyword + "주유소", "주소", 37.5, 127.0));
        }
    }

    private long now = 1_000_000;
    private final MemoryCache cache = new MemoryCache();
    private final CountingSource source = new CountingSource();
    private final StationNameSearch search = new StationNameSearch(cache, source, "mock", () -> now);

    @Test
    public void shorterThanTwoLetters_doesNotCallSource() throws Exception {
        assertTrue(search.search("역").isEmpty());
        assertTrue(search.search("  역  ").isEmpty());
        assertTrue(search.search("").isEmpty());
        assertTrue(search.search(null).isEmpty());
        assertTrue(source.calls.isEmpty());
    }

    @Test
    public void isSearchable_countsLettersWithoutSpaces() {
        assertFalse(StationNameSearch.isSearchable("역"));
        assertFalse(StationNameSearch.isSearchable(" 역 "));
        assertFalse(StationNameSearch.isSearchable("   "));
        assertTrue(StationNameSearch.isSearchable("역삼"));
        assertTrue(StationNameSearch.isSearchable(" 역 삼 "));
        assertTrue(StationNameSearch.isSearchable("GS"));
    }

    @Test
    public void sameKeywordWithin30Minutes_usesCache() throws Exception {
        search.search("역삼");
        now += 29 * MINUTE;
        List<StationSearchResult> second = search.search("  역삼 ");

        assertEquals(1, source.calls.size());
        assertEquals("역삼주유소", second.get(0).getName());
    }

    @Test
    public void after30Minutes_callsSourceAgain() throws Exception {
        search.search("역삼");
        now += 30 * MINUTE + 1;
        search.search("역삼");

        assertEquals(2, source.calls.size());
    }

    @Test
    public void differentKeyword_callsSource() throws Exception {
        search.search("역삼");
        search.search("셀프");
        assertEquals(2, source.calls.size());
    }

    @Test
    public void keyword_isNormalizedBeforeCallingSource() throws Exception {
        search.search("  역삼   셀프 ");
        assertEquals("역삼 셀프", source.calls.get(0));
    }

    @Test
    public void cacheKey_ignoresCaseAndKeepsSourceSeparate() {
        assertEquals(search.cacheKey("GS칼텍스"), search.cacheKey("gs칼텍스"));
        assertEquals("search|mock|역삼", search.cacheKey(" 역삼 "));
        StationNameSearch api = new StationNameSearch(cache, source, "api", () -> now);
        assertNotEquals(search.cacheKey("역삼"), api.cacheKey("역삼"));
    }
}
