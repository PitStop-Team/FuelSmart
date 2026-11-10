package com.example.findcheapgas.widget;

import androidx.annotation.Nullable;

import com.example.findcheapgas.data.mock.MockOpinetDataSource;
import com.example.findcheapgas.data.repository.StationErrors;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.PlaceCompareCard;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;
import com.example.findcheapgas.util.PlaceCompareBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 위젯들이 고른 위치(집 / 회사)의 주변 목록을 가져온다. 안드로이드에 의존하지 않는다.
 * - 같은 종류의 위젯이 여러 개여도 종류마다 한 번만 가져오고, 집과 회사가 같은 좌표면 한 번만 가져온다.
 * - 등록하지 않은 위치는 가져오지 않는다 (WidgetContentBuilder가 "등록하세요"를 보여준다).
 * - 가짜 데이터 모드에서 강남역 주변 5km 밖이면 가져오지 않고 OUTSIDE_MOCK_AREA로 둔다 (집·회사 기준 비교와 같다).
 * 실제로 어디서 가져올지(캐시 → 가짜 데이터 또는 오피넷, 하루 한도)는 StationSource가 정한다.
 */
public final class WidgetStationLoader {

    /** 그 위치·유종의 5km 안 목록을 가져온다. maxCacheAgeMs 안의 캐시가 있으면 캐시를 쓴다. */
    public interface StationSource {
        Fetched load(double latitude, double longitude, FuelType fuelType, long maxCacheAgeMs) throws Exception;
    }

    public static final class Fetched {
        final List<GasStation> stations;
        final long fetchedAt;

        public Fetched(List<GasStation> stations, long fetchedAt) {
            this.stations = stations;
            this.fetchedAt = fetchedAt;
        }
    }

    private WidgetStationLoader() {
    }

    /**
     * @param kinds 위젯들이 고른 종류 (겹쳐도 된다)
     * @return 종류별 조회 결과. 등록하지 않은 종류는 들어 있지 않다.
     */
    public static Map<SavedPlace.Kind, PlaceCompareBuilder.Source> load(
            Collection<SavedPlace.Kind> kinds, SavedPlaces places, FuelType fuelType, long maxCacheAgeMs,
            boolean mockMode, StationSource source) {
        Map<SavedPlace.Kind, PlaceCompareBuilder.Source> result = new EnumMap<>(SavedPlace.Kind.class);
        // 이미 가져온 위치와 그 결과. 같은 좌표면 다시 가져오지 않는다.
        List<SavedPlace> loadedPlaces = new ArrayList<>(2);
        List<Outcome> outcomes = new ArrayList<>(2);
        for (SavedPlace.Kind kind : new LinkedHashSet<>(kinds)) {
            SavedPlace place = places.get(kind);
            if (place == null) {
                continue;
            }
            PlaceCompareCard.Slot slot = kind == SavedPlace.Kind.HOME
                    ? PlaceCompareCard.Slot.HOME : PlaceCompareCard.Slot.WORK;
            if (mockMode && MockOpinetDataSource.isOutsideMockArea(place.getLatitude(), place.getLongitude())) {
                result.put(kind, PlaceCompareBuilder.Source.of(slot, PlaceCompareCard.Status.OUTSIDE_MOCK_AREA));
                continue;
            }
            Outcome outcome = null;
            for (int i = 0; i < loadedPlaces.size(); i++) {
                if (loadedPlaces.get(i).isSamePosition(place)) {
                    outcome = outcomes.get(i);
                    break;
                }
            }
            if (outcome == null) {
                outcome = fetch(source, place, fuelType, maxCacheAgeMs);
                loadedPlaces.add(place);
                outcomes.add(outcome);
            }
            result.put(kind, outcome.toSource(slot));
        }
        return result;
    }

    private static Outcome fetch(StationSource source, SavedPlace place, FuelType fuelType, long maxCacheAgeMs) {
        try {
            return new Outcome(source.load(place.getLatitude(), place.getLongitude(), fuelType, maxCacheAgeMs), null);
        } catch (Exception e) {
            return new Outcome(null, e);
        }
    }

    private static final class Outcome {
        @Nullable
        final Fetched fetched;
        @Nullable
        final Exception failure;

        Outcome(@Nullable Fetched fetched, @Nullable Exception failure) {
            this.fetched = fetched;
            this.failure = failure;
        }

        PlaceCompareBuilder.Source toSource(PlaceCompareCard.Slot slot) {
            if (fetched != null) {
                return PlaceCompareBuilder.Source.loaded(slot, fetched.stations, fetched.fetchedAt);
            }
            return PlaceCompareBuilder.Source.error(slot, StationErrors.from(failure));
        }
    }
}
