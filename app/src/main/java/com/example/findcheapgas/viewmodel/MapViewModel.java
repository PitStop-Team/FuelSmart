package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.data.local.StationFilterPreferences;
import com.example.findcheapgas.data.mock.MockOpinetDataSource;
import com.example.findcheapgas.data.repository.BrandSettingsRepository;
import com.example.findcheapgas.data.repository.CarInfoRepository;
import com.example.findcheapgas.model.AroundStations;
import com.example.findcheapgas.data.repository.LocationRepository;
import com.example.findcheapgas.data.repository.RegionNameRepository;
import com.example.findcheapgas.data.repository.StationRepository;
import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.LocationPermissionState;
import com.example.findcheapgas.model.SearchOrigin;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.navigation.RoadDistanceResolver;
import com.example.findcheapgas.pleos.PleosVehicleSession;
import com.example.findcheapgas.vehicle.CarInfoResolver;
import com.example.findcheapgas.vehicle.LowFuelSuggestion;
import com.example.findcheapgas.vehicle.LowFuelTrigger;
import com.example.findcheapgas.pleos.NaviHelperNavigationController;
import java.util.OptionalInt;
import com.example.findcheapgas.navigation.RouteDistanceProvider;
import com.example.findcheapgas.navigation.UserInputRouteDistanceProvider;
import com.example.findcheapgas.model.StationSearchResult;
import com.example.findcheapgas.model.UserLocation;
import com.example.findcheapgas.util.PlaceCompareBuilder;
import com.example.findcheapgas.util.StationListBuilder;

import java.util.Collections;
import java.util.List;

/**
 * 지도 탭의 화면 상태를 가진다.
 * - 위치: 현재 좌표(지도 위 파란 점), 권한 상태, 위치 조회 중 여부, 기기 위치 켜짐 여부
 * - 조회 위치: 현재 위치, 검색한 장소·주유소, 지도 중심 (SearchOriginState). 주변 조회, 거리, 이동 비용, 기준 주유소,
 *   예상 이득은 모두 조회 위치 기준이다. 장소·지도 중심 기준일 때 내 위치를 새로 받아도 조회 위치는 그대로다.
 * - 이 지역에서 다시 검색: 지도 이동이 끝날 때마다 지도 중심을 받아서, 조회 위치에서 고른 반경(최대 3km) 이상 떨어져 있으면 버튼을 보여준다.
 *   지도를 움직이는 것만으로는 조회하지 않고, 버튼을 눌렀을 때만 지도 중심을 조회 위치로 조회한다.
 * - 주유소: 주변 주유소 목록, 조회 중 여부, 오류, 선택한 주유소
 * - 선택: 유종, 반경, 정렬, 브랜드 빠른 필터 (마지막 선택을 StationFilterPreferences에 저장)
 * - 내 차 정보: CarInfoRepository의 LiveData를 그대로 내보낸다 (계산 기준 문구)
 * - 내 브랜드 설정: BrandSettingsRepository의 LiveData를 그대로 내보낸다 (빠른 필터 메뉴)
 *
 * API 호출은 유종이 바뀌거나 조회 위치가 바뀌었을 때만 한다. 오피넷에서 받은 5km 안 전체 목록(fetchedStations)을
 * 들고 있다가, 반경, 정렬, 브랜드 빠른 필터, 내 차 정보, 내 브랜드 설정이 바뀌면
 * StationListBuilder로 화면 목록(stations)만 다시 만든다.
 * 화면 목록의 각 항목에는 예상 이득 계산 결과(StationSaving)가 들어 있다.
 *
 * MainActivity 범위로 만들어서 즐겨찾기 탭도 같은 ViewModel을 본다. 즐겨찾기 탭은 aroundStations(조회 위치, 유종,
 * 받아 둔 목록, 기준 주유소)로 같은 기준의 예상 이득을 계산하고, "지도에서 보기"는 showStationOnMap을 부른다.
 * 집·회사 기준 비교 카드를 누르면 showSavedPlaceOnMap(집·회사 위치) 또는 selectBestStation(지금 조회 위치)을 부른다.
 */
public class MapViewModel extends AndroidViewModel {

