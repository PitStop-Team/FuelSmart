package com.example.findcheapgas.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSetting;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.PlaceCompareCard;
import com.example.findcheapgas.model.PlaceCompareCard.Slot;
import com.example.findcheapgas.model.PlaceCompareCard.Status;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavingLevel;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.util.PlaceCompareBuilder;
import com.example.findcheapgas.util.PlaceCompareBuilder.Source;
import com.example.findcheapgas.util.StationListBuilder;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 위젯에 보여줄 내용: 정상, 등록 안 함, 주유소 없음, 가짜 데이터 범위 밖, 한도 도달, 이전 결과 + 갱신 실패, 계산 기준 문구.
 * 실제 오피넷이나 카카오를 부르지 않고 만든 주유소 목록으로 계산한다.
 */
public class WidgetContentBuilderTest {

    private static final long PLACE_SAVED_AT = 1_000L;
    private static final long FETCHED_AT = 5_000L;
    private static final SavedPlace HOME = new SavedPlace(SavedPlace.Kind.HOME, "선릉역", "주소",
            37.5045, 127.0490, PLACE_SAVED_AT);

    private static GasStation station(String id, String brand, int price, double distance) {
        return new GasStation(id, brand, id, price, distance, 0, 0, 0, 0);
    }

    // 연비 12km/L, 40L: h1 66,057.75(기준), h2 64,466.67 → 이득 1위 h2, 약 1,591원 이득
    private static final List<GasStation> STATIONS = Arrays.asList(
            station("h1", "SKE", 1650, 300),
            station("h2", "GSC", 1600, 2500));

    private static WidgetContent build(Source source, CarInfo carInfo, WidgetContent previous) {
        return WidgetContentBuilder.build(SavedPlace.Kind.HOME, HOME, FuelType.GASOLINE, source, SearchRadius.KM_3,
                carInfo, BrandSettings.EMPTY, BrandFilter.ALL, previous);
    }

    private static WidgetContent ready() {
        return build(Source.loaded(Slot.HOME, STATIONS, FETCHED_AT), CarInfo.DEFAULT, null);
    }

    @Test
    public void ready_showsBestStationPriceSavingDistanceAndBasis() {
        WidgetContent content = ready();

        assertEquals(WidgetContent.State.READY, content.getState());
        assertEquals(SavedPlace.Kind.HOME, content.getKind());
        assertEquals("h2", content.getStationId());
        assertEquals("h2", content.getStationName());
        assertEquals("GS칼텍스", content.getBrandName());
        assertEquals(1600, content.getPrice());
        assertEquals("약 1,591원 이득", content.getSavingText());
        assertEquals(SavingLevel.GAIN, content.getSavingLevel());
        assertEquals(2500, content.getDistanceMeters(), 0.001);
        assertEquals("12km/L · 40L (기본값)", content.getBasis());
        assertEquals(FETCHED_AT, content.getFetchedAt());
        assertFalse(content.isUpdateFailed());
        assertEquals(PLACE_SAVED_AT, content.getPlaceSavedAt());
        assertEquals(FuelType.GASOLINE.getCode(), content.getFuelCode());
    }

    @Test
    public void ready_sameBestAsPlaceCompareCard() {
        // 할인과 선호를 넣어도 비교 카드와 위젯이 같은 계산(PlaceCompareBuilder)을 쓰는지
        BrandSettings settings = BrandSettings.EMPTY
                .with("SKE", new BrandSetting(100, null, false, false))
                .with("GSC", new BrandSetting(0, null, true, false));
        Source source = Source.loaded(Slot.HOME, STATIONS, FETCHED_AT);
        PlaceCompareCard card = PlaceCompareBuilder.build(Collections.singletonList(source), SearchRadius.KM_3,
                CarInfo.DEFAULT, settings, BrandFilter.ALL).get(0);

        WidgetContent content = WidgetContentBuilder.build(SavedPlace.Kind.HOME, HOME, FuelType.GASOLINE, source,
                SearchRadius.KM_3, CarInfo.DEFAULT, settings, BrandFilter.ALL, null);

        assertEquals(card.getBest().getStation().getId(), content.getStationId());
    }

    @Test
    public void basis_userEnteredCarInfo() {
        WidgetContent content = build(Source.loaded(Slot.HOME, STATIONS, FETCHED_AT),
                CarInfo.userEntered(110, 50), null);

        assertEquals("11km/L · 50L", content.getBasis());
    }

