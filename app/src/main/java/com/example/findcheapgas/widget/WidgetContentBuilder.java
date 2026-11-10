package com.example.findcheapgas.widget;

import androidx.annotation.Nullable;

import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.PlaceCompareCard;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.util.PlaceCompareBuilder;

import java.util.Collections;

/**
 * 위젯에 보여줄 내용을 만든다. API는 부르지 않고, 안드로이드에 의존하지 않는다.
 *
 * 이득 1위는 집·회사 기준 비교 카드와 똑같이 PlaceCompareBuilder로 고른다:
 *   그 위치의 5km 안 목록 → 반경 → 숨긴 브랜드 → 브랜드 빠른 필터 → 예상 이득 계산(내 차 정보, 브랜드 할인) → 이득 1위
 * 그래서 같은 위치·설정이면 비교 카드와 위젯의 1위가 같다.
 *
 * 가져오지 못했을 때(하루 한도, 네트워크, 오피넷)는 같은 위치·유종의 이전 결과가 있으면 그 결과를 남기고 "갱신 실패"만 붙인다.
 */
public final class WidgetContentBuilder {

    private WidgetContentBuilder() {
    }

    /**
     * @param place    위젯이 고른 위치. 등록하지 않았으면 null
     * @param source   그 위치의 주변 조회 결과 (WidgetStationLoader). 아직 없으면 null
     * @param previous 같은 종류(집 / 회사)로 마지막에 그린 READY 결과. 없으면 null
     */
    public static WidgetContent build(SavedPlace.Kind kind, @Nullable SavedPlace place, FuelType fuelType,
                                      @Nullable PlaceCompareBuilder.Source source, SearchRadius radius,
                                      CarInfo carInfo, BrandSettings brandSettings, BrandFilter brandFilter,
                                      @Nullable WidgetContent previous) {
        if (place == null) {
            return new WidgetContent(kind, WidgetContent.State.NOT_REGISTERED);
        }
        WidgetContent usablePrevious = previous != null
                && previous.getState() == WidgetContent.State.READY
                && previous.isFor(place.getSavedAt(), fuelType.getCode()) ? previous : null;
        if (source == null) {
            return usablePrevious != null ? usablePrevious : base(kind, WidgetContent.State.LOADING, place, fuelType);
        }

        PlaceCompareCard card = PlaceCompareBuilder.build(Collections.singletonList(source), radius, carInfo,
                brandSettings, brandFilter).get(0);
        switch (card.getStatus()) {
            case READY:
                return ready(kind, place, fuelType, card, carInfo);
            case EMPTY: {
                WidgetContent empty = base(kind, WidgetContent.State.EMPTY, place, fuelType);
                empty.emptyReason = card.getEmptyReason();
                empty.radiusMeters = radius.getMeters();
                return empty;
            }
            case OUTSIDE_MOCK_AREA:
                return base(kind, WidgetContent.State.OUTSIDE_MOCK_AREA, place, fuelType);
            case ERROR: {
                StationError error = card.getError() != null ? card.getError() : StationError.OPINET;
                if (usablePrevious != null) {
                    return usablePrevious.withUpdateFailed(error);
                }
                WidgetContent failed = base(kind, WidgetContent.State.ERROR, place, fuelType);
                failed.error = error;
                return failed;
            }
            default:
                return usablePrevious != null ? usablePrevious : base(kind, WidgetContent.State.LOADING, place, fuelType);
        }
    }

    /** 위치·유종을 모를 때(등록 안 함 등)가 아닌, 그 위치·유종의 결과 */
    private static WidgetContent base(SavedPlace.Kind kind, WidgetContent.State state, SavedPlace place,
                                      FuelType fuelType) {
        WidgetContent content = new WidgetContent(kind, state);
        content.placeSavedAt = place.getSavedAt();
        content.fuelCode = fuelType.getCode();
        return content;
    }

    private static WidgetContent ready(SavedPlace.Kind kind, SavedPlace place, FuelType fuelType,
                                       PlaceCompareCard card, CarInfo carInfo) {
        StationSaving best = card.getBest();
        WidgetContent content = base(kind, WidgetContent.State.READY, place, fuelType);
        content.stationId = best.getStation().getId();
        content.stationName = best.getStation().getName();
        content.brandName = best.getStation().getBrandName();
        content.price = best.getStation().getPrice();
        content.savingText = FormatUtil.formatSaving(best);
        content.savingLevel = best.getLevel();
        content.distanceMeters = best.getStraightDistance();
        content.basis = FormatUtil.formatShortCalculationBasis(carInfo);
        content.fetchedAt = card.getFetchedAt();
        return content;
    }
}
