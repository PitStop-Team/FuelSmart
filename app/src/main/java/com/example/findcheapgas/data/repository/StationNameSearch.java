package com.example.findcheapgas.data.repository;

import com.example.findcheapgas.data.local.DailyLimitReachedException;
import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.model.StationSearchResult;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * 주유소 이름 검색의 캐시 규칙. Android에 의존하지 않아서 단위 테스트로 확인한다.
 * - 검색어는 앞뒤 공백을 지우고 가운데 공백을 하나로 줄인 뒤, 두 글자 이상일 때만 검색한다.
 * - 같은 검색어(영문 대소문자 무시)는 30분 동안 캐시를 쓰고 데이터 출처를 다시 부르지 않는다.
 * StationSearchRepository가 Room 캐시와 가짜 데이터 / 실제 API 출처를 넣어서 쓴다.
 */
final class StationNameSearch {

    static final int MIN_KEYWORD_LENGTH = 2;
    static final long CACHE_TTL_MS = 30 * 60 * 1000;

    interface Cache {
        /** minSavedAt 이후에 저장된 결과. 없으면 null */
        List<StationSearchResult> find(String key, long minSavedAt);

        void save(String key, List<StationSearchResult> results, long savedAt);

        void deleteOlderThan(long minSavedAt);
    }

    interface Source {
        List<StationSearchResult> search(String keyword)
                throws IOException, OpinetException, DailyLimitReachedException;
    }

    private final Cache cache;
    private final Source source;
    // 가짜 데이터와 실제 API 결과가 섞이지 않도록 캐시 키에 넣는다 (mock 또는 api).
    private final String sourceName;
    private final LongSupplier clock;

    StationNameSearch(Cache cache, Source source, String sourceName, LongSupplier clock) {
        this.cache = cache;
        this.source = source;
        this.sourceName = sourceName;
        this.clock = clock;
    }

    /** 검색어 정리: 앞뒤 공백 제거, 가운데 공백은 하나로 */
    static String normalize(String keyword) {
        return keyword == null ? "" : keyword.trim().replaceAll("\\s+", " ");
    }

    /** 정리한 검색어가 두 글자 이상인지 (공백은 세지 않는다) */
    static boolean isSearchable(String keyword) {
        String letters = normalize(keyword).replace(" ", "");
        return letters.codePointCount(0, letters.length()) >= MIN_KEYWORD_LENGTH;
    }

    /** 예: search|mock|역삼 */
    String cacheKey(String keyword) {
        return "search|" + sourceName + "|" + normalize(keyword).toLowerCase(Locale.ROOT);
    }

    /** 두 글자보다 짧으면 출처를 부르지 않고 빈 목록을 돌려준다. 결과는 숨긴 브랜드를 빼기 전 전체 목록이다. */
    List<StationSearchResult> search(String keyword) throws IOException, OpinetException, DailyLimitReachedException {
        if (!isSearchable(keyword)) {
            return Collections.emptyList();
        }
        long now = clock.getAsLong();
        long minSavedAt = now - CACHE_TTL_MS;
        cache.deleteOlderThan(minSavedAt);

        String key = cacheKey(keyword);
        List<StationSearchResult> cached = cache.find(key, minSavedAt);
        if (cached != null) {
            return cached;
        }
        List<StationSearchResult> results = source.search(normalize(keyword));
        cache.save(key, results, now);
        return results;
    }
}