    private final LocationRepository repository;
    private final StationRepository stationRepository;
    private final StationFilterPreferences filterPreferences;
    private final CarInfoRepository carInfoRepository;
    // 내 차 정보가 바뀌면 받아 둔 목록으로 다시 계산한다. ViewModel이 없어질 때 해제한다.
    private final Observer<CarInfo> carInfoObserver = carInfo -> updateDisplayedStations();
    // 차량에서 연비를 새로 받으면 목록을 다시 계산한다.
    private final Observer<Long> vehicleObserver = version -> {
        onVehicleFuelChanged();
        updateDisplayedStations();
    };
    // 연료 부족 자동 제안: 한 번 내면 연료가 회복될 때까지 다시 내지 않는다 (LowFuelTrigger).
    private final LowFuelTrigger lowFuelTrigger = new LowFuelTrigger();
    private boolean lowFuelPending = false;
    private int lowFuelPercent;
    private final MutableLiveData<LowFuelSuggestion> lowFuelSuggestion = new MutableLiveData<>();
    private final PleosVehicleSession vehicleSession;
    // 내 브랜드 설정(할인, 선호, 숨기기)이 바뀌어도 같은 방식으로 다시 계산한다.
    private final BrandSettingsRepository brandSettingsRepository;
    private final Observer<BrandSettings> brandSettingsObserver = settings -> updateDisplayedStations();

    // 내 위치 (파란 점). 조회 위치와 다를 수 있다.
    private final MutableLiveData<UserLocation> location = new MutableLiveData<>();
    // 조회 위치. 바뀌면 지도가 그 위치로 옮겨 간다. 아직 없으면 null
    private final MutableLiveData<SearchOrigin> origin = new MutableLiveData<>();
    // 가짜 데이터 모드에서 강남역에서 먼 곳을 골랐을 때 한 번 보여줄 안내. 보여주면 false로 되돌린다.
    private final MutableLiveData<Boolean> mockAreaNotice = new MutableLiveData<>(false);
    // 기기 위치가 꺼져 있어 마지막으로 확인한 위치로 조회했을 때 한 번 보여줄 안내. 보여주면 false로 되돌린다.
    // (기본 위치 서울시청으로 조회했을 때는 이득 1위 카드 자리에 안내가 나온다.)
    private final MutableLiveData<Boolean> lastKnownLocationNotice = new MutableLiveData<>(false);
    // 주유소 이름 검색에서 고른 주유소가 지금 목록에 없을 때(그 유종을 안 팔거나 필터에 걸림) 한 번 보여줄 상호.
    // 보여주면 null로 되돌린다.
    private final MutableLiveData<String> searchedStationMissing = new MutableLiveData<>();
    // "이 지역에서 다시 검색" 버튼을 보여줄지
    private final MutableLiveData<Boolean> researchAvailable = new MutableLiveData<>(false);
    private final MutableLiveData<LocationPermissionState> permissionState =
            new MutableLiveData<>(LocationPermissionState.UNKNOWN);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> locationEnabled = new MutableLiveData<>(true);

    private final MutableLiveData<FuelType> fuelType = new MutableLiveData<>();
    private final MutableLiveData<SearchRadius> radius = new MutableLiveData<>();
    private final MutableLiveData<SortOrder> sortOrder = new MutableLiveData<>();
    // 저장해 둔 그대로의 빠른 필터. 화면에 적용할 때는 BrandFilter.effective()로 정리한다.
    private final MutableLiveData<BrandFilter> brandFilter = new MutableLiveData<>();

    // 화면에 보여줄 목록 (반경 필터, 예상 이득 계산, 정렬 적용). 아직 조회하지 않았으면 null
    private final MutableLiveData<List<StationSaving>> stations = new MutableLiveData<>();
    // 화면 목록이 비었을 때 이유 (반경, 숨긴 브랜드, 빠른 필터). stations보다 먼저 바꾼다.
    private final MutableLiveData<StationListBuilder.EmptyReason> emptyReason =
            new MutableLiveData<>(StationListBuilder.EmptyReason.NONE);
    private final MutableLiveData<Boolean> stationsLoading = new MutableLiveData<>(false);
    // 오류가 없으면 null
    private final MutableLiveData<StationError> stationsError = new MutableLiveData<>();
    // 선택한 주유소 ID. 없으면 null
    private final MutableLiveData<String> selectedStationId = new MutableLiveData<>();
    // 마지막 주변 조회 결과 (조회 위치, 유종, 5km 안 전체 목록, 화면 목록). 즐겨찾기 탭이 같은 기준으로 계산할 때 쓴다.
    // 조회가 끝날 때와 화면 목록을 다시 만들 때 바뀐다. 아직 조회하지 않았으면 null
    private final MutableLiveData<AroundStations> aroundStations = new MutableLiveData<>();

