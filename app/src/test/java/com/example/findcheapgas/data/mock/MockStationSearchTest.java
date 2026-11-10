package com.example.findcheapgas.data.mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.data.remote.OpinetException;
import com.example.findcheapgas.data.remote.OpinetSearchResponse;
import com.example.findcheapgas.model.StationSearchResult;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 가짜 데이터 주유소 이름 검색. 앱과 같은 방법으로 assets/mock/detail 파일 전체를 상호 검색 응답으로 읽고
 * 상호에 검색어가 들어간 주유소를 찾는다.
 */
public class MockStationSearchTest {

    // 단위 테스트는 app 모듈 폴더에서 실행된다.
    private static final File DETAIL_DIR = new File("src/main/assets/mock/detail");

    private static List<StationSearchResult> allMockStations() throws IOException, OpinetException {
        File[] files = DETAIL_DIR.listFiles();
        assertTrue(files != null && files.length > 0);
        List<StationSearchResult> all = new ArrayList<>();
        for (File file : files) {
            String body = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            all.addAll(OpinetSearchResponse.parse(body));
        }
        return all;
    }

    private static List<StationSearchResult> search(String keyword) throws IOException, OpinetException {
        return MockOpinetDataSource.filterByName(allMockStations(), keyword);
    }

    @Test
    public void everyDetailFile_isReadableAsSearchResult() throws IOException, OpinetException {
        List<StationSearchResult> all = allMockStations();
        assertEquals(DETAIL_DIR.listFiles().length, all.size());
        for (StationSearchResult station : all) {
            assertFalse(station.getId().isEmpty());
            assertFalse(station.getBrandCode().isEmpty());  // 상세 파일은 POLL_DIV_CO
            assertFalse(station.getAddress().isEmpty());
            // 강남역 주변 (WGS84로 바뀌었는지)
            assertEquals(37.5, station.getLatitude(), 0.1);
            assertEquals(127.03, station.getLongitude(), 0.1);
        }
    }

    @Test
    public void yeoksam_findsStationsWithNameContainingKeyword() throws IOException, OpinetException {
        assertMatches("역삼", 2);
    }

    @Test
    public void self_findsSelfStations() throws IOException, OpinetException {
        assertMatches("셀프", 5);
    }

    @Test
    public void altteul_findsAltteulStations() throws IOException, OpinetException {
        assertMatches("알뜰", 3);
    }

    @Test
    public void ignoresSpacesAndCase() throws IOException, OpinetException {
        assertEquals(search("역삼셀프").size(), search("역삼 셀프").size());
        assertFalse(search("역삼 셀프").isEmpty());
    }

    @Test
    public void noMatch_returnsEmpty() throws IOException, OpinetException {
        assertTrue(search("해운대").isEmpty());
        assertTrue(search("").isEmpty());
    }

    @Test
    public void mockArea_gangnamInside_farAwayOutside() {
        assertFalse(MockOpinetDataSource.isOutsideMockArea(37.4979, 127.0276));  // 강남역
        assertFalse(MockOpinetDataSource.isOutsideMockArea(37.5045, 127.0490));  // 선릉역 (약 2km)
        assertTrue(MockOpinetDataSource.isOutsideMockArea(37.5547, 126.9707));   // 서울역 (약 8km)
        assertTrue(MockOpinetDataSource.isOutsideMockArea(35.1631, 129.1635));   // 해운대
    }

    /** 결과가 최소 개수 이상이고, 모두 상호에 검색어가 들어 있다. */
    private static void assertMatches(String keyword, int atLeast) throws IOException, OpinetException {
        List<StationSearchResult> result = search(keyword);
        assertTrue(keyword + " 결과 " + result.size() + "곳", result.size() >= atLeast);
        for (StationSearchResult station : result) {
            assertTrue(station.getName(), station.getName().contains(keyword));
        }
    }
}
