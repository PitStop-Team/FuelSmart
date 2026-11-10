package com.example.findcheapgas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.AverageComparison;
import com.example.findcheapgas.model.AveragePrice;
import com.example.findcheapgas.model.AveragePrices;
import com.example.findcheapgas.model.FuelPrice;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.StationDetail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AverageComparisonBuilderTest {

    private static final AveragePrices AVERAGES = new AveragePrices(
            Arrays.asList(
                    AveragePrice.national(FuelType.GASOLINE, 1712.35),
                    AveragePrice.national(FuelType.DIESEL, 1528.74)),
            Arrays.asList(
                    new AveragePrice(FuelType.GASOLINE, 1767.60, "01", "서울"),
                    new AveragePrice(FuelType.DIESEL, 1601.35, "01", "서울")));

    private static StationDetail detail(String address, FuelPrice... prices) {
        return new StationDetail("A0000001", "역삼셀프주유소", "SKE", address, "", "", false, false, false,
                false, null, 0, 0, Arrays.asList(prices));
    }

    private static FuelPrice gasoline(int price) {
        return new FuelPrice(FuelType.GASOLINE, price, "20261106", "090000");
    }

    @Test
    public void cheaperThanNationalAndSeoul_userExample() {
        AverageComparison result = AverageComparisonBuilder.build(FuelType.GASOLINE,
                detail("서울특별시 강남구 테헤란로 152", gasoline(1671)), 0, null, AVERAGES, 40);

        assertEquals(AverageComparison.Status.READY, result.getStatus());
        assertEquals(1671, result.getStationPrice());
        List<AverageComparison.Line> lines = result.getLines();
        assertEquals(2, lines.size());

        assertEquals("전국", lines.get(0).getRegionLabel());
        assertEquals(1712, lines.get(0).getAverageWon());
        assertEquals(-41, lines.get(0).getDifferencePerLiter());
        assertEquals(-1640, lines.get(0).getDifferenceForFuelAmount());

        assertEquals("서울", lines.get(1).getRegionLabel());
        assertEquals(1768, lines.get(1).getAverageWon());
        assertEquals(-97, lines.get(1).getDifferencePerLiter());
    }

    @Test
    public void pricierAndEqual() {
        AverageComparison pricier = AverageComparisonBuilder.build(FuelType.GASOLINE,
                detail("서울특별시 강남구", gasoline(1780)), 0, null, AVERAGES, 50);
        assertEquals(68, pricier.getLines().get(0).getDifferencePerLiter());
        assertEquals(3400, pricier.getLines().get(0).getDifferenceForFuelAmount());
        assertEquals(12, pricier.getLines().get(1).getDifferencePerLiter());

        AverageComparison equal = AverageComparisonBuilder.build(FuelType.GASOLINE,
                detail("서울특별시 강남구", gasoline(1712)), 0, null, AVERAGES, 40);
        assertEquals(0, equal.getLines().get(0).getDifferencePerLiter());
    }

    @Test
    public void usesDetailPriceOfSelectedFuel_notListedPrice() {
        AverageComparison result = AverageComparisonBuilder.build(FuelType.DIESEL,
                detail("서울특별시 강남구", gasoline(1671), new FuelPrice(FuelType.DIESEL, 1519, "", "")),
                1500, null, AVERAGES, 40);
        assertEquals(1519, result.getStationPrice());
        assertEquals(-10, result.getLines().get(0).getDifferencePerLiter());
    }

    @Test
    public void notSold_whenDetailHasNoPriceForFuel() {
        AverageComparison result = AverageComparisonBuilder.build(FuelType.DIESEL,
                detail("서울특별시 강남구", gasoline(1671)), 1519, null, AVERAGES, 40);
        assertEquals(AverageComparison.Status.NOT_SOLD, result.getStatus());
        assertTrue(result.getLines().isEmpty());
    }

    @Test
    public void unknownSido_showsNationalOnly() {
        AverageComparison result = AverageComparisonBuilder.build(FuelType.GASOLINE,
                detail("강남구 테헤란로 152", gasoline(1671)), 0, null, AVERAGES, 40);
        assertEquals(1, result.getLines().size());
        assertEquals("전국", result.getLines().get(0).getRegionLabel());

        AverageComparison noAddress = AverageComparisonBuilder.build(FuelType.GASOLINE,
                detail("", gasoline(1671)), 0, null, AVERAGES, 40);
        assertEquals(1, noAddress.getLines().size());
    }

    @Test
    public void sidoFetchFailed_showsNationalOnly() {
        AveragePrices nationalOnly = new AveragePrices(AVERAGES.getNational(),
                Collections.<AveragePrice>emptyList());
        AverageComparison result = AverageComparisonBuilder.build(FuelType.GASOLINE,
                detail("서울특별시 강남구", gasoline(1671)), 0, null, nationalOnly, 40);
        assertEquals(1, result.getLines().size());
    }

    @Test
    public void detailFailed_usesListedPriceAndFallbackAddress() {
        AverageComparison result = AverageComparisonBuilder.build(FuelType.GASOLINE, null, 1700,
                "서울특별시 서초구 서초대로 1", AVERAGES, 40);
        assertEquals(AverageComparison.Status.READY, result.getStatus());
        assertEquals(1700, result.getStationPrice());
        assertEquals(2, result.getLines().size());

        AverageComparison noPrice = AverageComparisonBuilder.build(FuelType.GASOLINE, null, 0, null, AVERAGES, 40);
        assertEquals(AverageComparison.Status.NO_PRICE, noPrice.getStatus());
    }

    @Test
    public void noAverageForFuel() {
        AverageComparison result = AverageComparisonBuilder.build(FuelType.LPG, null, 1004, null, AVERAGES, 40);
        assertEquals(AverageComparison.Status.NO_AVERAGE, result.getStatus());
        assertTrue(result.getLines().isEmpty());
    }

    @Test
    public void format_userExample() {
        AverageComparison result = AverageComparisonBuilder.build(FuelType.GASOLINE,
                detail("서울특별시 강남구", gasoline(1671)), 0, null, AVERAGES, 40);
        AverageComparison.Line national = result.getLines().get(0);
        assertEquals("전국 평균 1,712원보다 리터당 41원 저렴해요", FormatUtil.formatAverageComparison(national));
        assertEquals("40L 기준 약 1,640원 저렴해요", FormatUtil.formatAverageForFuelAmount(national));
        assertEquals("서울 평균 1,768원보다 리터당 97원 저렴해요",
                FormatUtil.formatAverageComparison(result.getLines().get(1)));
    }

    @Test
    public void format_pricierAndEqual() {
        AverageComparison.Line pricier = new AverageComparison.Line("서울", 1768, 12, 40);
        assertEquals("서울 평균 1,768원보다 리터당 12원 비싸요", FormatUtil.formatAverageComparison(pricier));
        assertEquals("40L 기준 약 480원 비싸요", FormatUtil.formatAverageForFuelAmount(pricier));

        AverageComparison.Line equal = new AverageComparison.Line("전국", 1712, 0, 40);
        assertEquals("전국 평균 1,712원 · 평균과 같아요", FormatUtil.formatAverageComparison(equal));
        assertNull(FormatUtil.formatAverageForFuelAmount(equal));
    }
}