    // 지도 탭을 처음 열 때 한 번만 권한을 묻기 위한 표시
    private boolean permissionRequested = false;

    // 조회 위치를 정하는 규칙. 다시 시도하거나 유종을 바꿀 때 지금 조회 위치로 조회한다. 지도를 움직여도 바뀌지 않는다.
    private final SearchOriginState originState = new SearchOriginState();
    // 위치 조회가 끝나면 장소 기준이어도 내 위치 기준으로 돌아갈지 (내 위치 버튼, 칩 X)
    private boolean returnToMyLocationPending = false;
    // 주유소 이름 검색이나 즐겨찾기 "지도에서 보기"로 고른 주유소. 그 위치로 조회가 끝나면 선택하고 비운다.
    private String pendingSelectId;
    private String pendingSelectName;
    // 집·회사 기준 비교 카드를 눌렀을 때. 그 위치로 조회가 끝나면 이득 1위 주유소를 선택하고 false로 돌린다.
    private boolean pendingSelectBest = false;
    // 마지막으로 받은 5km 안 전체 목록 (정렬 전). 조회 전이거나 실패했으면 null
    private List<GasStation> fetchedStations;
    // fetchedStations를 조회한 위치와 유종
    private SearchOrigin fetchedOrigin;
    private FuelType fetchedFuelType;
    // 실제 도로 거리를 주는 곳. 지금은 사용자 입력 구현(비어 있음)이라 항상 구간별 계수로 대체된다.
    private final RouteDistanceProvider routeDistanceProvider = new UserInputRouteDistanceProvider();
    // 조회가 겹치면 마지막 요청의 결과만 반영한다.
    private int stationRequestId = 0;

    // 마지막으로 이동이 끝난 지도 중심 (WGS84). 지도가 아직 안 움직였으면 hasMapCenter가 false
    private boolean hasMapCenter = false;
    private double mapCenterLatitude;
    private double mapCenterLongitude;
    // 지도 중심 조회 위치의 동 이름 (카카오 로컬 API). 실패하면 "지도 중심"으로 보여준다.
    private final RegionNameRepository regionNameRepository = new RegionNameRepository();

    public MapViewModel(@NonNull Application application) {
        super(application);
        repository = new LocationRepository(application);
        stationRepository = new StationRepository(application);
        filterPreferences = new StationFilterPreferences(application);
        fuelType.setValue(filterPreferences.getFuelType());
        radius.setValue(filterPreferences.getRadius());
        sortOrder.setValue(filterPreferences.getSortOrder());
        brandFilter.setValue(filterPreferences.getBrandFilter());
        carInfoRepository = CarInfoRepository.getInstance(application);
        carInfoRepository.getCarInfo().observeForever(carInfoObserver);
        vehicleSession = PleosVehicleSession.getInstance(application);
        vehicleSession.getFuelDataVersion().observeForever(vehicleObserver);
        brandSettingsRepository = BrandSettingsRepository.getInstance(application);
        brandSettingsRepository.getBrandSettings().observeForever(brandSettingsObserver);
    }

    /** 지금 보여줄 연료 부족 제안. 없으면 null */
    public LiveData<LowFuelSuggestion> getLowFuelSuggestion() {
        return lowFuelSuggestion;
    }

    /** 운전자가 "나중에"를 눌렀거나 제안을 처리했다. 같은 연료 하락 구간에서는 다시 제안하지 않는다. */
    public void dismissLowFuelSuggestion() {
        lowFuelPending = false;
        lowFuelSuggestion.setValue(null);
    }