    @Test
    public void notRegistered_whenPlaceMissing() {
        WidgetContent content = WidgetContentBuilder.build(SavedPlace.Kind.WORK, null, FuelType.GASOLINE, null,
                SearchRadius.KM_3, CarInfo.DEFAULT, BrandSettings.EMPTY, BrandFilter.ALL, null);

        assertEquals(WidgetContent.State.NOT_REGISTERED, content.getState());
        assertEquals(SavedPlace.Kind.WORK, content.getKind());
    }

    @Test
    public void empty_whenNoStationInRadius() {
        WidgetContent content = build(Source.loaded(Slot.HOME, Collections.singletonList(
                station("far", "SKE", 1600, 4500)), FETCHED_AT), CarInfo.DEFAULT, null);

        assertEquals(WidgetContent.State.EMPTY, content.getState());
        assertEquals(StationListBuilder.EmptyReason.RADIUS, content.getEmptyReason());
        assertEquals(3000, content.getRadiusMeters());
    }

    @Test
    public void empty_whenAllHidden() {
        BrandSettings hidden = BrandSettings.EMPTY
                .with("SKE", new BrandSetting(0, null, false, true))
                .with("GSC", new BrandSetting(0, null, false, true));
        WidgetContent content = WidgetContentBuilder.build(SavedPlace.Kind.HOME, HOME, FuelType.GASOLINE,
                Source.loaded(Slot.HOME, STATIONS, FETCHED_AT), SearchRadius.KM_3, CarInfo.DEFAULT, hidden,
                BrandFilter.ALL, null);

        assertEquals(WidgetContent.State.EMPTY, content.getState());
        assertEquals(StationListBuilder.EmptyReason.HIDDEN_BRANDS, content.getEmptyReason());
    }

    @Test
    public void outsideMockArea() {
        WidgetContent content = build(Source.of(Slot.HOME, Status.OUTSIDE_MOCK_AREA), CarInfo.DEFAULT, null);

        assertEquals(WidgetContent.State.OUTSIDE_MOCK_AREA, content.getState());
    }

    @Test
    public void dailyLimit_withoutPrevious_showsError() {
        WidgetContent content = build(Source.error(Slot.HOME, StationError.DAILY_LIMIT), CarInfo.DEFAULT, null);

        assertEquals(WidgetContent.State.ERROR, content.getState());
        assertEquals(StationError.DAILY_LIMIT, content.getError());
    }

    @Test
    public void networkError_keepsPreviousResultAndMarksFailed() {
        WidgetContent previous = ready();

        WidgetContent content = build(Source.error(Slot.HOME, StationError.NETWORK), CarInfo.DEFAULT, previous);

        assertEquals(WidgetContent.State.READY, content.getState());
        assertEquals("h2", content.getStationId());
        assertEquals(FETCHED_AT, content.getFetchedAt());
        assertTrue(content.isUpdateFailed());
        assertEquals(StationError.NETWORK, content.getError());
        // 저장해 둔 이전 결과는 그대로다.
        assertFalse(previous.isUpdateFailed());
        assertNull(previous.getError());
    }

    @Test
    public void error_ignoresPreviousOfOtherFuelOrReRegisteredPlace() {
        WidgetContent previous = ready();
        Source failed = Source.error(Slot.HOME, StationError.DAILY_LIMIT);

        WidgetContent otherFuel = WidgetContentBuilder.build(SavedPlace.Kind.HOME, HOME, FuelType.DIESEL, failed,
                SearchRadius.KM_3, CarInfo.DEFAULT, BrandSettings.EMPTY, BrandFilter.ALL, previous);
        SavedPlace moved = new SavedPlace(SavedPlace.Kind.HOME, "양재역", null, 37.4844, 127.0343, 2_000L);
        WidgetContent otherPlace = WidgetContentBuilder.build(SavedPlace.Kind.HOME, moved, FuelType.GASOLINE, failed,
                SearchRadius.KM_3, CarInfo.DEFAULT, BrandSettings.EMPTY, BrandFilter.ALL, previous);

        assertEquals(WidgetContent.State.ERROR, otherFuel.getState());
        assertEquals(WidgetContent.State.ERROR, otherPlace.getState());
    }

    @Test
    public void noSourceYet_showsPreviousOrLoading() {
        WidgetContent previous = ready();

        assertSame(previous, build(null, CarInfo.DEFAULT, previous));
        assertEquals(WidgetContent.State.LOADING, build(null, CarInfo.DEFAULT, null).getState());
    }
}
