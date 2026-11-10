package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSetting;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.PlaceCompareCard;
import com.example.findcheapgas.model.PlaceCompareCard.Slot;
import com.example.findcheapgas.model.PlaceCompareCard.Status;
import com.example.findcheapgas.model.SavingLevel;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.util.PlaceCompareBuilder.Source;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 집·회사 기준 비교 카드: 위치별 이득 1위, 예상 총비용 비교와 차이, 등록 안 함·주유소 없음·한도 도달 카드.
 * 실제 오피넷이나 카카오를 부르지 않고 만든 주유소 목록으로 계산한다.
 */
public class PlaceCompareBuilderTest {

    private static GasStation station(String id, String brand, int price, double distance) {
        return new GasStation(id, brand, id, price, distance, 0, 0, 0, 0);
    }

    // 연비 12km/L, 40L 기준 예상 총비용 = 표시 가격 × 40 + 직선거리 × 1.4 ÷ 12 × 표시 가격
    // 지금 조회 위치: a 68,079.33(기준) / b 67,396.00 / d 66,690.50 / c 66,385.00 → 이득 1위 c, 66,385원
    private static final List<GasStation> CURRENT = Arrays.asList(
            station("a", "SKE", 1700, 400),
            station("b", "GSC", 1680, 1000),
            station("c", "HDO", 1650, 2000),
            station("d", "SOL", 1660, 1500));

    // 집: h1 66,000 + 0.42 ÷ 12 × 1,650 = 66,057.75(기준), h2 64,000 + 3.5 ÷ 12 × 1,600 = 64,466.67 → 이득 1위 h2, 64,467원
    private static final List<GasStation> HOME = Arrays.asList(
            station("h1", "SKE", 1650, 300),
            station("h2", "GSC", 1600, 2500));

    // 회사: w1 하나뿐이라 기준이자 이득 1위. 65,600 + 0.7 ÷ 12 × 1,640 = 65,695.67 → 65,696원
    private static final List<GasStation> WORK = Collections.singletonList(
            station("w1", "SOL", 1640, 500));

    private static List<PlaceCompareCard> build(Source current, Source home, Source work) {
        return build(BrandSettings.EMPTY, current, home, work);
    }

    private static List<PlaceCompareCard> build(BrandSettings settings, Source current, Source home, Source work) {
        return PlaceCompareBuilder.build(Arrays.asList(current, home, work), SearchRadius.KM_3, CarInfo.DEFAULT,
                settings, BrandFilter.ALL);
    }

    @Test
    public void eachPlace_picksBestSavingStationFromThatPlace() {
        List<PlaceCompareCard> cards = build(Source.loaded(Slot.CURRENT, CURRENT, 0),
                Source.loaded(Slot.HOME, HOME, 1000L), Source.loaded(Slot.WORK, WORK, 2000L));

        assertEquals(Slot.CURRENT, cards.get(0).getSlot());
        assertEquals(Slot.HOME, cards.get(1).getSlot());
        assertEquals(Slot.WORK, cards.get(2).getSlot());
        assertEquals("c", cards.get(0).getBest().getStation().getId());
        assertEquals("h2", cards.get(1).getBest().getStation().getId());
        assertEquals("w1", cards.get(2).getBest().getStation().getId());
        assertEquals(66385, cards.get(0).getTotalCostWon());
        assertEquals(64467, cards.get(1).getTotalCostWon());
        assertEquals(65696, cards.get(2).getTotalCostWon());
        assertEquals(0L, cards.get(0).getFetchedAt());
        assertEquals(1000L, cards.get(1).getFetchedAt());
    }

    @Test
    public void placesCompared_byTotalCost_cheapestMarkedAndOthersShowDifference() {
        List<PlaceCompareCard> cards = build(Source.loaded(Slot.CURRENT, CURRENT, 0),
                Source.loaded(Slot.HOME, HOME, 0), Source.loaded(Slot.WORK, WORK, 0));

        assertTrue(cards.get(1).isCheapest());
        assertEquals(0, cards.get(1).getExtraCostWon());
        assertFalse(cards.get(0).isCheapest());
        assertEquals(66385 - 64467, cards.get(0).getExtraCostWon());
        assertFalse(cards.get(2).isCheapest());
        assertEquals(65696 - 64467, cards.get(2).getExtraCostWon());
    }

    /**
     * 예상 이득으로 비교하면 안 되는 예: 지금 조회 위치의 이득 1위 c는 1,694원 이득, 회사의 w1은 기준이라 0원 이득이지만
     * 예상 총비용은 w1(65,696원)이 c(66,385원)보다 낮다. 비교는 예상 총비용으로 한다.
     */
    @Test
    public void largerSavingDoesNotMeanCheaper() {
        List<PlaceCompareCard> cards = build(Source.loaded(Slot.CURRENT, CURRENT, 0),
                Source.of(Slot.HOME, Status.NOT_REGISTERED), Source.loaded(Slot.WORK, WORK, 0));

        assertEquals(1694, cards.get(0).getBest().getSavingWon());
        assertEquals(0, cards.get(2).getBest().getSavingWon());
        assertTrue(cards.get(2).isCheapest());
        assertFalse(cards.get(0).isCheapest());
        assertEquals(689, cards.get(0).getExtraCostWon());
    }

