package com.example.findcheapgas.ui.map;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.R;
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
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.model.UserLocation;
import com.example.findcheapgas.navigation.NavPoint;
import com.example.findcheapgas.navigation.WaypointApprovalFlow;
import com.example.findcheapgas.ui.OriginLabels;
import com.example.findcheapgas.ui.SavingColors;
import com.example.findcheapgas.ui.detail.StationDetailActivity;
import com.example.findcheapgas.pleos.NaviHelperNavigationController;
import com.example.findcheapgas.pleos.PleosVehicleSession;
import com.example.findcheapgas.ui.onboarding.OnboardingActivity;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.util.PlaceCompareBuilder;
import com.example.findcheapgas.util.StationListBuilder;
import com.example.findcheapgas.vehicle.DrivingState;
import com.example.findcheapgas.vehicle.LowFuelSuggestion;
import com.example.findcheapgas.viewmodel.MapViewModel;
import com.example.findcheapgas.viewmodel.SearchViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 주유소 목록 화면. 지도 없이 이득 1위 카드와 주변 주유소 목록만 보여준다.
 * 조회, 계산, 필터는 MapViewModel이 하고 여기서는 그 결과를 그린다.
 */
public class MapFragment extends Fragment {

    private static final String TAG = "MapFragment";

    private static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);

    // 상세 화면은 닫힐 때 주유소 ID를 돌려준다. 그 주유소를 선택한 채로 목록에서 그 줄로 이동한다.
    private final ActivityResultLauncher<Intent> detailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onDetailResult);

    private MapViewModel viewModel;
    // 검색창 상태. 검색 결과 시트도 같은 ViewModel을 쓴다.
    private SearchViewModel searchViewModel;

    // 두 번 빨리 눌러서 상세 화면이 두 개 열리지 않게 한다. 상세 화면이 닫히면 풀린다.
    private boolean detailOpening = false;

    private View progress;

    private MaterialButtonToggleGroup searchModeGroup;
    private EditText searchInput;

    private Chip originChip;
    private Chip fuelTypeChip;
    private Chip radiusChip;
    private Chip brandChip;
    private MaterialButton sortButton;

    private TextView listTitle;
    // 주행 중에는 검색, 필터, 목록을 숨기고 카드 1장과 큰 버튼만 보여준다.
    private View topBar;
    private View listHeader;
    private View listCard;
    private View waypointButton;
    private View drivingButton;
    private DrivingState drivingState = DrivingState.UNKNOWN;
    // 연료 부족 자동 제안 카드
    private View lowFuelCard;
    private TextView lowFuelMessage;
    private MaterialButton lowFuelAction;
    @Nullable
    private LowFuelSuggestion lowFuelSuggestion;
    private RecyclerView stationList;
    private LinearLayoutManager stationLayoutManager;
    private StationAdapter stationAdapter;

    // 이득 1위 카드
    private View bestCard;
    private View bestCardContent;
    private TextView bestCardLabel;
    private View bestCardShowBest;
    private TextView bestCardHeadline;
    private TextView bestCardName;
    private TextView bestCardInfo;
    private TextView basisText;
    private View bestCardStatus;
    private View bestCardStatusProgress;
    private TextView bestCardStatusText;
    private MaterialButton bestCardStatusButton;
    // 카드가 지금 보여주는 주유소 (상세 화면에 넘긴다). 안내를 보여주는 중이면 null
    @Nullable
    private StationSaving cardStation;
    // 안내 버튼을 눌렀을 때 할 일 (다시 시도, 권한 요청, 설정 열기)
    @Nullable
    private Runnable cardStatusAction;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 즐겨찾기 탭과 함께 쓰므로 Activity 범위로 만든다.
        viewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        progress = view.findViewById(R.id.location_progress);

        setUpStationList(view);
        setUpBestCard(view);
        setUpSearch(view);
        setUpFilters(view);
        setUpDrivingLayout(view);

        viewModel.getOrigin().observe(getViewLifecycleOwner(), origin -> {
            renderOriginChip(origin);
            renderFilters();
            renderListTitle();
            renderBestCard();
        });
        viewModel.getMockAreaNotice().observe(getViewLifecycleOwner(), show -> {
            if (Boolean.TRUE.equals(show)) {
                Snackbar.make(view, R.string.mock_area_notice, Snackbar.LENGTH_LONG).show();
                viewModel.onMockAreaNoticeShown();
            }
        });
        viewModel.getLastKnownLocationNotice().observe(getViewLifecycleOwner(), show -> {
            if (Boolean.TRUE.equals(show)) {
                Snackbar.make(view, R.string.location_disabled_last_known, Snackbar.LENGTH_LONG)
                        .setAction(R.string.location_open_settings,
                                v -> openSettings(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                        .show();
                viewModel.onLastKnownLocationNoticeShown();
            }
        });
        viewModel.getSearchedStationMissing().observe(getViewLifecycleOwner(), name -> {
            if (name != null) {
                Snackbar.make(view, getString(R.string.searched_station_missing, name), Snackbar.LENGTH_LONG).show();
                viewModel.onSearchedStationMissingShown();
            }
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> renderProgress());
        // 위치 권한·기기 위치 안내는 이득 1위 카드 자리에서 보여준다.
        viewModel.getPermissionState().observe(getViewLifecycleOwner(), state -> renderBestCard());
        viewModel.getLocationEnabled().observe(getViewLifecycleOwner(), enabled -> renderBestCard());

        viewModel.getFuelType().observe(getViewLifecycleOwner(), fuelType -> renderFilters());
        viewModel.getRadius().observe(getViewLifecycleOwner(), radius -> renderFilters());
        viewModel.getSortOrder().observe(getViewLifecycleOwner(), sortOrder -> renderFilters());
        // 칩 글자는 저장된 필터를 지금 브랜드 설정에 맞게 정리한 값으로 쓰므로 둘 다 본다.
        viewModel.getBrandFilter().observe(getViewLifecycleOwner(), filter -> renderBrandChip());
        viewModel.getBrandSettings().observe(getViewLifecycleOwner(), settings -> renderBrandChip());
        // 내 차 정보 수정 화면에서 저장하고 돌아오면 바로 바뀐다.
        viewModel.getCarInfo().observe(getViewLifecycleOwner(), this::renderCalculationBasis);

        // 차량에서 연비를 받으면 계산 기준 문구도 바꾼다 (계산 자체는 MapViewModel이 다시 한다).
        viewModel.getVehicleDataVersion().observe(getViewLifecycleOwner(),
                version -> renderCalculationBasis(viewModel.getCarInfo().getValue()));
        PleosVehicleSession.getInstance(requireContext()).getDrivingState().observe(getViewLifecycleOwner(),
                this::applyDrivingState);
        viewModel.getLowFuelSuggestion().observe(getViewLifecycleOwner(), this::showLowFuelSuggestion);

        viewModel.getStations().observe(getViewLifecycleOwner(), this::showStations);
        viewModel.getStationsLoading().observe(getViewLifecycleOwner(), loading -> {
            renderProgress();
            renderBestCard();
        });
        viewModel.getStationsError().observe(getViewLifecycleOwner(), error -> renderBestCard());
        viewModel.getSelectedStationId().observe(getViewLifecycleOwner(), id -> {
            stationAdapter.setSelectedId(id);
            renderBestCard();
        });

        // 이 탭이 처음 열릴 때 한 번만 권한을 묻는다.
        if (!viewModel.isPermissionRequested()) {
            viewModel.markPermissionRequested();
            if (viewModel.hasPermission()) {
                viewModel.onPermissionGranted();
            } else {
                permissionLauncher.launch(LOCATION_PERMISSIONS);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        renderBestCard(); // 내비게이션 연결 상태가 바뀌었을 수 있다.
        if (viewModel.isPermissionRequested()) {
            if (viewModel.getPermissionState().getValue() != LocationPermissionState.GRANTED) {
                // 앱 설정 화면에서 권한을 켜고 돌아온 경우
                if (viewModel.hasPermission()) {
                    viewModel.onPermissionGranted();
                }
            } else {
                // 위치 설정 화면에서 기기 위치를 켜고 돌아온 경우
                viewModel.checkLocationEnabled();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        searchInput = null;
        searchModeGroup = null;
        stationList = null;
        stationLayoutManager = null;
        stationAdapter = null;
        topBar = null;
        listHeader = null;
        listCard = null;
        waypointButton = null;
        drivingButton = null;
        lowFuelCard = null;
        lowFuelMessage = null;
        lowFuelAction = null;
        cardStation = null;
        cardStatusAction = null;
    }

    // ---------- 권한 ----------

    private void onPermissionResult(Map<String, Boolean> result) {
        if (viewModel.hasPermission()) {
            // 대략적 위치만 허용한 경우도 여기로 온다.
            viewModel.onPermissionGranted();
            return;
        }
        // 거부했는데 시스템이 더 이상 설명을 보여주지 않는다면 "다시 묻지 않음" 상태다.
        // 결과가 비어 있으면 요청이 중간에 취소된 것이므로 다시 요청할 수 있게 둔다.
        boolean permanently = !result.isEmpty()
                && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
        viewModel.onPermissionDenied(permanently);
    }

    /**
     * 이득 1위 카드 자리에 위치 권한 / 기기 위치 안내를 보여줄지.
     * 내 위치를 따라가는 중인데 실제 내 위치를 얻지 못해 기본 위치(서울시청)로 조회한 경우에만 보여준다.
     * 검색한 장소 기준이거나 마지막 위치라도 얻었으면 그 결과가 쓸모 있으므로 결과를 그대로 보여준다.
     * @return 안내 문구와 버튼 문구, 보여주지 않으면 null
     */
    @Nullable
    private int[] locationNotice() {
        SearchOrigin origin = viewModel.getOrigin().getValue();
        UserLocation location = viewModel.getLocation().getValue();
        if ((origin != null && origin.isFixed()) || (location != null && location.isUserLocation())) {
            return null;
        }
        LocationPermissionState state = viewModel.getPermissionState().getValue();
        if (state == LocationPermissionState.PERMANENTLY_DENIED) {
            // "다시 묻지 않음"이라 권한 창을 띄울 수 없다. 앱 설정 화면을 열고, 돌아왔을 때 허용됐으면 바로 내 위치로 조회한다.
            return new int[]{R.string.permission_denied_permanently, R.string.permission_open_settings};
        }
        if (state == LocationPermissionState.DENIED) {
            return new int[]{R.string.permission_rationale, R.string.permission_request};
        }
        if (state == LocationPermissionState.GRANTED
                && Boolean.FALSE.equals(viewModel.getLocationEnabled().getValue())) {
            return new int[]{R.string.location_disabled, R.string.location_open_settings};
        }
        return null;
    }

    /** 권한 요청, 앱 설정 열기(다시 묻지 않음), 위치 설정 열기(기기 위치 꺼짐) 중 지금 상태에 맞는 것 */
    private void requestLocationAccess() {
        LocationPermissionState state = viewModel.getPermissionState().getValue();
        if (state == LocationPermissionState.PERMANENTLY_DENIED) {
            openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().getPackageName(), null)));
        } else if (state == LocationPermissionState.GRANTED) {
            openSettings(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } else {
            permissionLauncher.launch(LOCATION_PERMISSIONS);
        }
    }

    private void openSettings(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "설정 화면을 열 수 없습니다: " + intent.getAction(), e);
        }
    }

    // ---------- 주변 주유소 목록 ----------

    private void setUpStationList(View view) {
        listTitle = view.findViewById(R.id.station_sheet_title);
        stationList = view.findViewById(R.id.station_list);
        stationAdapter = new StationAdapter(this::onStationItemClick);
        stationLayoutManager = new LinearLayoutManager(requireContext());
        stationList.setLayoutManager(stationLayoutManager);
        stationList.setAdapter(stationAdapter);
    }

    private void showStations(List<StationSaving> stations) {
        List<StationSaving> list = stations != null ? stations : Collections.emptyList();
        stationAdapter.submit(list, cheapestPrice(list));
        String selectedId = viewModel.getSelectedStationId().getValue();
        stationAdapter.setSelectedId(selectedId);
        // 주유소 이름 검색에서 고른 주유소처럼 선택된 주유소가 있으면 그 줄을, 없으면 맨 위를 보여준다.
        int selectedPosition = selectedId != null ? stationAdapter.positionOf(selectedId) : RecyclerView.NO_POSITION;
        if (selectedPosition != RecyclerView.NO_POSITION) {
            stationLayoutManager.scrollToPositionWithOffset(selectedPosition, 0);
        } else {
            stationLayoutManager.scrollToPosition(0);
        }
        renderListTitle();
        renderBestCard();
    }

    /** 목록 머리줄: "주변 23곳 · 내 위치 기준". 유종·반경은 필터 칩에 있어서 쓰지 않는다. */
    private void renderListTitle() {
        List<StationSaving> stations = viewModel.getStations().getValue();
        String origin = originLabel(viewModel.getOrigin().getValue());
        if (stations != null && !stations.isEmpty()) {
            listTitle.setText(getString(R.string.station_sheet_title_count, stations.size(), origin));
        } else {
            listTitle.setText(getString(R.string.station_sheet_title, origin));
        }
    }

    /** 위치 조회 중이거나 주유소 조회 중이면 위쪽 진행 막대를 보여준다. */
    private void renderProgress() {
        boolean busy = Boolean.TRUE.equals(viewModel.getLoading().getValue())
                || Boolean.TRUE.equals(viewModel.getStationsLoading().getValue());
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    // ---------- 이득 1위 카드 ----------

    private void setUpBestCard(View view) {
        bestCard = view.findViewById(R.id.best_card);
        bestCardContent = view.findViewById(R.id.best_card_content);
        bestCardLabel = view.findViewById(R.id.best_card_label);
        bestCardShowBest = view.findViewById(R.id.best_card_show_best);
        bestCardHeadline = view.findViewById(R.id.best_card_headline);
        bestCardName = view.findViewById(R.id.best_card_name);
        bestCardInfo = view.findViewById(R.id.best_card_info);
        basisText = view.findViewById(R.id.best_card_basis);
        bestCardStatus = view.findViewById(R.id.best_card_status);
        bestCardStatusProgress = view.findViewById(R.id.best_card_status_progress);
        bestCardStatusText = view.findViewById(R.id.best_card_status_text);
        bestCardStatusButton = view.findViewById(R.id.best_card_status_button);

        // 경유지 추가 버튼 (정차 중에는 카드 안, 주행 중에는 아래 큰 버튼). 누르면 승인 화면을 연다.
        waypointButton = view.findViewById(R.id.best_card_route);
        if (waypointButton instanceof MaterialButton) {
            ((MaterialButton) waypointButton).setText(R.string.waypoint_action);
            waypointButton.setContentDescription(getString(R.string.waypoint_action));
        }
        waypointButton.setOnClickListener(v -> startWaypointApproval());
        drivingButton = view.findViewById(R.id.driving_action_button);
        drivingButton.setOnClickListener(v -> startWaypointApproval());

        // 카드 본문을 누르면 이 화면에서 계산한 결과와 함께 상세 화면을 연다.
        bestCardContent.setOnClickListener(v -> {
            if (cardStation != null) {
                openDetail(cardStation);
            }
        });
        bestCardShowBest.setOnClickListener(v -> showBestStation());
        basisText.setOnClickListener(v ->
                startActivity(OnboardingActivity.createEditIntent(requireContext())));
        bestCardStatusButton.setOnClickListener(v -> {
            if (cardStatusAction != null) {
                cardStatusAction.run();
            }
        });
    }

    /**
     * 이득 1위 카드. 안내가 필요하면 안내를 보여주고, 아니면 주유소 한 곳을 보여준다.
     * 안내 순서: 불러오는 중 → 오류 → 위치 권한·기기 위치 → 위치 확인 중 → 주유소 없음.
     * 주유소는 목록에서 고른 주유소가 있으면 그곳, 없으면 실제 예상 이득 1위(StationSaving.isBest)다.
     * 선호 브랜드의 정렬용 +500원은 쓰지 않으므로 목록 1번 줄과 다를 수 있다.
     */
    private void renderBestCard() {
        if (bestCard == null) {
            return;
        }
        List<StationSaving> stations = viewModel.getStations().getValue();
        StationError error = viewModel.getStationsError().getValue();
        boolean loading = Boolean.TRUE.equals(viewModel.getStationsLoading().getValue());
        int[] locationNotice = locationNotice();

        if (loading) {
            showCardStatus(getString(R.string.station_loading), true, 0, null);
        } else if (error != null) {
            // 하루 한도에 도달했거나 API 키가 없으면 다시 시도해도 같은 결과라 다시 시도 버튼을 두지 않는다.
            boolean retryable = error != StationError.DAILY_LIMIT && error != StationError.API_KEY;
            showCardStatus(getString(errorMessage(error)), false,
                    retryable ? R.string.station_retry : 0, viewModel::retryStations);
        } else if (locationNotice != null) {
            showCardStatus(getString(locationNotice[0]), false, locationNotice[1], this::requestLocationAccess);
        } else if (stations == null) {
            // 아직 위치를 받기 전
            showCardStatus(getString(R.string.station_locating), true, 0, null);
        } else if (stations.isEmpty()) {
            showCardStatus(emptyMessage(), false, 0, null);
        } else {
            StationSaving best = PlaceCompareBuilder.findBest(stations);
            StationSaving selected = viewModel.findStation(viewModel.getSelectedStationId().getValue());
            showCardStation(selected != null ? selected : best != null ? best : stations.get(0));
        }
    }

    /** 주유소가 없는 이유: 반경 때문인지, 숨긴 브랜드나 빠른 필터 때문인지 나눠서 알려준다. */
    private String emptyMessage() {
        StationListBuilder.EmptyReason reason = viewModel.getEmptyReason().getValue();
        if (reason == StationListBuilder.EmptyReason.RADIUS && viewModel.isOutsideMockArea()) {
            return getString(R.string.mock_area_notice);
        } else if (reason == StationListBuilder.EmptyReason.BRAND_FILTER) {
            return getString(R.string.station_empty_brand_filter);
        } else if (reason == StationListBuilder.EmptyReason.HIDDEN_BRANDS) {
            return getString(R.string.station_empty_hidden_brands);
        }
        return getString(R.string.station_empty, viewModel.getRadius().getValue().getLabel());
    }

    private void showCardStatus(String message, boolean showProgress, @StringRes int button,
                                @Nullable Runnable action) {
        cardStation = null;
        cardStatusAction = button != 0 ? action : null;
        bestCardStatusText.setText(message);
        bestCardStatusProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        if (button != 0) {
            bestCardStatusButton.setText(button);
        }
        bestCardStatusButton.setVisibility(button != 0 ? View.VISIBLE : View.GONE);
        bestCardStatus.setVisibility(View.VISIBLE);
        bestCardContent.setVisibility(View.GONE);
        updateActionButtons();
    }

    private void showCardStation(StationSaving saving) {
        cardStation = saving;
        cardStatusAction = null;
        GasStation station = saving.getStation();
        boolean best = saving.isBest();

        bestCardLabel.setText(best ? R.string.best_card_label : R.string.best_card_label_selected);
        bestCardShowBest.setVisibility(best ? View.GONE : View.VISIBLE);
        String headline = FormatUtil.formatSavingHeadline(saving);
        bestCardHeadline.setText(headline);
        bestCardHeadline.setTextColor(ContextCompat.getColor(requireContext(),
                SavingColors.heroTextColorOf(saving.getLevel())));
        bestCardName.setText(station.getName());
        String info = getString(R.string.best_card_info, station.getBrandName(),
                FormatUtil.formatDistance(station.getDistance()), FormatUtil.formatPrice(station.getPrice()));
        bestCardInfo.setText(info);
        bestCardContent.setContentDescription(getString(R.string.best_card_description,
                bestCardLabel.getText(), headline, station.getName() + ", " + info));

        bestCardStatus.setVisibility(View.GONE);
        bestCardContent.setVisibility(View.VISIBLE);
        updateActionButtons();
    }

    // ---------- 주행 / 정차 화면, 경유지 승인 ----------

    private void setUpDrivingLayout(View view) {
        topBar = view.findViewById(R.id.map_top_bar);
        listHeader = view.findViewById(R.id.station_list_header);
        listCard = view.findViewById(R.id.station_list_card);

        lowFuelCard = view.findViewById(R.id.low_fuel_card);
        lowFuelMessage = view.findViewById(R.id.low_fuel_message);
        lowFuelAction = view.findViewById(R.id.low_fuel_action);
        lowFuelAction.setOnClickListener(v -> onLowFuelAction());
        view.findViewById(R.id.low_fuel_later).setOnClickListener(v -> viewModel.dismissLowFuelSuggestion());
    }

    /**
     * 주행 중(DRIVING)에는 카드 1장과 큰 버튼만, 정차(STOPPED)나 차량 데이터 없음(UNKNOWN)에는 목록과 계산 내역까지 보여준다.
     * 차량 데이터가 없는 폰이나 에뮬레이터에서도 화면을 쓸 수 있게 UNKNOWN은 전체 화면으로 둔다.
     */
    private void applyDrivingState(DrivingState state) {
        if (state == null || topBar == null) {
            return;
        }
        drivingState = state;
        applyLayout();
    }

    private void updateActionButtons() {
        applyLayout();
    }

    /**
     * 주행 상태, 연료 부족 제안, 내비게이션 연결 여부에 맞춰 화면 요소를 보이거나 숨긴다.
     * - 주행 중: 검색, 필터, 목록을 숨긴다. 제안이 있으면 제안 카드(큰 버튼 두 개)만, 없으면 이득 1위 카드와 큰 버튼 하나.
     * - 정차 중, 차량 데이터 없음: 모두 보인다. 제안은 맨 위 카드로 보인다.
     * 경유지 버튼은 보여줄 주유소가 있고 내비게이션에 연결돼 있을 때만 보인다.
     */
    private void applyLayout() {
        if (topBar == null || waypointButton == null || drivingButton == null) {
            return;
        }
        boolean driving = drivingState == DrivingState.DRIVING;
        boolean hasSuggestion = lowFuelSuggestion != null;
        boolean connected = NaviHelperNavigationController.getInstance(requireContext()).isConnected();

        int detailVisibility = driving ? View.GONE : View.VISIBLE;
        topBar.setVisibility(detailVisibility);
        listHeader.setVisibility(detailVisibility);
        listCard.setVisibility(detailVisibility);

        lowFuelCard.setVisibility(hasSuggestion ? View.VISIBLE : View.GONE);
        lowFuelAction.setVisibility(connected ? View.VISIBLE : View.GONE);
        bestCard.setVisibility(driving && hasSuggestion ? View.GONE : View.VISIBLE);

        boolean canAct = cardStation != null && connected;
        waypointButton.setVisibility(canAct && !driving ? View.VISIBLE : View.GONE);
        drivingButton.setVisibility(canAct && driving && !hasSuggestion ? View.VISIBLE : View.GONE);
    }

    private void showLowFuelSuggestion(@Nullable LowFuelSuggestion suggestion) {
        lowFuelSuggestion = suggestion;
        if (suggestion != null && lowFuelMessage != null) {
            lowFuelMessage.setText(suggestion.message());
            lowFuelAction.setText(suggestion.actionLabel());
        }
        applyLayout();
    }

    /**
     * 제안을 승인했다. 안내 중인 경로가 있으면 경유지 승인 화면을 거치고, 없으면 이 버튼이 승인이라
     * 그 주유소로 안내를 요청한다. 어느 쪽이든 이 제안은 여기서 끝난다.
     */
    private void onLowFuelAction() {
        LowFuelSuggestion suggestion = lowFuelSuggestion;
        if (suggestion == null) {
            return;
        }
        viewModel.dismissLowFuelSuggestion();
        NaviHelperNavigationController nav = NaviHelperNavigationController.getInstance(requireContext());
        GasStation station = suggestion.getSaving().getStation();
        if (nav.getCurrentRouteInfo().isPresent()) {
            startWaypointApproval(suggestion.getSaving());
        } else {
            boolean sent = nav.setDestination(
                    new NavPoint(station.getName(), station.getLatitude(), station.getLongitude()));
            Toast.makeText(requireContext(), sent ? R.string.route_requested : R.string.route_request_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 경유지 추가 승인 화면. 여기서 "경유지로 추가"를 눌렀을 때만 내비게이션에 요청을 보낸다.
     * 닫거나 취소하면 아무것도 보내지 않는다.
     */
    private void startWaypointApproval() {
        startWaypointApproval(cardStation);
    }

    private void startWaypointApproval(@Nullable StationSaving saving) {
        if (saving == null) {
            return;
        }
        GasStation station = saving.getStation();
        WaypointApprovalFlow flow = new WaypointApprovalFlow(NaviHelperNavigationController.getInstance(requireContext()));
        WaypointApprovalFlow.Proposal proposal = flow.propose(
                new NavPoint(station.getName(), station.getLatitude(), station.getLongitude()),
                FormatUtil.formatSavingHeadline(saving));

        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_waypoint_approval, null);
        ((TextView) content.findViewById(R.id.approval_station)).setText(station.getName());
        ((TextView) content.findViewById(R.id.approval_summary)).setText(proposal.getSummary());
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(content)
                .setOnCancelListener(d -> flow.reject(proposal))
                .create();
        content.findViewById(R.id.approval_confirm).setOnClickListener(v -> {
            dialog.dismiss();
            boolean sent = flow.approve(proposal);
            Toast.makeText(requireContext(), sent ? R.string.waypoint_added : R.string.waypoint_failed,
                    Toast.LENGTH_LONG).show();
        });
        content.findViewById(R.id.approval_cancel).setOnClickListener(v -> {
            flow.reject(proposal);
            dialog.dismiss();
        });
        dialog.show();
    }

    /** "이득 1위 보기": 이득 1위를 선택하고 목록에서 그 줄로 이동한다. */
    private void showBestStation() {
        viewModel.selectBestStation();
        StationSaving best = PlaceCompareBuilder.findBest(viewModel.getStations().getValue());
        if (best == null) {
            return;
        }
        scrollListTo(best.getStation().getId());
    }

    @StringRes
    private static int errorMessage(StationError error) {
        switch (error) {
            case NETWORK:
                return R.string.station_error_network;
            case DAILY_LIMIT:
                return R.string.station_error_daily_limit;
            case API_KEY:
                return R.string.opinet_key_missing;
            default:
                return R.string.station_error_opinet;
        }
    }

    /** 목록 항목을 누르면 그 주유소를 선택하고 상세 화면을 연다. */
    private void onStationItemClick(StationSaving saving) {
        viewModel.selectStation(saving.getStation().getId());
        openDetail(saving);
    }

    /** 이 화면에서 계산한 예상 이득 결과를 그대로 넘긴다. 상세 화면은 이 값을 다시 계산하지 않는다. */
    private void openDetail(StationSaving saving) {
        if (detailOpening) {
            return;
        }
        detailOpening = true;
        detailLauncher.launch(StationDetailActivity.createIntent(requireContext(), saving,
                viewModel.getFuelType().getValue(), originLabel(viewModel.getOrigin().getValue())));
    }

    /** 상세 화면에서 돌아오면 그 주유소를 선택한 채로 목록에서 그 줄로 이동한다. */
    private void onDetailResult(ActivityResult result) {
        detailOpening = false;
        String stationId = StationDetailActivity.stationIdFrom(result.getData());
        StationSaving saving = viewModel.findStation(stationId);
        if (saving == null || getView() == null) {
            return; // 그사이 목록이 바뀌어 주유소가 없으면 그대로 둔다.
        }
        viewModel.selectStation(stationId);
        scrollListTo(stationId);
    }

    private void scrollListTo(String stationId) {
        int position = stationAdapter.positionOf(stationId);
        if (position != RecyclerView.NO_POSITION) {
            stationLayoutManager.scrollToPositionWithOffset(position, 0);
        }
    }

    private static int cheapestPrice(List<StationSaving> stations) {
        int cheapest = Integer.MAX_VALUE;
        for (StationSaving saving : stations) {
            cheapest = Math.min(cheapest, saving.getStation().getPrice());
        }
        return cheapest;
    }

    // ---------- 검색창, 조회 위치 ----------

    private void setUpSearch(View view) {
        searchModeGroup = view.findViewById(R.id.search_mode_group);
        searchInput = view.findViewById(R.id.search_input);
        View searchButton = view.findViewById(R.id.search_button);

        SearchViewModel.Mode mode = searchViewModel.getMode().getValue();
        searchModeGroup.check(mode == SearchViewModel.Mode.STATION ? R.id.search_mode_station : R.id.search_mode_place);
        renderSearchHint(mode);
        searchModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            SearchViewModel.Mode newMode = checkedId == R.id.search_mode_station
                    ? SearchViewModel.Mode.STATION : SearchViewModel.Mode.PLACE;
            searchViewModel.setMode(newMode);
            renderSearchHint(newMode);
        });

        // 입력 중에는 검색하지 않는다. 검색 버튼이나 키보드의 검색 키를 눌렀을 때만 검색한다.
        searchButton.setOnClickListener(v -> submitSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch();
                return true;
            }
            return false;
        });

        originChip = view.findViewById(R.id.chip_origin);
        originChip.setOnCloseIconClickListener(v -> viewModel.returnToMyLocation());
    }

    private void renderSearchHint(SearchViewModel.Mode mode) {
        searchInput.setHint(mode == SearchViewModel.Mode.STATION
                ? R.string.search_hint_station : R.string.search_hint_place);
    }

    private void submitSearch() {
        SearchViewModel.SubmitResult result = searchViewModel.submit(searchInput.getText().toString());
        if (result == SearchViewModel.SubmitResult.TOO_SHORT) {
            Toast.makeText(requireContext(), R.string.search_too_short, Toast.LENGTH_SHORT).show();
            return;
        }
        if (result == SearchViewModel.SubmitResult.BLANK) {
            Toast.makeText(requireContext(), R.string.search_blank, Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard();
        if (getChildFragmentManager().findFragmentByTag(SearchResultSheet.TAG) == null) {
            new SearchResultSheet().show(getChildFragmentManager(), SearchResultSheet.TAG);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = ContextCompat.getSystemService(requireContext(), InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
        searchInput.clearFocus();
    }

    /** 검색한 장소·주유소 기준일 때만 "강남역 기준" 칩을 보여준다. 닫으면 내 위치 기준으로 돌아간다. */
    private void renderOriginChip(SearchOrigin origin) {
        if (origin == null || !origin.isFixed()) {
            originChip.setVisibility(View.GONE);
            return;
        }
        String name = originLabel(origin);
        originChip.setText(getString(R.string.origin_chip, name));
        originChip.setContentDescription(getString(R.string.origin_chip_description, name));
        originChip.setVisibility(View.VISIBLE);
    }

    /** 조회 위치 이름: 검색한 장소 이름, 내 위치, 기본 위치(서울시청) */
    private String originLabel(SearchOrigin origin) {
        return OriginLabels.of(requireContext(), origin);
    }

    // ---------- 유종, 반경, 브랜드, 정렬 선택 ----------

    private void setUpFilters(View view) {
        fuelTypeChip = view.findViewById(R.id.chip_fuel_type);
        radiusChip = view.findViewById(R.id.chip_radius);
        brandChip = view.findViewById(R.id.chip_brand);
        sortButton = view.findViewById(R.id.btn_sort);

        // 칩의 ▼ 아이콘을 눌러도 칩을 누른 것과 같게 한다.
        fuelTypeChip.setOnClickListener(v -> showFuelTypeMenu(fuelTypeChip));
        fuelTypeChip.setOnCloseIconClickListener(v -> showFuelTypeMenu(fuelTypeChip));
        radiusChip.setOnClickListener(v -> showRadiusMenu(radiusChip));
        radiusChip.setOnCloseIconClickListener(v -> showRadiusMenu(radiusChip));
        brandChip.setOnClickListener(v -> showBrandFilter());
        brandChip.setOnCloseIconClickListener(v -> showBrandFilter());
        sortButton.setOnClickListener(this::showSortMenu);
    }

    private void showFuelTypeMenu(View anchor) {
        showChoiceMenu(anchor, FuelType.values(), viewModel.getFuelType().getValue(),
                FuelType::getLabel, viewModel::setFuelType);
    }

    private void showRadiusMenu(View anchor) {
        showChoiceMenu(anchor, SearchRadius.values(), viewModel.getRadius().getValue(),
                SearchRadius::getLabel, viewModel::setRadius);
    }

    /** 여러 개를 고를 수 있어서 팝업 메뉴 대신 시트를 연다. 이미 열려 있으면 다시 열지 않는다. */
    private void showBrandFilter() {
        if (getChildFragmentManager().findFragmentByTag(BrandFilterSheet.TAG) == null) {
            new BrandFilterSheet().show(getChildFragmentManager(), BrandFilterSheet.TAG);
        }
    }

    /** 정렬 메뉴는 SortOrder 상수 순서대로 만든다 (예상 이득순, 가격순, 거리순). */
    private void showSortMenu(View anchor) {
        showChoiceMenu(anchor, SortOrder.values(), viewModel.getSortOrder().getValue(),
                SortOrder::getLabel, viewModel::setSortOrder);
    }

    /** 하나만 고를 수 있는 팝업 메뉴. 지금 선택한 항목에 체크 표시를 한다. */
    private <T> void showChoiceMenu(View anchor, T[] options, T current,
                                    Function<T, String> label, Consumer<T> onChoice) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        Menu menu = popup.getMenu();
        for (int i = 0; i < options.length; i++) {
            menu.add(Menu.NONE, i, i, label.apply(options[i])).setChecked(options[i] == current);
        }
        menu.setGroupCheckable(Menu.NONE, true, true);
        popup.setOnMenuItemClickListener(item -> {
            onChoice.accept(options[item.getItemId()]);
            return true;
        });
        popup.show();
    }

    /** 필터 칩("휘발유", "5km")과 정렬 버튼을 지금 선택에 맞춘다. */
    private void renderFilters() {
        FuelType fuelType = viewModel.getFuelType().getValue();
        SearchRadius radius = viewModel.getRadius().getValue();
        SortOrder sortOrder = viewModel.getSortOrder().getValue();
        if (fuelType == null || radius == null || sortOrder == null) {
            return;
        }
        fuelTypeChip.setText(fuelType.getLabel());
        fuelTypeChip.setContentDescription(getString(R.string.filter_fuel_type_description, fuelType.getLabel()));
        radiusChip.setText(radius.getLabel());
        radiusChip.setContentDescription(getString(R.string.filter_radius_description, radius.getLabel()));
        sortButton.setText(sortOrder.getLabel());
        sortButton.setContentDescription(getString(R.string.station_sort_description, sortOrder.getLabel()));
    }

    /** 브랜드 칩 글자: "브랜드", "할인 브랜드", "SK", "브랜드 2" 등. 접근성 설명에는 고른 내용("SK 외 1")을 쓴다. */
    private void renderBrandChip() {
        BrandFilter filter = viewModel.getBrandFilter().getValue();
        BrandSettings settings = viewModel.getBrandSettings().getValue();
        if (filter == null || settings == null) {
            return;
        }
        BrandFilter effective = filter.effective(settings);
        brandChip.setText(effective.chipLabel());
        brandChip.setContentDescription(getString(R.string.filter_brand_description, effective.label()));
    }

    /** 이득 1위 카드 맨 아래 계산 기준 문구. 기본값이면 "(기본값)"이 붙는다. */
    private void renderCalculationBasis(CarInfo carInfo) {
        // 계산에 실제로 쓰는 값을 보여준다: 차량 연비가 있으면 그 값(표시에 밝힘), 없으면 입력값/기본값
        String basis = FormatUtil.formatCalculationBasis(viewModel.getEffectiveCarInfo());
        if (viewModel.isUsingVehicleEfficiency()) {
            basis = getString(R.string.basis_vehicle_suffix, basis);
        }
        basisText.setText(basis);
        basisText.setContentDescription(getString(R.string.station_basis_description, basis));
    }
}