    private void onVehicleFuelChanged() {
        OptionalInt percent = vehicleSession.getDataSource().getFuelPercent();
        if (percent.isPresent()) {
            lowFuelPercent = percent.getAsInt();
        }
        if (lowFuelTrigger.onFuelPercent(percent)) {
            lowFuelPending = true;
        }
        emitLowFuelSuggestionIfReady();
    }

    /** 제안할 때가 됐고 주변 주유소 목록이 준비됐으면 제안을 내보낸다. 목록이 아직 없으면 기다린다. */
    private void emitLowFuelSuggestionIfReady() {
        if (!lowFuelPending) {
            return;
        }
        List<StationSaving> list = stations.getValue();
        if (list == null || list.isEmpty()) {
            return;
        }
        boolean hasRoute = NaviHelperNavigationController.getInstance(getApplication())
                .getCurrentRouteInfo().isPresent();
        java.util.Optional<LowFuelSuggestion> suggestion = LowFuelSuggestion.create(lowFuelPercent, list, hasRoute);
        if (suggestion.isPresent()) {
            lowFuelPending = false;
            lowFuelSuggestion.setValue(suggestion.get());
        }
    }

    /** 계산에 실제로 쓰는 내 차 정보: 차량 연비가 있으면 그 값, 없으면 사용자 입력값 */
    public CarInfo getEffectiveCarInfo() {
        return CarInfoResolver.resolve(vehicleSession.getDataSource(), carInfoRepository.getCurrent());
    }

    /** 계산이 차량 연비를 쓰고 있는지 (계산 기준 문구에 밝힌다). */
    public boolean isUsingVehicleEfficiency() {
        return !getEffectiveCarInfo().equals(carInfoRepository.getCurrent());
    }

    /** 차량에서 연료 값을 새로 받을 때마다 바뀐다. */
    public LiveData<Long> getVehicleDataVersion() {
        return vehicleSession.getFuelDataVersion();
    }

    public LiveData<UserLocation> getLocation() {
        return location;
    }

    public LiveData<SearchOrigin> getOrigin() {
        return origin;
    }

    public LiveData<Boolean> getMockAreaNotice() {
        return mockAreaNotice;
    }

    public void onMockAreaNoticeShown() {
        mockAreaNotice.setValue(false);
    }

    public LiveData<Boolean> getLastKnownLocationNotice() {
        return lastKnownLocationNotice;
    }

    public void onLastKnownLocationNoticeShown() {
        lastKnownLocationNotice.setValue(false);
    }

    public LiveData<String> getSearchedStationMissing() {
        return searchedStationMissing;
    }

    public void onSearchedStationMissingShown() {
        searchedStationMissing.setValue(null);
    }

    /** 가짜 데이터 모드이고 조회 위치가 가짜 데이터가 있는 강남역 주변 밖인지 */
    public boolean isOutsideMockArea() {
        SearchOrigin current = origin.getValue();
        return BuildConfig.USE_MOCK_DATA && current != null
                && MockOpinetDataSource.isOutsideMockArea(current.getLatitude(), current.getLongitude());
    }

    /** "이 지역에서 다시 검색" 버튼을 보여줄지 */
    public LiveData<Boolean> getResearchAvailable() {
        return researchAvailable;
    }

    /**
     * 지도 이동이 끝났을 때 (손으로 움직였거나 코드로 옮겼거나). 조회하지 않고 버튼을 보여줄지만 정한다.
     * 지도 중심이 조회 위치에서 고른 반경(최대 3km) 이상이면 보여주고, 조회 위치 근처로 돌아오면 숨긴다.
     */
    public void onMapMoved(double latitude, double longitude) {
        hasMapCenter = true;
        mapCenterLatitude = latitude;
        mapCenterLongitude = longitude;
        updateResearchAvailable();
    }

    private void updateResearchAvailable() {
        SearchRadius currentRadius = radius.getValue();
        boolean show = hasMapCenter && currentRadius != null
                && originState.shouldOfferResearch(mapCenterLatitude, mapCenterLongitude, currentRadius);
        if (!Boolean.valueOf(show).equals(researchAvailable.getValue())) {
            researchAvailable.setValue(show);
        }
    }

