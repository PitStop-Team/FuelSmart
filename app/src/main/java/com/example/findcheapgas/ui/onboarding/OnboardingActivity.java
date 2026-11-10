package com.example.findcheapgas.ui.onboarding;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.findcheapgas.MainActivity;
import com.example.findcheapgas.R;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.util.CarInfoValidator;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.viewmodel.OnboardingViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 내 차 정보 입력 화면.
 * - 처음 실행: MainActivity가 로딩 화면 다음에 한 번만 연다. 저장하거나 건너뛰면 완료로 저장하고 지도 화면으로 간다.
 *   주로 넣는 유종도 여기서 고르고, 지도 탭의 유종 선택값으로 저장한다.
 * - 수정: 지도 탭의 계산 기준 문구나 설정 탭의 내 차 정보에서 연다. 같은 화면에 제목만 바뀌고,
 *   건너뛰기 대신 취소가 있고 유종 선택은 없다.
 * 저장한 값은 CarInfoRepository의 LiveData로 퍼지므로 결과를 따로 돌려주지 않는다.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final String EXTRA_EDIT_MODE = "edit_mode";

    private OnboardingViewModel viewModel;
    private boolean editMode;

    private TextInputLayout fuelEfficiencyLayout;
    private TextInputEditText fuelEfficiencyInput;
    private TextInputLayout fuelAmountLayout;
    private TextInputEditText fuelAmountInput;
    private ChipGroup fuelTypeGroup;

    /** 처음 실행할 때 */
    public static Intent createFirstRunIntent(Context context) {
        return new Intent(context, OnboardingActivity.class);
    }

    /** 지도 탭, 설정 탭에서 내 차 정보를 고칠 때 */
    public static Intent createEditIntent(Context context) {
        return new Intent(context, OnboardingActivity.class).putExtra(EXTRA_EDIT_MODE, true);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        editMode = getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false);
        viewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);

        fuelEfficiencyLayout = findViewById(R.id.input_fuel_efficiency_layout);
        fuelEfficiencyInput = findViewById(R.id.input_fuel_efficiency);
        fuelAmountLayout = findViewById(R.id.input_fuel_amount_layout);
        fuelAmountInput = findViewById(R.id.input_fuel_amount);
        fuelTypeGroup = findViewById(R.id.onboarding_fuel_type_group);
        MaterialButton primaryButton = findViewById(R.id.onboarding_primary_button);
        MaterialButton secondaryButton = findViewById(R.id.onboarding_secondary_button);

        if (editMode) {
            MaterialToolbar toolbar = findViewById(R.id.onboarding_toolbar);
            toolbar.setVisibility(View.VISIBLE);
            toolbar.setNavigationOnClickListener(v -> finish());
            // 같은 화면을 쓰고 제목만 바꾼다. 툴바 바로 아래라 위쪽 여백을 줄인다.
            TextView heading = findViewById(R.id.onboarding_heading);
            heading.setText(R.string.car_info_edit_title);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) heading.getLayoutParams();
            params.topMargin = getResources().getDimensionPixelSize(R.dimen.space_s);
            heading.setLayoutParams(params);
            ((TextView) findViewById(R.id.onboarding_description)).setText(R.string.car_info_edit_description);
            findViewById(R.id.onboarding_fuel_type_section).setVisibility(View.GONE);
            primaryButton.setText(R.string.car_info_save);
            secondaryButton.setText(R.string.car_info_cancel);
            secondaryButton.setOnClickListener(v -> finish());
        } else {
            setUpFuelTypeChips();
            primaryButton.setText(R.string.onboarding_save);
            secondaryButton.setText(R.string.onboarding_skip);
            secondaryButton.setOnClickListener(v -> finishOnboarding());
        }
        primaryButton.setOnClickListener(v -> save());

        // 회전 등으로 다시 만들어질 때는 입력칸이 스스로 글자를 복원하므로 처음에만 채운다.
        if (savedInstanceState == null) {
            fillCurrentValues(viewModel.getCurrentCarInfo());
        }

        fuelAmountInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save();
                return true;
            }
            return false;
        });

        viewModel.getFuelEfficiencyError().observe(this, error ->
                showError(fuelEfficiencyLayout, error == null ? 0 : fuelEfficiencyErrorText(error)));
        viewModel.getFuelAmountError().observe(this, error ->
                showError(fuelAmountLayout, error == null ? 0 : fuelAmountErrorText(error)));
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // 회전 뒤 입력칸 글자 복원(onRestoreInstanceState)이 끝난 다음에 붙인다.
        // 먼저 붙이면 복원하면서 글자가 바뀐 것으로 보고 남아 있던 오류 안내를 지운다.
        fuelEfficiencyInput.addTextChangedListener(new AfterTextChanged(viewModel::clearFuelEfficiencyError));
        fuelAmountInput.addTextChangedListener(new AfterTextChanged(viewModel::clearFuelAmountError));
    }

    /** 직접 입력한 값이 있으면 채운다. 기본값이면 비워 두고 입력칸의 예시(12.0, 40)만 보인다. */
    private void fillCurrentValues(CarInfo carInfo) {
        if (carInfo.isDefault()) {
            return;
        }
        fuelEfficiencyInput.setText(FormatUtil.formatFuelEfficiencyNumber(carInfo.getFuelEfficiencyTenths()));
        fuelAmountInput.setText(String.valueOf(carInfo.getFuelAmount()));
    }

    /** 유종 칩 4개. 고른 유종은 ViewModel이 들고 있어서 화면을 회전해도 그대로 남는다. */
    private void setUpFuelTypeChips() {
        FuelType selected = viewModel.getSelectedFuelType();
        // 칩 ID를 매번 새로 만들므로 시스템 상태 복원은 끄고 ViewModel 값으로 다시 고른다.
        fuelTypeGroup.setSaveFromParentEnabled(false);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (FuelType type : FuelType.values()) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_fuel_type_chip, fuelTypeGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(type.getLabel());
            fuelTypeGroup.addView(chip);
            chip.setChecked(type == selected);
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    viewModel.selectFuelType(type);
                }
            });
        }
    }

    private void save() {
        boolean saved = viewModel.saveCarInfo(textOf(fuelEfficiencyInput), textOf(fuelAmountInput));
        if (!saved) {
            // 첫 번째로 틀린 칸으로 옮긴다.
            if (viewModel.getFuelEfficiencyError().getValue() != null) {
                fuelEfficiencyInput.requestFocus();
            } else {
                fuelAmountInput.requestFocus();
            }
            return;
        }
        if (editMode) {
            finish();
        } else {
            finishOnboarding();
        }
    }

    /** 처음 실행 화면을 마치고 지도 화면으로 간다 (저장, 건너뛰기 모두). */
    private void finishOnboarding() {
        viewModel.completeOnboarding();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private static void showError(TextInputLayout layout, @StringRes int message) {
        if (message == 0) {
            layout.setError(null);
            layout.setErrorEnabled(false);
        } else {
            layout.setError(layout.getContext().getString(message));
        }
    }

    @StringRes
    private static int fuelEfficiencyErrorText(CarInfoValidator.Error error) {
        switch (error) {
            case EMPTY:
                return R.string.car_info_error_fuel_efficiency_empty;
            case OUT_OF_RANGE:
                return R.string.car_info_error_fuel_efficiency_range;
            case TOO_MANY_DECIMALS:
                return R.string.car_info_error_fuel_efficiency_decimals;
            default:
                return R.string.car_info_error_not_number;
        }
    }

    @StringRes
    private static int fuelAmountErrorText(CarInfoValidator.Error error) {
        switch (error) {
            case EMPTY:
                return R.string.car_info_error_fuel_amount_empty;
            case OUT_OF_RANGE:
                return R.string.car_info_error_fuel_amount_range;
            case NOT_INTEGER:
                return R.string.car_info_error_fuel_amount_integer;
            default:
                return R.string.car_info_error_not_number;
        }
    }

    private static String textOf(TextInputEditText input) {
        Editable text = input.getText();
        return text != null ? text.toString() : "";
    }

    /** 글자를 고치면 그 칸의 오류 안내를 지운다. */
    private static final class AfterTextChanged implements TextWatcher {
        private final Runnable action;

        AfterTextChanged(Runnable action) {
            this.action = action;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            action.run();
        }
    }
}