    @Test
    public void sameTotalCost_bothCheapest() {
        List<PlaceCompareCard> cards = build(Source.loaded(Slot.CURRENT, WORK, 0),
                Source.loaded(Slot.HOME, WORK, 0), Source.of(Slot.WORK, Status.NOT_REGISTERED));

        assertTrue(cards.get(0).isCheapest());
        assertTrue(cards.get(1).isCheapest());
        assertEquals(0, cards.get(0).getExtraCostWon());
    }

    @Test
    public void onlyOneReadyCard_notMarkedCheapest_otherCardsKeepTheirReason() {
        List<PlaceCompareCard> cards = build(Source.loaded(Slot.CURRENT, CURRENT, 0),
                Source.of(Slot.HOME, Status.NOT_REGISTERED), Source.error(Slot.WORK, StationError.DAILY_LIMIT));

        assertEquals(Status.READY, cards.get(0).getStatus());
        assertFalse(cards.get(0).isCheapest());
        assertEquals(0, cards.get(0).getExtraCostWon());

        assertEquals(Status.NOT_REGISTERED, cards.get(1).getStatus());
        assertNull(cards.get(1).getBest());

        assertEquals(Status.ERROR, cards.get(2).getStatus());
        assertEquals(StationError.DAILY_LIMIT, cards.get(2).getError());
        assertNull(cards.get(2).getBest());
    }

    @Test
    public void noStationInRadius_emptyCard_othersStillCompared() {
        List<GasStation> far = Collections.singletonList(station("f", "SKE", 1500, 4000));
        List<PlaceCompareCard> cards = build(Source.loaded(Slot.CURRENT, CURRENT, 0),
                Source.loaded(Slot.HOME, HOME, 0), Source.loaded(Slot.WORK, far, 0));

        assertEquals(Status.EMPTY, cards.get(2).getStatus());
        assertEquals(StationListBuilder.EmptyReason.RADIUS, cards.get(2).getEmptyReason());
        assertNull(cards.get(2).getBest());
        assertTrue(cards.get(1).isCheapest());
        assertEquals(1918, cards.get(0).getExtraCostWon());
    }

    @Test
    public void emptyFetchResult_emptyCard() {
        List<PlaceCompareCard> cards = build(Source.loaded(Slot.CURRENT, Collections.emptyList(), 0),
                Source.of(Slot.HOME, Status.LOADING), Source.of(Slot.WORK, Status.OUTSIDE_MOCK_AREA));

        assertEquals(Status.EMPTY, cards.get(0).getStatus());
        assertEquals(StationListBuilder.EmptyReason.RADIUS, cards.get(0).getEmptyReason());
        assertEquals(Status.LOADING, cards.get(1).getStatus());
        assertEquals(Status.OUTSIDE_MOCK_AREA, cards.get(2).getStatus());
    }

    @Test
    public void hiddenBrandsExcludedBeforeCalculation() {
        // 집의 GSC(h2)를 숨기면 h1만 남아 h1이 기준이자 이득 1위
        BrandSettings settings = BrandSettings.EMPTY.with("GSC", new BrandSetting(0, null, false, true));
        List<PlaceCompareCard> cards = build(settings, Source.loaded(Slot.CURRENT, CURRENT, 0),
                Source.loaded(Slot.HOME, HOME, 0), Source.loaded(Slot.WORK, WORK, 0));

        assertEquals("h1", cards.get(1).getBest().getStation().getId());
        assertEquals(66058, cards.get(1).getTotalCostWon());
        // 이제 회사(65,696원)가 가장 저렴
        assertTrue(cards.get(2).isCheapest());

        List<GasStation> allHidden = Collections.singletonList(station("g", "GSC", 1600, 500));
        List<PlaceCompareCard> hiddenCards = build(settings, Source.loaded(Slot.CURRENT, CURRENT, 0),
                Source.loaded(Slot.HOME, allHidden, 0), Source.of(Slot.WORK, Status.NOT_REGISTERED));
        assertEquals(Status.EMPTY, hiddenCards.get(1).getStatus());
        assertEquals(StationListBuilder.EmptyReason.HIDDEN_BRANDS, hiddenCards.get(1).getEmptyReason());
    }

    @Test
    public void brandDiscount_includedInTotalCost() {
        // 집의 GSC에 리터당 100원, 1회 한도 3,000원 → h2 예상 총비용 64,466.67 − 3,000 = 61,466.67
        BrandSettings settings = BrandSettings.EMPTY.with("GSC", new BrandSetting(100, 3000, false, false));
        List<PlaceCompareCard> cards = build(settings, Source.loaded(Slot.CURRENT, CURRENT, 0),
                Source.loaded(Slot.HOME, HOME, 0), Source.of(Slot.WORK, Status.NOT_REGISTERED));

        assertEquals(61467, cards.get(1).getTotalCostWon());
    }