    /**
     * "이 지역에서 다시 검색" 버튼. 마지막 지도 중심을 조회 위치로 바꾸고 주변을 조회한다 (같은 위치·유종이면 20분 캐시).
     * 조회 위치 이름(동 이름)은 따로 물어보고, 오기 전이나 실패하면 "지도 중심"으로 보여준다. 이름 때문에 조회를 기다리지 않는다.
     */
    public void researchMapCenter() {
        if (!hasMapCenter) {
            return;
        }
        pendingSelectId = null;
        pendingSelectName = null;
        pendingSelectBest = false;
        SearchOrigin requested = originState.selectMapCenter(mapCenterLatitude, mapCenterLongitude);
        setOrigin(requested);
        regionNameRepository.find(requested.getLatitude(), requested.getLongitude(), regionName -> {
            if (regionName == null) {
                return;
            }
            SearchOrigin named = originState.nameMapCenter(requested, regionName);
            if (named != null) {
                onOriginRenamed(named);
            }
        });
    }

    /** 조회 위치의 이름만 바뀌었을 때. 다시 조회하지 않고 이름만 바꿔서 내보낸다 (즐겨찾기 탭의 조회 위치 이름도). */
    private void onOriginRenamed(SearchOrigin named) {
        origin.setValue(named);
        if (named.isSamePosition(fetchedOrigin)) {
            fetchedOrigin = named;
        }
        AroundStations current = aroundStations.getValue();
        if (current != null && named.isSamePosition(current.getOrigin())) {
            aroundStations.setValue(new AroundStations(named, current.getFuelType(), current.getAll(),
                    current.getDisplayed()));
        }
    }

