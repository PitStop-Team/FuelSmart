package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSetting;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.DiscountRule;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationSaving;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** 내 브랜드 설정(할인, 선호, 숨기기)과 브랜드 빠른 필터를 거친 화면 목록 */
public class StationListBuilderBrandTest {

    private static GasStation station(String id, String brand, int price, double distance) {
        return new GasStation(id, brand, id, price, distance, 0, 0, 0, 0);
    }

    // 연비 12km/L, 40L 기준 예상 총비용
    // a: 68,000 + 0.56km ÷ 12 × 1,700 = 68,079.33
    // b: 67,200 + 1.4km ÷ 12 × 1,680 = 67,396.00  → 기준 a 대비 683원 이득
    // d: 66,400 + 2.1km ÷ 12 × 1,660 = 66,690.50  → 1,389원 이득
    // c: 66,000 + 2.8km ÷ 12 × 1,650 = 66,385.00  → 1,694원 이득
    private static final GasStation A = station("a", "SKE", 1700, 400);
    private static final GasStation B = station("b", "GSC", 1680, 1000);
    private static final GasStation D = station("d", "SOL", 1660, 1500);
    private static final GasStation C = station("c", "HDO", 1650, 2000);
    private static final List<GasStation> ALL = Arrays.asList(A, B, C, D);

    private static List<StationSaving> build(BrandSettings settings, BrandFilter filter, SortOrder order) {
        return StationListBuilder.build(ALL, SearchRadius.KM_3, order, CarInfo.DEFAULT, settings, filter);
    }

    private static List<String> ids(List<StationSaving> savings) {
        List<String> result = new ArrayList<>();
        for (StationSaving saving : savings) {
            result.add(saving.getStation().getId());
        }
        return result;
    }

    private static StationSaving find(List<StationSaving> savings, String id) {
        for (StationSaving saving : savings) {
            if (saving.getStation().getId().equals(id)) {
                return saving;
            }
        }
        throw new AssertionError("없음: " + id);
    }

    private static BrandSettings settings(String brand, BrandSetting setting) {
        return BrandSettings.EMPTY.with(brand, setting);
    }

    @Test
    public void noBrandSettings_savingsAsBefore() {
        List<StationSaving> result = build(BrandSettings.EMPTY, BrandFilter.ALL, SortOrder.SAVING);
        assertEquals(Arrays.asList("c", "d", "b", "a"), ids(result));
        assertEquals(1694, find(result, "c").getSavingWon());
        assertEquals(1389, find(result, "d").getSavingWon());
        assertEquals(683, find(result, "b").getSavingWon());
    }

    @Test
    public void brandDiscount_appliedWithCap() {
        // GS칼텍스 리터당 100원, 1회 한도 3,000원: 40L × 100원 = 4,000원이지만 3,000원만 할인
        BrandSettings settings = settings("GSC", new BrandSetting(100, 3_000, false, false));
        StationSaving b = find(build(settings, BrandFilter.ALL, SortOrder.SAVING), "b");
        assertEquals(3_000, b.getDiscountAmount(), 1e-9);
        assertTrue(b.isDiscountCapApplied());
        assertEquals(683 + 3_000, b.getSavingWon());
    }

    @Test
    public void brandDiscount_noCapMeansNoLimit() {
        BrandSettings settings = settings("GSC", new BrandSetting(150, null, false, false));
        StationSaving b = find(build(settings, BrandFilter.ALL, SortOrder.SAVING), "b");
        assertEquals(6_000, b.getDiscountAmount(), 1e-9);
        assertFalse(b.isDiscountCapEntered());
        assertFalse(b.isDiscountCapApplied());
    }

    @Test
    public void baselineBrandDiscount_appliedToBaselineToo() {
        // 기준 a(SK에너지)에 3,000원 할인 → 기준 총비용 65,079.33. 다른 곳의 이득이 3,000원씩 줄어든다.
        BrandSettings settings = settings("SKE", new BrandSetting(100, 3_000, false, false));
        List<StationSaving> result = build(settings, BrandFilter.ALL, SortOrder.SAVING);
        assertEquals(65_079.33, find(result, "a").getBaselineTotalCost(), 0.01);
        assertEquals(1694 - 3_000, find(result, "c").getSavingWon());
        assertEquals(683 - 3_000, find(result, "b").getSavingWon());
        // 모두 손해가 되면 가장 가까운 기준이 이득 1위
        assertTrue(find(result, "a").isBest());
    }

    @Test
    public void preferred_raisesWithin500Won_inSavingOrderOnly() {
        // c 1,694원 vs d(S-OIL 선호) 1,389원 + 500 = 1,889 → d가 위로
        BrandSettings settings = settings("SOL", new BrandSetting(0, null, true, false));
        List<StationSaving> result = build(settings, BrandFilter.ALL, SortOrder.SAVING);
        assertEquals(Arrays.asList("d", "c", "b", "a"), ids(result));
        // 표시 금액과 이득 1위는 실제 예상 이득 그대로
        assertEquals(1389, find(result, "d").getSavingWon());
        assertTrue(find(result, "d").isPreferred());
        assertTrue(find(result, "c").isBest());
        assertFalse(find(result, "d").isBest());
    }

    @Test
    public void preferred_doesNotRaiseWhenGapOver500Won() {
        // b(GS 선호) 683 + 500 = 1,183 < d 1,389
        BrandSettings settings = settings("GSC", new BrandSetting(0, null, true, false));
        assertEquals(Arrays.asList("c", "d", "b", "a"), ids(build(settings, BrandFilter.ALL, SortOrder.SAVING)));
    }

