package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.data.mock.MockOpinetDataSource;
import com.example.findcheapgas.data.repository.BrandSettingsRepository;
import com.example.findcheapgas.data.repository.CarInfoRepository;
import com.example.findcheapgas.data.repository.SavedPlaceRepository;
import com.example.findcheapgas.data.repository.StationRepository;
import com.example.findcheapgas.model.AroundStations;
import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.PlaceCompareCard;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.util.PlaceCompareBuilder;
import com.example.findcheapgas.util.PlaceCompareCachePolicy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 즐겨찾기 탭 위쪽 "집·회사 기준 비교"의 상태. 카드 세 장(지금 조회 위치 / 집 근처 / 회사 근처)을 만든다.
 * - 지금 조회 위치: 지도 탭(MapViewModel)이 받아 둔 결과를 화면이 넘겨준다. 오피넷을 부르지 않는다.
 * - 집·회사: SavedPlaceRepository의 위치로 StationRepository에서 5km 안 목록을 가져온다.
 *   탭을 열 때는 3시간 안의 캐시를, 당겨서 새로고침할 때는 20분 안의 캐시를 그대로 쓴다 (PlaceCompareCachePolicy).
 *   두 곳이라 한 번에 많아야 2회 호출하고, 실제 호출은 하루 한도(ApiCallCounter)를 거친다.
 * - 반경, 브랜드 빠른 필터, 유종은 지도 탭에서 고른 값을 쓴다. 반경, 필터, 내 차 정보, 브랜드 설정이 바뀌면
 *   API 없이 받아 둔 목록으로 다시 계산하고, 유종이나 등록한 위치가 바뀌었을 때만 새로 가져온다.
 * 가격은 탭이 보일 때만 가져온다. 집·회사 이름, 주소, 좌표는 로그에 남기지 않는다.
 */
public class PlaceCompareViewModel extends AndroidViewModel {

    /** 집·회사 한 곳의 조회 상태 */
    private static final class PlaceState {
        final SavedPlace place;
        final FuelType fuelType;
        PlaceCompareCard.Status status = PlaceCompareCard.Status.LOADING;
        @Nullable
        StationError error;
        @Nullable
        List<GasStation> stations;
        long fetchedAt;
        int requestId;

        PlaceState(SavedPlace place, FuelType fuelType) {
            this.place = place;
            this.fuelType = fuelType;
        }

        boolean matches(SavedPlace other, FuelType otherFuelType) {
            return place.isSamePosition(other) && fuelType == otherFuelType;
        }
    }

    private static final SavedPlace.Kind[] KINDS = {SavedPlace.Kind.HOME, SavedPlace.Kind.WORK};

    private final SavedPlaceRepository savedPlaceRepository;
    private final CarInfoRepository carInfoRepository;
    private final BrandSettingsRepository brandSettingsRepository;
    private final StationRepository stationRepository;

