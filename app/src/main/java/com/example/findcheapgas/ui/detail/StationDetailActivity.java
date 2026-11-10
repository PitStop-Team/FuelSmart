package com.example.findcheapgas.ui.detail;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.AverageComparison;
import com.example.findcheapgas.model.Brand;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.DailyAveragePrice;
import com.example.findcheapgas.model.DiscountRule;
import com.example.findcheapgas.model.FavoriteItem;
import com.example.findcheapgas.model.FavoritePrice;
import com.example.findcheapgas.model.FavoriteStation;
import com.example.findcheapgas.model.FuelPrice;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.StationDetail;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.ui.AppFonts;
import com.example.findcheapgas.ui.SavingColors;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.util.PriceTrend;
import com.example.findcheapgas.viewmodel.DetailViewModel;
import com.example.findcheapgas.viewmodel.SectionState;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

/**
 * 주유소 상세 화면. 지도 탭에서 목록 항목을 누르거나, 선택된 마커를 한 번 더 누르면 열린다.
 * 목록에서 이미 아는 정보(상호, 브랜드, 선택한 유종 가격, 거리)는 Intent로 받아 바로 보여주고,
 * 상세 조회(DetailViewModel)가 끝나면 주소, 전화, 유종별 가격, 부가시설을 채운다.
 * 상세 조회가 실패해도 기본 정보는 그대로 둔다.
 * 맨 위 카드에는 지도 탭이 계산한 결과(StationSaving)를 Intent로 받아 주인공 숫자(예상 이득)와 기준 주유소, 계산 기준을 보여주고,
 * "계산 내역 보기"를 누르면 계산 내역 표를 펼친다 (펼침 상태는 회전해도 유지).
 * 여기서는 다시 조회하거나 계산하지 않고, 계산 기준(내 차 정보)도 바꿀 수 없다.
 * 닫힐 때 주유소 ID를 결과로 돌려줘서 지도 탭이 그 주유소를 선택한 채로 보여주게 한다.
 * 즐겨찾기 탭에서도 열린다 (createFavoriteIntent). 이때는 "지도에서 보기" 버튼이 보이고, 누르면 결과로 알려서
 * 지도 탭이 그 주유소 위치를 조회 위치로 주변을 조회하고 그 주유소를 선택한다.
 * 아래 즐겨찾기 버튼(북마크)으로 즐겨찾기를 추가하거나 삭제한다.
 * 평균가 대비(전국·지역 평균과 표시 가격 비교)와 7일 가격 추이(최근 7일 전국 평균 그래프)는 상세 조회와 따로 불러오고,
 * 못 불러오면 그 구역만 짧은 안내로 바꾼다.
 */
public class StationDetailActivity extends AppCompatActivity {

    private static final String TAG = "StationDetailActivity";

    public static final String EXTRA_STATION_ID = "station_id";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_BRAND_CODE = "brand_code";
    private static final String EXTRA_PRICE = "price";
    private static final String EXTRA_DISTANCE = "distance";
    private static final String EXTRA_FUEL_TYPE = "fuel_type";
    private static final String EXTRA_ORIGIN_LABEL = "origin_label";
    private static final String EXTRA_SAVING = "saving";
    private static final String EXTRA_KATEC_X = "katec_x";
    private static final String EXTRA_KATEC_Y = "katec_y";
    private static final String EXTRA_LATITUDE = "latitude";
    private static final String EXTRA_LONGITUDE = "longitude";
    private static final String EXTRA_ADDRESS = "address";
    // 즐겨찾기 탭에서 열었으면 true. "지도에서 보기" 버튼을 보여준다.
    private static final String EXTRA_FROM_FAVORITES = "from_favorites";
    // 결과: "지도에서 보기"를 눌렀으면 true
    private static final String EXTRA_SHOW_ON_MAP = "show_on_map";

    // 계산 내역을 펼쳤는지 (화면 회전 뒤에도 유지)
    private static final String STATE_SAVING_EXPANDED = "saving_expanded";

    // 1회 할인 한도를 비워 둔 채 할인액이 이 금액 이상이면 "한도가 있다면..." 주의 문구를 보여준다.
    private static final int NO_CAP_WARNING_DISCOUNT = 5_000;

    private DetailViewModel viewModel;
    private FuelType selectedFuelType;
    private MaterialButton favoriteButton;
    private boolean savingExpanded;
    private View savingSection;
    private MaterialButton savingToggle;