    @Test
    public void preferred_ignoredInPriceAndDistanceOrder() {
        BrandSettings settings = settings("SKE", new BrandSetting(0, null, true, false));
        assertEquals(Arrays.asList("c", "d", "b", "a"), ids(build(settings, BrandFilter.ALL, SortOrder.PRICE)));
        assertEquals(Arrays.asList("a", "b", "d", "c"), ids(build(settings, BrandFilter.ALL, SortOrder.DISTANCE)));
    }

    @Test
    public void preferredSortOrder_isTransitive() {
        // 정렬용 점수 하나로 비교하므로 a ≤ b, b ≤ c 이면 a ≤ c 가 항상 성립한다.
        Random random = new Random(42);
        GasStation base = station("base", "SKE", 1700, 100);
        List<StationSaving> list = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            GasStation s = station("s" + i, "GSC", 1600 + random.nextInt(5) * 10, 500 + random.nextInt(4000));
            double total = 70_000 - random.nextInt(1_500);
            list.add(new StationSaving(s, base, CarInfo.DEFAULT, DiscountRule.NONE, 0, 0, 0, 0, total,
                    70_000, false).withPreferred(random.nextBoolean()));
        }
        Comparator<StationSaving> cmp = SortOrder.SAVING.getComparator();
        for (StationSaving x : list) {
            for (StationSaving y : list) {
                assertEquals(Integer.signum(cmp.compare(x, y)), -Integer.signum(cmp.compare(y, x)));
                for (StationSaving z : list) {
                    if (cmp.compare(x, y) <= 0 && cmp.compare(y, z) <= 0) {
                        assertTrue(cmp.compare(x, z) <= 0);
                    }
                }
            }
        }
        Collections.sort(list, cmp);
        for (int i = 1; i < list.size(); i++) {
            assertTrue(list.get(i - 1).getSortScoreWon() >= list.get(i).getSortScoreWon());
        }
    }

    @Test
    public void hiddenBrand_excludedBeforeCalculation() {
        // a(SK에너지)를 숨기면 화면에 보이는 가장 가까운 b가 기준이 된다.
        BrandSettings settings = settings("SKE", new BrandSetting(0, null, false, true));
        List<StationSaving> result = build(settings, BrandFilter.ALL, SortOrder.SAVING);
        assertEquals(Arrays.asList("c", "d", "b"), ids(result));
        assertEquals("b", result.get(0).getBaseline().getId());
        assertEquals(1011, find(result, "c").getSavingWon()); // 67,396 − 66,385
    }

    @Test
    public void brandFilter_appliedBeforeCalculation() {
        // 현대, S-OIL만 보면 그중 가까운 d가 기준
        BrandFilter filter = new BrandFilter(false, false, new HashSet<>(Arrays.asList("HDO", "SOL")));
        List<StationSaving> result = build(BrandSettings.EMPTY, filter, SortOrder.SAVING);
        assertEquals(Arrays.asList("c", "d"), ids(result));
        assertTrue(find(result, "d").isBaseline());
        assertEquals(305.5, find(result, "c").getSaving(), 0.01); // 66,690.5 − 66,385
    }

    @Test
    public void brandFilter_discountOnlyAndPreferredOnly() {
        BrandSettings settings = BrandSettings.EMPTY
                .with("GSC", new BrandSetting(50, null, false, false))
                .with("HDO", new BrandSetting(0, null, true, false));
        assertEquals(Arrays.asList("b"),
                ids(build(settings, BrandFilter.ALL.withDiscountOnly(true), SortOrder.DISTANCE)));
        assertEquals(Arrays.asList("c"),
                ids(build(settings, BrandFilter.ALL.withPreferredOnly(true), SortOrder.DISTANCE)));
        // 둘 다 고르면 하나라도 맞는 곳
        assertEquals(Arrays.asList("b", "c"), ids(build(settings,
                BrandFilter.ALL.withDiscountOnly(true).withPreferredOnly(true), SortOrder.DISTANCE)));
    }

    @Test
    public void brandFilter_hiddenBrandCheckIgnored() {
        // 체크해 둔 브랜드를 나중에 숨기면 그 체크는 무시되고, 남는 게 없으면 전체
        BrandSettings settings = settings("SKE", new BrandSetting(0, null, false, true));
        BrandFilter filter = BrandFilter.ALL.withBrand("SKE", true);
        assertEquals(Arrays.asList("b", "d", "c"), ids(build(settings, filter, SortOrder.DISTANCE)));
    }

    @Test
    public void emptyReason_distinguishesRadiusHiddenAndFilter() {
        List<GasStation> one = Collections.singletonList(station("x", "SKE", 1700, 2500));
        BrandFilter gsOnly = BrandFilter.ALL.withBrand("GSC", true);
        assertEquals(StationListBuilder.EmptyReason.RADIUS, StationListBuilder.emptyReason(
                one, SearchRadius.KM_1, BrandSettings.EMPTY, BrandFilter.ALL));
        assertEquals(StationListBuilder.EmptyReason.HIDDEN_BRANDS, StationListBuilder.emptyReason(
                one, SearchRadius.KM_3, settings("SKE", new BrandSetting(0, null, false, true)), BrandFilter.ALL));
        assertEquals(StationListBuilder.EmptyReason.BRAND_FILTER, StationListBuilder.emptyReason(
                one, SearchRadius.KM_3, BrandSettings.EMPTY, gsOnly));
        assertEquals(StationListBuilder.EmptyReason.NONE, StationListBuilder.emptyReason(
                one, SearchRadius.KM_3, BrandSettings.EMPTY, BrandFilter.ALL));
        assertTrue(StationListBuilder.build(one, SearchRadius.KM_3, SortOrder.SAVING, CarInfo.DEFAULT,
                BrandSettings.EMPTY, gsOnly).isEmpty());
    }
}