    public LiveData<LocationPermissionState> getPermissionState() {
        return permissionState;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getLocationEnabled() {
        return locationEnabled;
    }

    public LiveData<List<StationSaving>> getStations() {
        return stations;
    }

    public LiveData<StationListBuilder.EmptyReason> getEmptyReason() {
        return emptyReason;
    }

    public LiveData<Boolean> getStationsLoading() {
        return stationsLoading;
    }

    public LiveData<StationError> getStationsError() {
        return stationsError;
    }

    public LiveData<String> getSelectedStationId() {
        return selectedStationId;
    }

    /** 마지막 주변 조회 결과. 아직 조회하지 않았으면 null */
    public LiveData<AroundStations> getAroundStations() {
        return aroundStations;
    }

    public LiveData<FuelType> getFuelType() {
        return fuelType;
    }

    public LiveData<SearchRadius> getRadius() {
        return radius;
    }

    public LiveData<SortOrder> getSortOrder() {
        return sortOrder;
    }

    /** 항상 값이 있다 (입력하지 않았으면 CarInfo.DEFAULT). */
    public LiveData<CarInfo> getCarInfo() {
        return carInfoRepository.getCarInfo();
    }

    public LiveData<BrandFilter> getBrandFilter() {
        return brandFilter;
    }

    /** 항상 값이 있다. */
    public LiveData<BrandSettings> getBrandSettings() {
        return brandSettingsRepository.getBrandSettings();
    }

    public boolean hasPermission() {
        return repository.hasPermission();
    }

    public boolean isPermissionRequested() {
        return permissionRequested;
    }

    public void markPermissionRequested() {
        permissionRequested = true;
    }

    public void onPermissionGranted() {
        permissionState.setValue(LocationPermissionState.GRANTED);
        refreshLocation(false);
    }

    public void onPermissionDenied(boolean permanently) {
        permissionState.setValue(permanently
                ? LocationPermissionState.PERMANENTLY_DENIED
                : LocationPermissionState.DENIED);
        if (location.getValue() == null) {
            // 권한이 없어도 기본 위치(서울시청) 주변 주유소는 보여준다.
            onMyLocation(repository.getDefaultLocation(), false);
        }
    }

    /**
     * 내 위치 버튼, "OO 기준" 칩의 X. 장소·지도 중심 기준이면 내 위치 기준으로 돌아간다.
     * 권한이 있으면 위치를 새로 받고, 없으면 마지막으로 받은 위치(보통 기본 위치)로 돌아간다.
     */
    public void returnToMyLocation() {
        if (repository.hasPermission()) {
            refreshLocation(true);
            return;
        }
        SearchOrigin changed = originState.returnToLastMyLocation();
        if (changed != null) {
            setOrigin(changed);
        }
    }

    /** 장소 검색 결과를 골랐을 때. 그 장소를 조회 위치로 주변 주유소를 조회한다. */
    public void selectPlace(String name, double latitude, double longitude) {
        pendingSelectId = null;
        pendingSelectName = null;
        pendingSelectBest = false;
        setOrigin(originState.selectPlace(name, latitude, longitude));
    }

    /** 주유소 이름 검색 결과를 골랐을 때. 그 주유소 위치를 조회 위치로 조회하고, 끝나면 그 주유소를 선택한다. */
    public void selectSearchedStation(StationSearchResult station) {
        showStationOnMap(station.getId(), station.getName(), station.getLatitude(), station.getLongitude());
    }

    /**
     * 주유소 이름 검색, 즐겨찾기 탭의 "지도에서 보기". 그 주유소 위치를 조회 위치("OO 기준")로 주변을 조회하고,
     * 끝나면 그 주유소를 선택한다. 지금 유종이나 필터 때문에 목록에 없으면 한 번 안내한다.
     */
    public void showStationOnMap(String stationId, String name, double latitude, double longitude) {
        SearchOrigin changed = originState.selectPlace(name, latitude, longitude);
        pendingSelectId = stationId;
        pendingSelectName = name;
        pendingSelectBest = false;
        setOrigin(changed);
    }

    /**
     * 즐겨찾기 탭의 집·회사 기준 비교 카드. 그 위치를 조회 위치("집 기준", "회사 기준")로 주변을 조회하고,
     * 끝나면 이득 1위 주유소를 선택한다. label은 화면에 보이는 이름("집", "회사")이고 장소 이름이나 주소가 아니다.
     */
    public void showSavedPlaceOnMap(String label, double latitude, double longitude) {
        SearchOrigin changed = originState.selectPlace(label, latitude, longitude);
        pendingSelectId = null;
        pendingSelectName = null;
        pendingSelectBest = true;
        setOrigin(changed);
    }

    /** 즐겨찾기 탭의 "지금 조회 위치" 카드. 다시 조회하지 않고 지금 목록의 이득 1위 주유소를 선택한다. */
    public void selectBestStation() {
        StationSaving best = PlaceCompareBuilder.findBest(stations.getValue());
        if (best != null) {
            selectedStationId.setValue(best.getStation().getId());
        }
    }

    /**
     * 위치를 다시 가져온다. 내 위치 기준이면 그 위치로 주변 주유소를 조회하고,
     * 장소 기준이면 파란 점만 옮기고 조회 위치는 그대로 둔다 (returnToMyLocation이면 내 위치 기준으로 돌아간다).
     * 결과는 매번 새 값으로 넣어서 같은 좌표여도 카메라가 다시 이동한다.
     */
    private void refreshLocation(boolean returnToMyLocation) {
        returnToMyLocationPending |= returnToMyLocation;
        if (Boolean.TRUE.equals(loading.getValue())) {
            return; // 진행 중인 위치 조회가 끝날 때 returnToMyLocationPending을 반영한다.
        }
        locationEnabled.setValue(repository.isLocationEnabled());
        loading.setValue(true);
        repository.fetchCurrentLocation(result -> {
            loading.setValue(false);
            boolean returnNow = returnToMyLocationPending;
            returnToMyLocationPending = false;
            onMyLocation(result, returnNow);
        });
    }

    private void onMyLocation(UserLocation result, boolean returnToMyLocation) {
        location.setValue(result);
        SearchOrigin changed = originState.onMyLocation(result, returnToMyLocation);
        if (changed != null) {
            setOrigin(changed);
            // 기기 위치가 꺼져 있어 지금 위치가 아니라 마지막 위치로 조회했다면 그 사실을 알린다.
            if (result.getSource() == UserLocation.Source.LAST_KNOWN
                    && Boolean.FALSE.equals(locationEnabled.getValue())) {
                lastKnownLocationNotice.setValue(true);
            }
        }
    }

    /** 조회 위치를 바꾸고 그 위치로 주변 주유소를 조회한다. 다시 검색 버튼도 새 조회 위치 기준으로 다시 정한다. */
    private void setOrigin(SearchOrigin newOrigin) {
        origin.setValue(newOrigin);
        updateResearchAvailable();
        if (newOrigin.isFixed() && isOutsideMockArea()) {
            mockAreaNotice.setValue(true);
        }
        loadStations(newOrigin);
    }

    /** 조회 중에 이름이 붙었으면(지도 중심의 동 이름) 이름이 붙은 쪽을 쓴다. */
    private SearchOrigin latestOf(SearchOrigin requested) {
        SearchOrigin current = originState.getOrigin();
        return requested.isSamePosition(current) ? current : requested;
    }

    /** 화면으로 돌아올 때 호출. 기기 위치가 꺼져 있다가 켜졌으면 위치를 다시 가져온다. */
    public void checkLocationEnabled() {
        boolean wasDisabled = Boolean.FALSE.equals(locationEnabled.getValue());
        boolean enabled = repository.isLocationEnabled();
        locationEnabled.setValue(enabled);
        if (wasDisabled && enabled && permissionState.getValue() == LocationPermissionState.GRANTED) {
            refreshLocation(false);
        }
    }

    /** 오류 화면의 다시 시도 버튼 */
    public void retryStations() {
        SearchOrigin current = originState.getOrigin();
        if (current != null) {
            loadStations(current);
        }
    }

    /** 유종을 바꾸면 그 유종으로 다시 조회한다 (20분 안에 같은 위치·유종을 조회했으면 캐시 사용). */
    public void setFuelType(FuelType newFuelType) {
        if (newFuelType == fuelType.getValue()) {
            return;
        }
        fuelType.setValue(newFuelType);
        filterPreferences.setFuelType(newFuelType);
        // 위치를 아직 못 받았으면 위치를 받은 뒤 이 유종으로 조회된다.
        SearchOrigin current = originState.getOrigin();
        if (current != null) {
            loadStations(current);
        }
    }

    /** 반경을 바꾸면 API를 부르지 않고 받아 둔 목록에서 다시 고른다. */
    public void setRadius(SearchRadius newRadius) {
        if (newRadius == radius.getValue()) {
            return;
        }
        radius.setValue(newRadius);
        filterPreferences.setRadius(newRadius);
        updateDisplayedStations();
        // 다시 검색 버튼 기준도 반경을 따르므로 다시 정한다.
        updateResearchAvailable();
    }

    /** 정렬을 바꾸면 API를 부르지 않고 받아 둔 목록을 다시 정렬한다. */
    public void setSortOrder(SortOrder newSortOrder) {
        if (newSortOrder == sortOrder.getValue()) {
            return;
        }
        sortOrder.setValue(newSortOrder);
        filterPreferences.setSortOrder(newSortOrder);
        updateDisplayedStations();
    }

    /** 빠른 필터를 바꾸면 API를 부르지 않고 받아 둔 목록에서 다시 고르고 다시 계산한다. */
    public void setBrandFilter(BrandFilter newFilter) {
        if (newFilter.equals(brandFilter.getValue())) {
            return;
        }
        brandFilter.setValue(newFilter);
        filterPreferences.setBrandFilter(newFilter);
        updateDisplayedStations();
    }

    /** 마커나 목록 항목을 눌렀을 때 */
    public void selectStation(String stationId) {
        selectedStationId.setValue(stationId);
    }

    /** 지금 화면 목록에 있는 주유소와 그 계산 결과. 없으면 null */
    public StationSaving findStation(String stationId) {
        List<StationSaving> list = stations.getValue();
        if (list == null || stationId == null) {
            return null;
        }
        for (StationSaving saving : list) {
            if (saving.getStation().getId().equals(stationId)) {
                return saving;
            }
        }
        return null;
    }

    private void loadStations(SearchOrigin center) {
        int requestId = ++stationRequestId;
        stationsLoading.setValue(true);
        stationsError.setValue(null);

        FuelType queryFuelType = fuelType.getValue();
        StationQuery query = new StationQuery(center.getLatitude(), center.getLongitude(), queryFuelType);
        stationRepository.fetchAroundStations(query, new StationRepository.Callback() {
            @Override
            public void onSuccess(List<GasStation> result) {
                if (requestId != stationRequestId) {
                    return;
                }
                fetchedStations = result;
                fetchedOrigin = latestOf(center);
                fetchedFuelType = queryFuelType;
                stationsLoading.setValue(false);
                String selectId = pendingSelectId;
                String selectName = pendingSelectName;
                boolean selectBest = pendingSelectBest;
                pendingSelectId = null;
                pendingSelectName = null;
                pendingSelectBest = false;
                updateDisplayedStations(selectId);
                if (selectId != null && findStation(selectId) == null) {
                    searchedStationMissing.setValue(selectName);
                }
                if (selectBest) {
                    selectBestStation();
                }
            }

            @Override
            public void onError(StationError error) {
                if (requestId != stationRequestId) {
                    return;
                }
                fetchedStations = null;
                pendingSelectId = null;
                pendingSelectName = null;
                pendingSelectBest = false;
                selectedStationId.setValue(null);
                stationsLoading.setValue(false);
                stationsError.setValue(error);
                emptyReason.setValue(StationListBuilder.EmptyReason.NONE);
                stations.setValue(Collections.emptyList());
                // 즐겨찾기 탭은 조회 위치와 유종만 알고, 가격은 상세정보에서 가져온다.
                aroundStations.setValue(new AroundStations(latestOf(center), queryFuelType, null,
                        Collections.emptyList()));
            }
        });
    }

    /**
     * 받아 둔 전체 목록 → 반경 필터 → 숨긴 브랜드 제외 → 브랜드 빠른 필터 → 예상 이득 계산 → 정렬 → 화면.
     * 목록이 바뀌는 곳은 모두 여기를 거친다.
     * 내 차 정보와 내 브랜드 설정(할인 정책, 선호, 숨기기)은 각 Repository의 지금 값을 쓴다.
     * 선택한 주유소가 새 목록에 없으면 선택을 먼저 해제해서, 화면이 지워진 주유소를 선택한 채로 그리지 않게 한다.
     */
    private void updateDisplayedStations() {
        updateDisplayedStations(null);
    }

    /**
     * @param selectId 새 목록에 있으면 목록을 내보내기 전에 선택할 주유소 (주유소 이름 검색에서 고른 곳). 없으면 null
     */
    private RoadDistanceResolver roadDistances(SearchOrigin from) {
        if (from == null) {
            return RoadDistanceResolver.FIXED_FACTOR;
        }
        return RoadDistanceResolver.withFallback(routeDistanceProvider, from.getLatitude(), from.getLongitude());
    }

    private void updateDisplayedStations(String selectId) {
        if (fetchedStations == null) {
            return;
        }
        BrandSettings brandSettings = brandSettingsRepository.getCurrent();
        List<StationSaving> result = StationListBuilder.build(fetchedStations, radius.getValue(),
                sortOrder.getValue(), getEffectiveCarInfo(), brandSettings, brandFilter.getValue(),
                roadDistances(fetchedOrigin));
        if (selectId != null && containsStation(result, selectId)) {
            selectedStationId.setValue(selectId);
        }
        String selectedId = selectedStationId.getValue();
        if (selectedId != null && !containsStation(result, selectedId)) {
            selectedStationId.setValue(null);
        }
        emptyReason.setValue(result.isEmpty()
                ? StationListBuilder.emptyReason(fetchedStations, radius.getValue(), brandSettings,
                        brandFilter.getValue())
                : StationListBuilder.EmptyReason.NONE);
        stations.setValue(result);
        emitLowFuelSuggestionIfReady();
        aroundStations.setValue(new AroundStations(fetchedOrigin, fetchedFuelType, fetchedStations, result));
    }

    private static boolean containsStation(List<StationSaving> list, String stationId) {
        for (StationSaving saving : list) {
            if (saving.getStation().getId().equals(stationId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        carInfoRepository.getCarInfo().removeObserver(carInfoObserver);
        vehicleSession.getFuelDataVersion().removeObserver(vehicleObserver);
        brandSettingsRepository.getBrandSettings().removeObserver(brandSettingsObserver);
        stationRepository.shutdown();
        regionNameRepository.shutdown();
    }
}