    private View status;
    private View statusProgress;
    private TextView statusText;
    private View retryButton;
    private View content;
    private TextView addressText;
    private View phoneSection;
    private MaterialButton phoneButton;
    private LinearLayout priceRows;
    private View pricesEmpty;
    private ChipGroup facilityGroup;
    private View facilitiesEmpty;
    private View selfNote;
    private TextView averageBasis;
    private LinearLayout averageRows;
    private TextView averageStatus;
    private View averageNote;
    private TextView trendHeading;
    private PriceTrendView trendChart;
    private TextView trendSummary;
    private TextView trendStatus;

    /**
     * 목록의 주유소 정보, 예상 이득 계산 결과, 지금 고른 유종으로 상세 화면을 여는 Intent
     *
     * @param originLabel 조회 위치 이름 (예: 내 위치, 강남역). "강남역에서 0.7km"처럼 거리 앞에 붙는다.
     */
    public static Intent createIntent(Context context, StationSaving saving, FuelType fuelType, String originLabel) {
        GasStation station = saving.getStation();
        return new Intent(context, StationDetailActivity.class)
                .putExtra(EXTRA_SAVING, saving)
                .putExtra(EXTRA_STATION_ID, station.getId())
                .putExtra(EXTRA_NAME, station.getName())
                .putExtra(EXTRA_BRAND_CODE, station.getBrandCode())
                .putExtra(EXTRA_PRICE, station.getPrice())
                .putExtra(EXTRA_DISTANCE, station.getDistance())
                .putExtra(EXTRA_FUEL_TYPE, fuelType.getCode())
                .putExtra(EXTRA_ORIGIN_LABEL, originLabel)
                .putExtra(EXTRA_KATEC_X, station.getKatecX())
                .putExtra(EXTRA_KATEC_Y, station.getKatecY())
                .putExtra(EXTRA_LATITUDE, station.getLatitude())
                .putExtra(EXTRA_LONGITUDE, station.getLongitude());
    }

    /**
     * 즐겨찾기 탭에서 여는 Intent. 가격, 거리, 예상 이득은 즐겨찾기 탭이 아는 만큼만 넘긴다.
     *
     * @param item        즐겨찾기 한 줄 (가격이 없거나 5km 밖이면 예상 이득 없음)
     * @param originLabel 조회 위치 이름
     */
    public static Intent createFavoriteIntent(Context context, FavoriteItem item, FuelType fuelType,
                                              String originLabel) {
        FavoriteStation favorite = item.getFavorite();
        FavoritePrice price = item.getPrice();
        Double distance = item.getDistance();
        Intent intent = new Intent(context, StationDetailActivity.class)
                .putExtra(EXTRA_STATION_ID, favorite.getId())
                .putExtra(EXTRA_NAME, favorite.getName())
                .putExtra(EXTRA_BRAND_CODE, favorite.getBrandCode())
                .putExtra(EXTRA_PRICE, price != null && price.isAvailable() ? price.getPrice() : 0)
                .putExtra(EXTRA_DISTANCE, distance != null ? distance : Double.NaN)
                .putExtra(EXTRA_FUEL_TYPE, fuelType.getCode())
                .putExtra(EXTRA_ORIGIN_LABEL, originLabel)
                .putExtra(EXTRA_KATEC_X, favorite.getKatecX())
                .putExtra(EXTRA_KATEC_Y, favorite.getKatecY())
                .putExtra(EXTRA_LATITUDE, favorite.getLatitude())
                .putExtra(EXTRA_LONGITUDE, favorite.getLongitude())
                .putExtra(EXTRA_ADDRESS, favorite.getAddress())
                .putExtra(EXTRA_FROM_FAVORITES, true);
        if (item.getSaving() != null) {
            intent.putExtra(EXTRA_SAVING, item.getSaving());
        }
        return intent;
    }

    /** 상세 화면에서 "지도에서 보기"를 눌렀을 때 지도 탭에 넘길 주유소 */
    public static final class ShowOnMapRequest {
        public final String stationId;
        public final String name;
        public final double latitude;
        public final double longitude;

        ShowOnMapRequest(String stationId, String name, double latitude, double longitude) {
            this.stationId = stationId;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /** 결과가 "지도에서 보기"면 그 주유소, 아니면 null */
    @Nullable
    public static ShowOnMapRequest showOnMapFrom(@Nullable Intent result) {
        if (result == null || !result.getBooleanExtra(EXTRA_SHOW_ON_MAP, false)) {
            return null;
        }
        String id = result.getStringExtra(EXTRA_STATION_ID);
        String name = result.getStringExtra(EXTRA_NAME);
        if (id == null || name == null) {
            return null;
        }
        return new ShowOnMapRequest(id, name, result.getDoubleExtra(EXTRA_LATITUDE, 0),
                result.getDoubleExtra(EXTRA_LONGITUDE, 0));
    }

    /** 상세 화면이 돌려준 결과에서 주유소 ID를 꺼낸다. 없으면 null */
    @Nullable
    public static String stationIdFrom(@Nullable Intent result) {
        return result != null ? result.getStringExtra(EXTRA_STATION_ID) : null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String stationId = intent.getStringExtra(EXTRA_STATION_ID);
        if (stationId == null) {
            Log.e(TAG, "주유소 ID 없이 열렸습니다.");
            finish();
            return;
        }
        setContentView(R.layout.activity_station_detail);
        // 뒤로 가기, 위쪽 화살표 어느 쪽으로 닫혀도 지도 탭이 이 주유소를 선택하도록 미리 결과를 넣어 둔다.
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_STATION_ID, stationId));

