package com.example.findcheapgas.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.ApiUsage;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.ThemeMode;
import com.example.findcheapgas.ui.brand.BrandSettingsActivity;
import com.example.findcheapgas.ui.onboarding.OnboardingActivity;
import com.example.findcheapgas.ui.place.PlaceSettingsActivity;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.viewmodel.MapViewModel;
import com.example.findcheapgas.viewmodel.SettingsViewModel;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 설정 탭. 회색 바탕에 흰 카드로 묶은 목록이고, 한 줄은 왼쪽 이름, 오른쪽 지금 값 + 화살표(item_settings_row)다.
 * 묶음: 내 차(내 차 정보) / 할인과 브랜드(내 브랜드) / 위치(집, 회사) / 지도 기본값(기본 유종, 기본 반경) /
 * 화면(화면 테마) / 사용량(오늘 오피넷 사용량).
 * 내 차 정보를 누르면 수정 화면(OnboardingActivity 수정 모드)이, 내 브랜드를 누르면 내 브랜드 화면이,
 * 집·회사를 누르면 집·회사 위치 화면(PlaceSettingsActivity)이 열린다.
 * 값은 각 Repository의 LiveData를 보고 그리므로 수정 화면에서 저장하고 돌아오면 바로 바뀐다.
 * 기본 유종·반경은 지도 탭이 기억하는 값 그대로이고, Activity 범위의 MapViewModel로 바꿔서 지도 탭에도 바로 반영된다.
 * 오늘 오피넷 사용량은 탭이 보일 때마다 다시 읽는다.
 */
public class SettingsFragment extends Fragment {

    private SettingsViewModel viewModel;
    private MapViewModel mapViewModel;