    @Test
    public void preferredBrand_doesNotChangeBest() {
        // x1(SKE)은 기준, x2(GSC)는 65,991.92로 66원 이득. SKE를 선호해도 이득 1위는 실제 예상 이득으로 x2
        List<GasStation> list = Arrays.asList(
                station("x1", "SKE", 1650, 300),
                station("x2", "GSC", 1645, 1000));
        BrandSettings settings = BrandSettings.EMPTY.with("SKE", new BrandSetting(0, null, true, false));
        List<PlaceCompareCard> cards = build(settings, Source.loaded(Slot.CURRENT, list, 0),
                Source.of(Slot.HOME, Status.NOT_REGISTERED), Source.of(Slot.WORK, Status.NOT_REGISTERED));

        assertEquals("x2", cards.get(0).getBest().getStation().getId());
        assertEquals(65992, cards.get(0).getTotalCostWon());
    }

    @Test
    public void findBest_nullOrEmpty() {
        assertNull(PlaceCompareBuilder.findBest(null));
        assertNull(PlaceCompareBuilder.findBest(Collections.emptyList()));
    }

    // 회사 근처 w1(65,696원)과 비슷한 곳: 65,800 + 0.7 ÷ 12 × 1,645 = 65,895.96 → 65,896원 (200원 차이)
    private static final List<GasStation> NEAR_WORK = Collections.singletonList(
            station("n1", "SOL", 1645, 500));

    @Test
    public void withinThreshold_similarToCheapest_andHeroSaysSimilar() {
        List<PlaceCompareCard> cards = build(Source.of(Slot.CURRENT, Status.WAITING),
                Source.loaded(Slot.HOME, NEAR_WORK, 0), Source.loaded(Slot.WORK, WORK, 0));

        // "가장 저렴" 판정과 차이 금액은 그대로
        assertTrue(cards.get(2).isCheapest());
        assertFalse(cards.get(2).isSimilarToCheapest());
        assertFalse(cards.get(1).isCheapest());
        assertEquals(200, cards.get(1).getExtraCostWon());
        assertTrue(cards.get(1).isSimilarToCheapest());
        assertTrue(PlaceCompareBuilder.allSimilarToCheapest(cards));
    }

    @Test
    public void overThreshold_notSimilar_andHeroKeepsCheapest() {
        // 지금 조회 위치 1,918원, 회사 1,229원 더 듦 → 둘 다 1,000원 이상이라 "더 들어요"
        List<PlaceCompareCard> cards = build(Source.loaded(Slot.CURRENT, CURRENT, 0),
                Source.loaded(Slot.HOME, HOME, 0), Source.loaded(Slot.WORK, WORK, 0));

        assertFalse(cards.get(0).isSimilarToCheapest());
        assertFalse(cards.get(2).isSimilarToCheapest());
        assertFalse(PlaceCompareBuilder.allSimilarToCheapest(cards));
    }

    @Test
    public void threeCardsAllWithin_similar_butOneOver_cheapest() {
        // 회사 65,696 가장 저렴, 집 65,896(200원 차이, 비슷), 지금 조회 위치 66,385(689원 차이, 비슷)
        List<PlaceCompareCard> similar = build(Source.loaded(Slot.CURRENT, CURRENT, 0),
                Source.loaded(Slot.HOME, NEAR_WORK, 0), Source.loaded(Slot.WORK, WORK, 0));
        assertTrue(similar.get(0).isSimilarToCheapest());
        assertTrue(PlaceCompareBuilder.allSimilarToCheapest(similar));

        // 집 64,467 가장 저렴, 회사 1,229원(손해 구간), 지금 조회 위치 없음 → 하나라도 1,000원 이상이면 결론은 "가장 저렴"
        List<PlaceCompareCard> notSimilar = build(Source.of(Slot.CURRENT, Status.WAITING),
                Source.loaded(Slot.HOME, HOME, 0), Source.loaded(Slot.WORK, WORK, 0));
        assertFalse(PlaceCompareBuilder.allSimilarToCheapest(notSimilar));
    }

    @Test
    public void threshold_isSavingLevelBoundary() {
        // 지도 탭과 같은 기준: 999원 차이는 비슷, 1,000원 차이부터는 "더 들어요"
        assertTrue(card(999).isSimilarToCheapest());
        assertFalse(card(SavingLevel.THRESHOLD_WON).isSimilarToCheapest());
        assertFalse(card(0).isSimilarToCheapest());
    }

    @Test
    public void allSimilar_needsTwoReadyCards() {
        List<PlaceCompareCard> cards = build(Source.loaded(Slot.CURRENT, CURRENT, 0),
                Source.of(Slot.HOME, Status.NOT_REGISTERED), Source.of(Slot.WORK, Status.NOT_REGISTERED));
        assertFalse(PlaceCompareBuilder.allSimilarToCheapest(cards));
        assertFalse(PlaceCompareBuilder.allSimilarToCheapest(null));
    }

    private static PlaceCompareCard card(long extra) {
        return new PlaceCompareCard(Slot.HOME, Status.READY, null, StationListBuilder.EmptyReason.NONE, null, 0,
                extra == 0, extra);
    }
}