        selectedFuelType = FuelType.fromCode(intent.getStringExtra(EXTRA_FUEL_TYPE), FuelType.GASOLINE);
        savingExpanded = savedInstanceState != null && savedInstanceState.getBoolean(STATE_SAVING_EXPANDED);
        bindViews();
        showSummary(intent);
        StationSaving saving = savingFrom(intent);
        showHero(saving);
        showSaving(saving);

        viewModel = new ViewModelProvider(this).get(DetailViewModel.class);
        viewModel.getDetail().observe(this, detail -> {
            renderStatus();
            if (detail != null) {
                showDetail(detail);
            }
        });
        viewModel.getLoading().observe(this, loading -> renderStatus());
        viewModel.getError().observe(this, error -> renderStatus());
        viewModel.getAverageSection().observe(this, this::renderAverageSection);
        viewModel.getTrendSection().observe(this, this::renderTrendSection);
        // 화면을 회전해도 ViewModel이 남아 있으므로 다시 조회하지 않는다 (상세정보, 평균가, 7일 추이).
        viewModel.start(stationId, selectedFuelType, intent.getIntExtra(EXTRA_PRICE, 0),
                intent.getStringExtra(EXTRA_ADDRESS));
        setUpActions(intent, stationId);
        viewModel.isFavorite().observe(this, this::renderFavoriteButton);
        viewModel.getFavoriteMessage().observe(this, this::showFavoriteMessage);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SAVING_EXPANDED, savingExpanded);
    }

    /**
     * 아래 버튼 줄: 즐겨찾기(북마크, 누를 때마다 추가 / 삭제), 즐겨찾기 탭에서 열었으면 "지도에서 보기",
     * 길찾기(카카오맵 / 티맵 선택창, 좌표가 없으면 숨김).
     */
    private void setUpActions(Intent intent, String stationId) {
        favoriteButton = findViewById(R.id.detail_favorite_button);
        favoriteButton.setOnClickListener(v -> viewModel.toggleFavorite(favoriteFrom(intent, stationId)));
        View showOnMap = findViewById(R.id.detail_show_on_map_button);
        boolean fromFavorites = intent.getBooleanExtra(EXTRA_FROM_FAVORITES, false);
        showOnMap.setVisibility(fromFavorites ? View.VISIBLE : View.GONE);
        showOnMap.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent()
                    .putExtra(EXTRA_STATION_ID, stationId)
                    .putExtra(EXTRA_SHOW_ON_MAP, true)
                    .putExtra(EXTRA_NAME, intent.getStringExtra(EXTRA_NAME))
                    .putExtra(EXTRA_LATITUDE, intent.getDoubleExtra(EXTRA_LATITUDE, 0))
                    .putExtra(EXTRA_LONGITUDE, intent.getDoubleExtra(EXTRA_LONGITUDE, 0)));
            finish();
        });
        setUpRouteButton(intent);
        findViewById(R.id.detail_actions).setVisibility(View.VISIBLE);
    }

    /**
     * 길찾기 앱(카카오맵, 티맵) 호출은 없앴다. 내비게이션 연동(NaviHelper)이 들어오기 전까지 버튼을 숨긴다.
     */
    private void setUpRouteButton(Intent intent) {
        findViewById(R.id.detail_route_button).setVisibility(View.GONE);
    }

    /** 즐겨찾기로 저장할 값. 주소는 상세 조회가 끝났으면 그 주소, 아니면 넘겨받은 주소(없으면 null). */
    private FavoriteStation favoriteFrom(Intent intent, String stationId) {
        StationDetail detail = viewModel.getDetail().getValue();
        String address = detail != null ? detail.getAddress() : intent.getStringExtra(EXTRA_ADDRESS);
        String name = intent.getStringExtra(EXTRA_NAME);
        return new FavoriteStation(stationId, name != null ? name : stationId,
                intent.getStringExtra(EXTRA_BRAND_CODE), address,
                intent.getDoubleExtra(EXTRA_KATEC_X, 0), intent.getDoubleExtra(EXTRA_KATEC_Y, 0),
                intent.getDoubleExtra(EXTRA_LATITUDE, 0), intent.getDoubleExtra(EXTRA_LONGITUDE, 0),
                System.currentTimeMillis());
    }

    /** 즐겨찾기면 채운 북마크와 "즐겨찾기됨", 아니면 빈 북마크와 "즐겨찾기" */
    private void renderFavoriteButton(Boolean favorite) {
        boolean saved = Boolean.TRUE.equals(favorite);
        favoriteButton.setIconResource(saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        favoriteButton.setText(saved ? R.string.detail_favorite_saved : R.string.detail_favorite_add);
        favoriteButton.setContentDescription(getString(saved
                ? R.string.detail_favorite_remove_description : R.string.detail_favorite_add_description));
    }

    private void showFavoriteMessage(@Nullable DetailViewModel.FavoriteMessage message) {
        if (message == null) {
            return;
        }
        String text;
        if (message == DetailViewModel.FavoriteMessage.ADDED) {
            text = getString(R.string.detail_favorite_added);
        } else if (message == DetailViewModel.FavoriteMessage.REMOVED) {
            text = getString(R.string.detail_favorite_removed);
        } else {
            text = getString(R.string.detail_favorite_full, FavoriteStation.MAX_COUNT);
        }
        Toast.makeText(this, text, message == DetailViewModel.FavoriteMessage.FULL
                ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        viewModel.onFavoriteMessageShown();
    }

    private void bindViews() {
        MaterialToolbar toolbar = findViewById(R.id.detail_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        status = findViewById(R.id.detail_status);
        statusProgress = findViewById(R.id.detail_status_progress);
        statusText = findViewById(R.id.detail_status_text);
        retryButton = findViewById(R.id.detail_retry_button);
        savingSection = findViewById(R.id.detail_saving_section);
        savingToggle = findViewById(R.id.detail_saving_toggle);
        savingToggle.setOnClickListener(v -> {
            savingExpanded = !savingExpanded;
            renderSavingExpanded();
        });
        content = findViewById(R.id.detail_content);
        addressText = findViewById(R.id.detail_address);
        phoneSection = findViewById(R.id.detail_phone_section);
        phoneButton = findViewById(R.id.detail_phone);
        priceRows = findViewById(R.id.detail_price_rows);
        pricesEmpty = findViewById(R.id.detail_prices_empty);
        facilityGroup = findViewById(R.id.detail_facilities);
        facilitiesEmpty = findViewById(R.id.detail_facilities_empty);
        selfNote = findViewById(R.id.detail_self_note);
        averageBasis = findViewById(R.id.detail_average_basis);
        averageRows = findViewById(R.id.detail_average_rows);
        averageStatus = findViewById(R.id.detail_average_status);
        averageNote = findViewById(R.id.detail_average_note);
        trendHeading = findViewById(R.id.detail_trend_heading);
        trendChart = findViewById(R.id.detail_trend_chart);
        trendSummary = findViewById(R.id.detail_trend_summary);
        trendStatus = findViewById(R.id.detail_trend_status);

        retryButton.setOnClickListener(v -> viewModel.retry());
    }

    /** 목록에서 넘겨받은 기본 정보 */
    private void showSummary(Intent intent) {
        String name = intent.getStringExtra(EXTRA_NAME);
        ((TextView) findViewById(R.id.detail_name)).setText(name);
        String originLabel = intent.getStringExtra(EXTRA_ORIGIN_LABEL);
        String brand = Brand.nameOf(intent.getStringExtra(EXTRA_BRAND_CODE));
        double distance = intent.getDoubleExtra(EXTRA_DISTANCE, 0);
        // 즐겨찾기 탭에서 열 때 조회 위치를 아직 모르면 거리가 없다.
        ((TextView) findViewById(R.id.detail_info)).setText(Double.isNaN(distance)
                ? getString(R.string.detail_info_no_distance, brand)
                : getString(R.string.detail_info, brand,
                        originLabel != null ? originLabel : getString(R.string.origin_my_location),
                        FormatUtil.formatDistance(distance)));
        ((TextView) findViewById(R.id.detail_selected_fuel)).setText(selectedFuelType.getLabel());
        int price = intent.getIntExtra(EXTRA_PRICE, 0);
        ((TextView) findViewById(R.id.detail_selected_price)).setText(price > 0
                ? FormatUtil.formatPrice(price) : getString(R.string.detail_price_unknown));
    }

    @Nullable
    @SuppressWarnings("deprecation") // getSerializableExtra(String, Class)는 API 33부터라 minSdk 24에서는 이 방법을 쓴다.
    private static StationSaving savingFrom(Intent intent) {
        Object extra = intent.getSerializableExtra(EXTRA_SAVING);
        return extra instanceof StationSaving ? (StationSaving) extra : null;
    }

    /**
     * 맨 위 카드의 주인공 숫자와 그 아래 한 줄.
     * - 이득: "약 2,583원 아낄 수 있어요"(초록, 어두운 테마는 라임), 손해: "약 1,200원 더 들어요"(빨강), 비슷: "가장 가까운 곳과 비슷해요"(회색)
     * - 아래 한 줄: "역삼셀프주유소보다 · 내 차 기준 · 연비 15km/L · 40L" (기준 주유소 이름 + 계산 기준)
     * - 이 주유소가 기준 주유소 자신이면 "가장 가까운 주유소예요" / "다른 곳과 비교하는 기준이에요 · 계산 기준"
     * 계산 결과가 없으면(즐겨찾기 탭에서 5km 밖 등) 숫자와 계산 내역 버튼을 숨긴다.
     */
    private void showHero(@Nullable StationSaving saving) {
        TextView headline = findViewById(R.id.detail_headline);
        TextView basis = findViewById(R.id.detail_headline_basis);
        if (saving == null) {
            headline.setVisibility(View.GONE);
            basis.setVisibility(View.GONE);
            savingToggle.setVisibility(View.GONE);
            return;
        }
        String calculationBasis = FormatUtil.formatCalculationBasis(saving.getCarInfo());
        if (saving.isBaseline()) {
            headline.setText(R.string.detail_hero_baseline);
            basis.setText(getString(R.string.detail_hero_basis_baseline, calculationBasis));
        } else {
            headline.setText(FormatUtil.formatSavingHeadline(saving));
            basis.setText(getString(R.string.detail_hero_basis, saving.getBaseline().getName(), calculationBasis));
        }
        headline.setTextColor(ContextCompat.getColor(this, SavingColors.heroTextColorOf(saving.getLevel())));
        headline.setVisibility(View.VISIBLE);
        basis.setVisibility(View.VISIBLE);
        savingToggle.setVisibility(View.VISIBLE);
    }

    /** "계산 내역 보기" / "계산 내역 접기" */
    private void renderSavingExpanded() {
        savingSection.setVisibility(savingExpanded ? View.VISIBLE : View.GONE);
        savingToggle.setText(savingExpanded ? R.string.detail_saving_collapse : R.string.detail_saving_expand);
        savingToggle.setIconResource(savingExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
    }

    /**
     * 예상 이득 계산 내역. 계산 결과가 없으면 구역을 숨긴다. 펼쳤을 때만 보인다.
     * 금액은 계산할 때 실수로 들고 있고, 여기서 보여줄 때만 원 단위로 반올림한다.
     */
    private void showSaving(@Nullable StationSaving saving) {
        if (saving == null) {
            savingSection.setVisibility(View.GONE);
            return;
        }
        GasStation station = saving.getStation();
        CarInfo carInfo = saving.getCarInfo();
        String price = FormatUtil.formatPrice(station.getPrice());

        ((TextView) findViewById(R.id.detail_saving_basis)).setText(FormatUtil.formatCalculationBasis(carInfo));
        findViewById(R.id.detail_saving_is_baseline).setVisibility(saving.isBaseline() ? View.VISIBLE : View.GONE);

        LinearLayout rows = findViewById(R.id.detail_saving_rows);
        rows.removeAllViews();

        if (!saving.isBaseline()) {
            GasStation baseline = saving.getBaseline();
            addSavingRow(rows, R.string.detail_saving_baseline, baseline.getName(),
                    getString(R.string.detail_saving_baseline_note,
                            FormatUtil.formatDistance(baseline.getDistance()),
                            FormatUtil.formatPrice(baseline.getPrice()),
                            FormatUtil.formatWon(saving.getBaselineTotalCost())), null);
        }

        String amount = FormatUtil.formatFuelAmount(carInfo.getFuelAmount());
        String fuelNote = saving.getDiscountAmount() > 0
                ? getString(R.string.detail_saving_fuel_cost_note_discount, price, amount,
                        FormatUtil.formatWon(saving.getDiscountAmount()))
                : getString(R.string.detail_saving_fuel_cost_note, price, amount);
        addSavingRow(rows, R.string.detail_saving_fuel_cost, FormatUtil.formatWon(saving.getFuelCost()),
                fuelNote, null);

        showDiscountRow(rows, saving);

        String roadDistance = FormatUtil.formatDistance(saving.getRoadDistance());
        addSavingRow(rows, R.string.detail_saving_road_distance,
                getString(R.string.detail_saving_approx, roadDistance),
                getString(R.string.detail_saving_road_distance_note,
                        FormatUtil.formatDistance(saving.getStraightDistance())), null);
        addSavingRow(rows, R.string.detail_saving_travel_cost,
                getString(R.string.detail_saving_approx, FormatUtil.formatWon(saving.getTravelCost())),
                getString(R.string.detail_saving_travel_cost_note, roadDistance,
                        FormatUtil.formatFuelEfficiency(carInfo.getFuelEfficiencyTenths()), price), null);
        TextView total = addSavingRow(rows, R.string.detail_saving_total,
                getString(R.string.detail_saving_approx, FormatUtil.formatWon(saving.getTotalCost())),
                getString(R.string.detail_saving_total_note), null);
        AppFonts.setBold(total, true);

        if (saving.isBaseline()) {
            addSavingRow(rows, R.string.detail_saving_difference,
                    getString(R.string.detail_saving_difference_baseline), null, null);
        } else {
            TextView difference = addSavingRow(rows, R.string.detail_saving_difference,
                    FormatUtil.formatSaving(saving),
                    getString(R.string.detail_saving_difference_note,
                            FormatUtil.formatWon(saving.getBaselineTotalCost()),
                            FormatUtil.formatWon(saving.getTotalCost())), null);
            AppFonts.setBold(difference, true);
            difference.setTextColor(ContextCompat.getColor(this, SavingColors.textColorOf(saving.getLevel())));
        }
        renderSavingExpanded();
    }

    /**
     * 할인액 줄. 내 브랜드 설정의 할인을 보여주고, 할인이 없으면 "할인 없음".
     * 예: "리터당 100원 · 할인 3,000원 (1회 한도 3,000원 적용)", "리터당 100원 · 할인 4,000원 (한도 미적용)"
     * 1회 할인 한도를 비워 뒀으면 "한도 미적용"을 쓰고, 그 상태에서 할인액이 5,000원 이상이면 주의 문구를 붙인다.
     */
    private void showDiscountRow(LinearLayout rows, StationSaving saving) {
        DiscountRule rule = saving.getDiscountRule();
        if (!rule.hasDiscount()) {
            addSavingRow(rows, R.string.detail_saving_discount,
                    getString(R.string.detail_saving_discount_none), null, null);
            return;
        }
        String perLiter = FormatUtil.formatPrice(rule.getPerLiter());
        String discount = FormatUtil.formatWon(saving.getDiscountAmount());
        String note;
        String warning = null;
        Integer cap = rule.getCap();
        if (cap == null) {
            note = getString(R.string.detail_saving_discount_no_cap, perLiter, discount);
            if (saving.getDiscountAmount() >= NO_CAP_WARNING_DISCOUNT) {
                warning = getString(R.string.detail_saving_discount_no_cap_warning);
            }
        } else if (saving.isDiscountCapApplied()) {
            note = getString(R.string.detail_saving_discount_cap_applied, perLiter, discount,
                    FormatUtil.formatPrice(cap));
        } else {
            note = getString(R.string.detail_saving_discount_cap_not_reached, perLiter, discount,
                    FormatUtil.formatPrice(cap));
        }
        addSavingRow(rows, R.string.detail_saving_discount,
                getString(R.string.detail_saving_discount_value, FormatUtil.formatWon(saving.getDiscountAmount())),
                note, warning);
    }

    /** 계산 내역 한 줄을 넣고 금액 TextView를 돌려준다. note, warning이 없으면 그 줄을 숨긴다. */
    private TextView addSavingRow(LinearLayout rows, @StringRes int label, String value,
                                  @Nullable String note, @Nullable String warning) {
        View row = getLayoutInflater().inflate(R.layout.item_saving_row, rows, false);
        ((TextView) row.findViewById(R.id.saving_row_label)).setText(label);
        TextView valueText = row.findViewById(R.id.saving_row_value);
        valueText.setText(value);
        setOptionalText(row.findViewById(R.id.saving_row_note), note);
        setOptionalText(row.findViewById(R.id.saving_row_warning), warning);
        rows.addView(row);
        return valueText;
    }

    private static void setOptionalText(TextView view, @Nullable String text) {
        view.setText(text);
        view.setVisibility(text != null ? View.VISIBLE : View.GONE);
    }

    /** 불러오는 중 → 오류 순서로 확인한다. 상세 정보가 있으면 안내를 숨긴다. */
    private void renderStatus() {
        boolean loading = Boolean.TRUE.equals(viewModel.getLoading().getValue());
        StationError error = viewModel.getError().getValue();
        boolean hasDetail = viewModel.getDetail().getValue() != null;

        @StringRes int message = 0;
        if (loading) {
            message = R.string.detail_loading;
        } else if (error != null && !hasDetail) {
            if (error == StationError.NETWORK) {
                message = R.string.detail_error_network;
            } else if (error == StationError.DAILY_LIMIT) {
                message = R.string.detail_error_daily_limit;
            } else if (error == StationError.NOT_FOUND) {
                message = R.string.detail_error_not_found;
            } else if (error == StationError.API_KEY) {
                message = R.string.opinet_key_missing;
            } else {
                message = R.string.detail_error_opinet;
            }
        }
        if (message == 0) {
            status.setVisibility(View.GONE);
            return;
        }
        statusText.setText(message);
        statusProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        // 하루 한도에 도달했거나 API 키가 없으면 다시 시도해도 같은 결과다.
        retryButton.setVisibility(loading || error == StationError.DAILY_LIMIT || error == StationError.API_KEY
                ? View.GONE : View.VISIBLE);
        status.setVisibility(View.VISIBLE);
    }

    private void showDetail(StationDetail detail) {
        String address = detail.getAddress();
        addressText.setText(address != null ? address : getString(R.string.detail_address_empty));

        String phone = detail.getPhone();
        if (phone != null) {
            phoneButton.setText(phone);
            phoneButton.setContentDescription(getString(R.string.detail_phone_description, phone));
            phoneButton.setOnClickListener(v -> openDialer(phone));
            phoneSection.setVisibility(View.VISIBLE);
        } else {
            phoneSection.setVisibility(View.GONE);
        }

        showPrices(detail.getPrices());
        showFacilities(detail);
        content.setVisibility(View.VISIBLE);
    }

    /** 파는 유종만 한 줄씩. 지도 탭에서 고른 유종은 강조한다. */
    private void showPrices(List<FuelPrice> prices) {
        priceRows.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (FuelPrice price : prices) {
            View row = inflater.inflate(R.layout.item_fuel_price, priceRows, false);
            boolean selected = price.getFuelType() == selectedFuelType;
            row.setActivated(selected);
            ((TextView) row.findViewById(R.id.fuel_label)).setText(price.getFuelType().getLabel());
            row.findViewById(R.id.fuel_selected).setVisibility(selected ? View.VISIBLE : View.GONE);
            TextView priceText = row.findViewById(R.id.fuel_price);
            priceText.setText(FormatUtil.formatPrice(price.getPrice()));
            AppFonts.setBold(priceText, selected);

            TextView tradeTime = row.findViewById(R.id.fuel_trade_time);
            String time = FormatUtil.formatTradeTime(price.getTradeDate(), price.getTradeTime());
            if (time != null) {
                tradeTime.setText(getString(R.string.detail_trade_time, time));
                tradeTime.setVisibility(View.VISIBLE);
            } else {
                tradeTime.setVisibility(View.GONE);
            }
            priceRows.addView(row);
        }
        pricesEmpty.setVisibility(prices.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 있는 부가시설만 칩으로. 셀프 여부를 상호로 추정했으면 "셀프 (추정)"과 안내 문구를 함께 보여준다. */
    private void showFacilities(StationDetail detail) {
        facilityGroup.removeAllViews();
        boolean selfEstimated = detail.isSelfService() && detail.isSelfServiceEstimated();
        if (detail.isSelfService()) {
            addFacilityChip(selfEstimated ? R.string.facility_self_estimated : R.string.facility_self);
        }
        if (detail.hasCarWash()) {
            addFacilityChip(R.string.facility_car_wash);
        }
        if (detail.hasConvenienceStore()) {
            addFacilityChip(R.string.facility_convenience_store);
        }
        if (detail.hasMaintenance()) {
            addFacilityChip(R.string.facility_maintenance);
        }
        if (detail.isQualityCertified()) {
            addFacilityChip(R.string.facility_quality_certified);
        }
        boolean empty = facilityGroup.getChildCount() == 0;
        facilityGroup.setVisibility(empty ? View.GONE : View.VISIBLE);
        facilitiesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        selfNote.setVisibility(selfEstimated ? View.VISIBLE : View.GONE);
    }

    /** 누를 수 없는 표시용 칩 */
    private void addFacilityChip(@StringRes int label) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setFocusable(false);
        chip.setEnsureMinTouchTargetSize(false);
        facilityGroup.addView(chip);
    }

    /**
     * 평균가 대비 구역. 지금 고른 유종의 표시 가격(할인 전)과 전국·지역 평균의 차이, 내 주유량 환산.
     * 불러오는 중, 못 불러옴(한도 도달 / 연결 없음 / 그 밖), 팔지 않는 유종은 짧은 안내 한 줄로 바꾼다.
     */
    private void renderAverageSection(SectionState<AverageComparison> state) {
        averageRows.removeAllViews();
        averageBasis.setVisibility(View.GONE);
        averageNote.setVisibility(View.GONE);

        String message = null;
        if (state.getStatus() == SectionState.Status.LOADING) {
            message = getString(R.string.detail_average_loading);
        } else if (state.getStatus() == SectionState.Status.ERROR) {
            message = getString(errorMessage(state.getError(), R.string.detail_average_error_daily_limit,
                    R.string.detail_average_error_network, R.string.detail_average_error));
        } else {
            AverageComparison comparison = state.getData();
            if (comparison == null || comparison.getStatus() == AverageComparison.Status.NO_PRICE) {
                message = getString(R.string.detail_average_no_price);
            } else if (comparison.getStatus() == AverageComparison.Status.NOT_SOLD) {
                message = getString(R.string.detail_average_not_sold);
            } else if (comparison.getStatus() == AverageComparison.Status.NO_AVERAGE) {
                message = getString(R.string.detail_average_no_average);
            } else {
                showAverageLines(comparison);
            }
        }
        averageStatus.setText(message);
        averageStatus.setVisibility(message != null ? View.VISIBLE : View.GONE);
    }

    private void showAverageLines(AverageComparison comparison) {
        averageBasis.setText(getString(R.string.detail_average_basis, comparison.getFuelType().getLabel(),
                FormatUtil.formatPrice(comparison.getStationPrice())));
        averageBasis.setVisibility(View.VISIBLE);
        LayoutInflater inflater = getLayoutInflater();
        for (AverageComparison.Line line : comparison.getLines()) {
            View row = inflater.inflate(R.layout.item_average_row, averageRows, false);
            TextView text = row.findViewById(R.id.average_row_text);
            text.setText(FormatUtil.formatAverageComparison(line));
            if (line.getDifferencePerLiter() < 0) {
                text.setTextColor(ContextCompat.getColor(this, R.color.saving_gain_text));
            } else if (line.getDifferencePerLiter() > 0) {
                text.setTextColor(ContextCompat.getColor(this, R.color.saving_loss_text));
            }
            setOptionalText(row.findViewById(R.id.average_row_note), FormatUtil.formatAverageForFuelAmount(line));
            averageRows.addView(row);
        }
        averageNote.setVisibility(View.VISIBLE);
    }

    /**
     * 7일 가격 추이 구역. 그래프 위에 유종, 아래에 지난 변화 한 줄 (예측 문구는 쓰지 않는다).
     * 점이 2개 미만이거나 못 불러오면 그래프 대신 안내 문구를 보여준다.
     */
    private void renderTrendSection(SectionState<List<DailyAveragePrice>> state) {
        trendHeading.setText(getString(R.string.detail_trend_heading, selectedFuelType.getLabel()));
        List<DailyAveragePrice> points = state.getData();
        PriceTrend.Summary summary = points != null ? PriceTrend.summarize(points) : null;

        String message = null;
        if (state.getStatus() == SectionState.Status.LOADING) {
            message = getString(R.string.detail_trend_loading);
        } else if (state.getStatus() == SectionState.Status.ERROR) {
            message = getString(errorMessage(state.getError(), R.string.detail_trend_error_daily_limit,
                    R.string.detail_trend_error_network, R.string.detail_trend_error));
        } else if (summary == null) {
            message = getString(R.string.detail_trend_not_enough);
        }

        if (message != null) {
            trendChart.setVisibility(View.GONE);
            trendSummary.setVisibility(View.GONE);
            trendStatus.setText(message);
            trendStatus.setVisibility(View.VISIBLE);
            return;
        }
        trendChart.setPoints(points);
        trendChart.setContentDescription(FormatUtil.formatTrendDescription(selectedFuelType, summary));
        trendChart.setVisibility(View.VISIBLE);
        trendSummary.setText(FormatUtil.formatTrendChange(summary));
        trendSummary.setVisibility(View.VISIBLE);
        trendStatus.setVisibility(View.GONE);
    }

    /** 평균가·추이 안내: 한도 도달, 연결 없음, API 키 없음, 그 밖에는 "가져오지 못함" */
    @StringRes
    private static int errorMessage(@Nullable StationError error, @StringRes int dailyLimit,
                                    @StringRes int network, @StringRes int other) {
        if (error == StationError.DAILY_LIMIT) {
            return dailyLimit;
        }
        if (error == StationError.NETWORK) {
            return network;
        }
        if (error == StationError.API_KEY) {
            return R.string.opinet_key_missing_section;
        }
        return other;
    }

    /** 전화 앱의 다이얼 화면만 연다 (전화 권한이 필요 없다). */
    private void openDialer(String phone) {
        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null));
        try {
            startActivity(dial);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "다이얼 화면을 열 수 없습니다.", e);
            Toast.makeText(this, R.string.detail_dial_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