    private SettingsRow carInfoRow;
    private TextView carInfoStatus;
    private View resetButton;
    private SettingsRow brandRow;
    private SettingsRow homeRow;
    private SettingsRow workRow;
    private SettingsRow fuelTypeRow;
    private SettingsRow radiusRow;
    private SettingsRow themeRow;
    private View apiUsageCard;
    private TextView apiUsageCount;
    private LinearProgressIndicator apiUsageProgress;
    private TextView apiUsageStatus;
    private TextView apiUsageMock;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        // 지도 탭과 같은 MapViewModel (MainActivity 범위)
        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);

        // 디버그 빌드에서만: 연료 %를 바꿔 연료 부족 제안을 시험하는 줄을 만든다. 릴리스 빌드에서는 만들지 않는다.
        if (com.example.findcheapgas.BuildConfig.DEBUG) {
            addDebugFuelSection(view);
        }

        carInfoRow = new SettingsRow(view.findViewById(R.id.settings_car_info_row), R.string.settings_car_info);
        carInfoStatus = view.findViewById(R.id.settings_car_info_status);
        resetButton = view.findViewById(R.id.settings_car_info_reset);
        brandRow = new SettingsRow(view.findViewById(R.id.settings_brand_row), R.string.settings_brand);
        homeRow = new SettingsRow(view.findViewById(R.id.settings_home_row), R.string.place_kind_home);
        workRow = new SettingsRow(view.findViewById(R.id.settings_work_row), R.string.place_kind_work);
        fuelTypeRow = new SettingsRow(view.findViewById(R.id.settings_fuel_type_row), R.string.settings_fuel_type);
        radiusRow = new SettingsRow(view.findViewById(R.id.settings_radius_row), R.string.settings_radius);
        themeRow = new SettingsRow(view.findViewById(R.id.settings_theme_row), R.string.settings_theme);

        carInfoRow.root.setOnClickListener(v ->
                startActivity(OnboardingActivity.createEditIntent(requireContext())));
        resetButton.setOnClickListener(v -> {
            viewModel.resetCarInfoToDefault();
            Snackbar.make(view, R.string.settings_car_info_reset_done, Snackbar.LENGTH_SHORT)
                    .setAnchorView(requireActivity().findViewById(R.id.bottom_nav))
                    .show();
        });
        brandRow.root.setOnClickListener(v ->
                startActivity(BrandSettingsActivity.createIntent(requireContext())));
        homeRow.root.setOnClickListener(v ->
                startActivity(PlaceSettingsActivity.createIntent(requireContext())));
        workRow.root.setOnClickListener(v ->
                startActivity(PlaceSettingsActivity.createIntent(requireContext())));
        fuelTypeRow.root.setOnClickListener(v -> showChoiceDialog(R.string.settings_fuel_type,
                FuelType.values(), mapViewModel.getFuelType().getValue(), FuelType::getLabel,
                mapViewModel::setFuelType));
        radiusRow.root.setOnClickListener(v -> showChoiceDialog(R.string.settings_radius,
                SearchRadius.values(), mapViewModel.getRadius().getValue(), SearchRadius::getLabel,
                mapViewModel::setRadius));
        themeRow.root.setOnClickListener(v -> showChoiceDialog(R.string.settings_theme,
                ThemeMode.values(), viewModel.getThemeMode().getValue(), ThemeMode::getLabel,
                viewModel::setThemeMode));

        apiUsageCard = view.findViewById(R.id.settings_api_usage_card);
        apiUsageCount = view.findViewById(R.id.settings_api_usage_count);
        apiUsageProgress = view.findViewById(R.id.settings_api_usage_progress);
        apiUsageStatus = view.findViewById(R.id.settings_api_usage_status);
        apiUsageMock = view.findViewById(R.id.settings_api_usage_mock);

        viewModel.getCarInfo().observe(getViewLifecycleOwner(), this::showCarInfo);
        viewModel.getBrandSettings().observe(getViewLifecycleOwner(), this::showBrandSummary);
        viewModel.getPlaces().observe(getViewLifecycleOwner(), this::showPlaces);
        mapViewModel.getFuelType().observe(getViewLifecycleOwner(), fuelType -> fuelTypeRow.show(fuelType.getLabel()));
        mapViewModel.getRadius().observe(getViewLifecycleOwner(), radius -> radiusRow.show(radius.getLabel()));
        viewModel.getThemeMode().observe(getViewLifecycleOwner(), mode -> themeRow.show(mode.getLabel()));
        viewModel.getApiUsage().observe(getViewLifecycleOwner(), this::showApiUsage);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 앱으로 돌아왔을 때. 탭이 숨겨져 있으면 보일 때(onHiddenChanged) 읽는다.
        if (!isHidden()) {
            viewModel.refreshApiUsage();
        }
    }

    /** 하단 탭을 바꿀 때 show/hide로 불린다. 다른 탭에서 늘어난 호출 횟수를 바로 반영한다. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && viewModel != null) {
            viewModel.refreshApiUsage();
        }
    }

    /** "오늘 12회 / 300회", 진행 막대, 250회부터 경고 색과 문구, 가짜 데이터 모드 안내 */
    private void showApiUsage(ApiUsage usage) {
        String count = FormatUtil.formatApiUsageCount(usage);
        String status = FormatUtil.formatApiUsageStatus(usage);
        apiUsageCount.setText(count);
        apiUsageProgress.setProgressCompat(usage.getProgressPercent(), false);
        boolean warning = usage.getLevel() != ApiUsage.Level.NORMAL;
        apiUsageProgress.setIndicatorColor(MaterialColors.getColor(apiUsageProgress, warning
                ? androidx.appcompat.R.attr.colorError : androidx.appcompat.R.attr.colorPrimary));
        apiUsageStatus.setText(status);
        apiUsageStatus.setVisibility(status != null ? View.VISIBLE : View.GONE);
        apiUsageMock.setVisibility(usage.isMockData() ? View.VISIBLE : View.GONE);

        StringBuilder notes = new StringBuilder();
        if (status != null) {
            notes.append(status).append(". ");
        }
        notes.append(getString(R.string.settings_api_usage_note));
        if (usage.isMockData()) {
            notes.append(". ").append(getString(R.string.settings_api_usage_mock));
        }
        apiUsageCard.setContentDescription(getString(R.string.settings_api_usage_description,
                count, usage.getLimit(), notes));
    }

    /** 하나를 고르는 창. 고르면 바로 적용하고 닫힌다. */
    private <T> void showChoiceDialog(@StringRes int title, T[] options, @Nullable T current,
                                      Function<T, String> label, Consumer<T> onSelected) {
        String[] labels = new String[options.length];
        int checked = -1;
        for (int i = 0; i < options.length; i++) {
            labels[i] = label.apply(options[i]);
            if (options[i] == current) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    onSelected.accept(options[which]);
                })
                .setNegativeButton(R.string.settings_choice_cancel, null)
                .show();
    }

    /** 집 / 회사 줄의 값: 등록한 장소 이름, 없으면 "등록 안 함" */
    private void showPlaces(@Nullable SavedPlaces places) {
        homeRow.show(placeName(places != null ? places.get(SavedPlace.Kind.HOME) : null));
        workRow.show(placeName(places != null ? places.get(SavedPlace.Kind.WORK) : null));
    }

    private String placeName(@Nullable SavedPlace place) {
        return place != null ? place.getName() : getString(R.string.place_not_registered);
    }

    /** "할인 2곳 · 선호 1곳 · 숨김 1곳", 아무것도 없으면 "설정한 브랜드 없음" */
    private void showBrandSummary(BrandSettings settings) {
        brandRow.show(FormatUtil.formatBrandSummary(settings));
    }

    /** 줄 값 "연비 15km/L · 40L", 아래 안내 "직접 입력한 값으로 계산하고 있어요", 직접 입력했을 때만 되돌리기 버튼 */
    private void showCarInfo(CarInfo carInfo) {
        String efficiency = FormatUtil.formatFuelEfficiency(carInfo.getFuelEfficiencyTenths());
        String amount = FormatUtil.formatFuelAmount(carInfo.getFuelAmount());
        @StringRes int status = carInfo.isDefault()
                ? R.string.settings_car_info_default
                : R.string.settings_car_info_user_entered;
        carInfoRow.value.setText(getString(R.string.settings_car_info_value, efficiency, amount));
        carInfoStatus.setText(status);
        carInfoRow.root.setContentDescription(getString(R.string.settings_car_info_description,
                efficiency, amount, getString(status)));
        // 이미 기본값이면 되돌릴 것이 없다.
        resetButton.setVisibility(carInfo.isDefault() ? View.GONE : View.VISIBLE);
    }

    /** 설정 카드 안의 한 줄 (item_settings_row): 이름, 지금 값 */
    private final class SettingsRow {
        final View root;
        final TextView value;
        final String title;

        SettingsRow(View root, @StringRes int title) {
            this.root = root;
            this.title = getString(title);
            value = root.findViewById(R.id.settings_row_value);
            ((TextView) root.findViewById(R.id.settings_row_title)).setText(title);
        }

        /** 값을 쓰고 접근성 설명은 "이름, 값. 누르면 바꿀 수 있어요" */
        void show(String text) {
            value.setText(text);
            root.setContentDescription(getString(R.string.settings_row_description, title, text));
        }
    }

    /** 디버그 전용. 문구는 코드에 직접 적고 리소스에 넣지 않는다. */
    private void addDebugFuelSection(@NonNull View root) {
        if (!(root instanceof android.view.ViewGroup)) {
            return;
        }
        View content = ((android.view.ViewGroup) root).getChildAt(0);
        if (!(content instanceof android.view.ViewGroup)) {
            return;
        }
        android.content.Context context = requireContext();
        com.example.findcheapgas.pleos.PleosVehicleSession session =
                com.example.findcheapgas.pleos.PleosVehicleSession.getInstance(context);

        TextView title = new TextView(context);
        title.setText("디버그: 연료 % 시뮬레이션");
        title.setPadding(0, 48, 0, 8);
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        TextView status = new TextView(context);
        status.setText("기준 25% 미만에서 제안, 35% 이상에서 재무장. 해제하면 차량 값을 따른다.");

        android.widget.LinearLayout row = new android.widget.LinearLayout(context);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        int[] values = {-1, 80, 40, 24, 10};
        for (int value : values) {
            com.google.android.material.button.MaterialButton button =
                    new com.google.android.material.button.MaterialButton(context, null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(value < 0 ? "해제" : value + "%");
            button.setOnClickListener(v -> session.setDebugFuelPercent(value < 0 ? null : value));
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(button, lp);
        }
        android.view.ViewGroup list = (android.view.ViewGroup) content;
        list.addView(title);
        list.addView(status);
        list.addView(row);
    }
}