    private final MediatorLiveData<List<PlaceCompareCard>> cards = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);

    private final Map<SavedPlace.Kind, PlaceState> states = new EnumMap<>(SavedPlace.Kind.class);
    private int nextRequestId = 0;
    private int refreshPending = 0;
    private boolean visible = false;

    // 지도 탭 상태 (화면이 넘겨준다)
    @Nullable
    private AroundStations around;
    @Nullable
    private StationError mapError;
    private boolean mapLoading = false;
    private FuelType fuelType = FuelType.GASOLINE;
    private SearchRadius radius = SearchRadius.KM_3;
    private BrandFilter brandFilter = BrandFilter.ALL;

    public PlaceCompareViewModel(@NonNull Application application) {
        super(application);
        savedPlaceRepository = SavedPlaceRepository.getInstance(application);
        carInfoRepository = CarInfoRepository.getInstance(application);
        brandSettingsRepository = BrandSettingsRepository.getInstance(application);
        stationRepository = new StationRepository(application);

        cards.addSource(savedPlaceRepository.getPlaces(), places -> {
            if (visible) {
                load(false);
            }
            rebuild();
        });
        cards.addSource(carInfoRepository.getCarInfo(), carInfo -> rebuild());
        cards.addSource(brandSettingsRepository.getBrandSettings(), settings -> rebuild());
    }

    /** 카드 세 장 (지금 조회 위치, 집, 회사 순서). 등록한 위치를 아직 읽지 못했으면 null */
    public LiveData<List<PlaceCompareCard>> getCards() {
        return cards;
    }

    /** 당겨서 새로고침으로 가져오는 중 */
    public LiveData<Boolean> getRefreshing() {
        return refreshing;
    }

    /** 등록한 집·회사 위치. 읽기 전에는 null */
    public LiveData<SavedPlaces> getPlaces() {
        return savedPlaceRepository.getPlaces();
    }

    /**
     * 지도 탭의 상태가 바뀌었을 때. 유종이 바뀌었으면 집·회사도 새 유종으로 가져온다.
     * 반경, 빠른 필터가 바뀌었으면 받아 둔 목록으로 다시 계산만 한다.
     */
    public void setMapState(@Nullable AroundStations newAround, @Nullable StationError newMapError,
                            boolean newMapLoading, FuelType newFuelType, SearchRadius newRadius,
                            BrandFilter newBrandFilter) {
        around = newAround;
        mapError = newMapError;
        mapLoading = newMapLoading;
        boolean fuelChanged = newFuelType != fuelType;
        fuelType = newFuelType;
        radius = newRadius;
        brandFilter = newBrandFilter;
        if (fuelChanged && visible) {
            load(false);
        }
        rebuild();
    }

    /** 탭이 보이거나 숨겨질 때. 보이면 집·회사 가격을 가져온다 (3시간 안의 캐시가 있으면 캐시). */
    public void setVisible(boolean newVisible) {
        boolean becameVisible = newVisible && !visible;
        visible = newVisible;
        if (becameVisible) {
            load(false);
            rebuild();
        }
    }

    /** 당겨서 새로고침: 20분이 지난 것만 다시 가져온다. */
    public void refresh() {
        load(true);
        rebuild();
    }

    private void load(boolean refresh) {
        SavedPlaces places = savedPlaceRepository.getPlaces().getValue();
        if (places == null) {
            return; // 등록한 위치를 읽으면 다시 불린다.
        }
        long now = System.currentTimeMillis();
        for (SavedPlace.Kind kind : KINDS) {
            SavedPlace place = places.get(kind);
            if (place == null) {
                states.remove(kind);
                continue;
            }
            PlaceState current = states.get(kind);
            if (!refresh && current != null && current.matches(place, fuelType) && !needsReload(current, now)) {
                continue;
            }
            if (BuildConfig.USE_MOCK_DATA
                    && MockOpinetDataSource.isOutsideMockArea(place.getLatitude(), place.getLongitude())) {
                PlaceState outside = new PlaceState(place, fuelType);
                outside.status = PlaceCompareCard.Status.OUTSIDE_MOCK_AREA;
                states.put(kind, outside);
                continue;
            }
            fetch(kind, place, current, refresh);
        }
    }

    /** 탭을 다시 열었을 때 다시 가져올지: 실패했거나, 받아 둔 가격이 3시간을 넘었으면 */
    private static boolean needsReload(PlaceState state, long now) {
        if (state.status == PlaceCompareCard.Status.ERROR) {
            return true;
        }
        return state.status == PlaceCompareCard.Status.READY
                && !PlaceCompareCachePolicy.isUsable(state.fetchedAt, now, false);
    }

    private void fetch(SavedPlace.Kind kind, SavedPlace place, @Nullable PlaceState previous, boolean refresh) {
        PlaceState state = new PlaceState(place, fuelType);
        // 같은 위치·유종을 새로고침하는 중에는 이전 결과를 계속 보여준다.
        if (previous != null && previous.matches(place, fuelType)
                && previous.status == PlaceCompareCard.Status.READY) {
            state.status = PlaceCompareCard.Status.READY;
            state.stations = previous.stations;
            state.fetchedAt = previous.fetchedAt;
        }
        int id = ++nextRequestId;
        state.requestId = id;
        states.put(kind, state);
        if (refresh) {
            refreshPending++;
            refreshing.setValue(true);
        }
        StationQuery query = new StationQuery(place.getLatitude(), place.getLongitude(), fuelType);
        stationRepository.fetchAroundStations(query, PlaceCompareCachePolicy.maxAgeMs(refresh),
                new StationRepository.TimedCallback() {
                    @Override
                    public void onSuccess(List<GasStation> stations, long fetchedAt) {
                        finishRefresh(refresh);
                        if (isLatest(kind, id)) {
                            state.status = PlaceCompareCard.Status.READY;
                            state.error = null;
                            state.stations = stations;
                            state.fetchedAt = fetchedAt;
                            rebuild();
                        }
                    }

                    @Override
                    public void onError(StationError error) {
                        finishRefresh(refresh);
                        if (isLatest(kind, id)) {
                            state.status = PlaceCompareCard.Status.ERROR;
                            state.error = error;
                            state.stations = null;
                            rebuild();
                        }
                    }
                });
    }

    private boolean isLatest(SavedPlace.Kind kind, int requestId) {
        PlaceState state = states.get(kind);
        return state != null && state.requestId == requestId;
    }

    private void finishRefresh(boolean refresh) {
        if (refresh && --refreshPending <= 0) {
            refreshPending = 0;
            refreshing.setValue(false);
        }
    }

    private void rebuild() {
        SavedPlaces places = savedPlaceRepository.getPlaces().getValue();
        if (places == null) {
            return;
        }
        List<PlaceCompareBuilder.Source> sources = new ArrayList<>(3);
        sources.add(currentSource());
        for (SavedPlace.Kind kind : KINDS) {
            sources.add(placeSource(kind, places.get(kind)));
        }
        cards.setValue(PlaceCompareBuilder.build(sources, radius, carInfoRepository.getCurrent(),
                brandSettingsRepository.getCurrent(), brandFilter));
    }

    /** 지금 조회 위치: 지도 탭이 받아 둔 결과 그대로 */
    private PlaceCompareBuilder.Source currentSource() {
        PlaceCompareCard.Slot slot = PlaceCompareCard.Slot.CURRENT;
        if (mapLoading || (around != null && around.getFuelType() != fuelType)) {
            return PlaceCompareBuilder.Source.of(slot, PlaceCompareCard.Status.LOADING);
        }
        if (around == null) {
            return mapError != null
                    ? PlaceCompareBuilder.Source.error(slot, mapError)
                    : PlaceCompareBuilder.Source.of(slot, PlaceCompareCard.Status.WAITING);
        }
        if (around.isFailed()) {
            return PlaceCompareBuilder.Source.error(slot, mapError != null ? mapError : StationError.OPINET);
        }
        if (BuildConfig.USE_MOCK_DATA && around.getAll().isEmpty()
                && MockOpinetDataSource.isOutsideMockArea(around.getOrigin().getLatitude(),
                around.getOrigin().getLongitude())) {
            return PlaceCompareBuilder.Source.of(slot, PlaceCompareCard.Status.OUTSIDE_MOCK_AREA);
        }
        return PlaceCompareBuilder.Source.loaded(slot, around.getAll(), 0);
    }

    private PlaceCompareBuilder.Source placeSource(SavedPlace.Kind kind, @Nullable SavedPlace place) {
        PlaceCompareCard.Slot slot = kind == SavedPlace.Kind.HOME
                ? PlaceCompareCard.Slot.HOME : PlaceCompareCard.Slot.WORK;
        if (place == null) {
            return PlaceCompareBuilder.Source.of(slot, PlaceCompareCard.Status.NOT_REGISTERED);
        }
        PlaceState state = states.get(kind);
        if (state == null || !state.matches(place, fuelType)) {
            // 아직 탭을 열지 않았거나 유종·위치가 바뀌어서 새로 가져와야 한다.
            return PlaceCompareBuilder.Source.of(slot, PlaceCompareCard.Status.LOADING);
        }
        switch (state.status) {
            case READY:
                return PlaceCompareBuilder.Source.loaded(slot, state.stations, state.fetchedAt);
            case ERROR:
                return PlaceCompareBuilder.Source.error(slot,
                        state.error != null ? state.error : StationError.OPINET);
            default:
                return PlaceCompareBuilder.Source.of(slot, state.status);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stationRepository.shutdown();
    }
}
