package com.example.findcheapgas.ui.brand;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.Brand;
import com.example.findcheapgas.model.BrandSetting;
import com.example.findcheapgas.model.DiscountRule;
import com.example.findcheapgas.util.BrandSettingValidator;
import com.example.findcheapgas.viewmodel.BrandSettingsViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 브랜드 한 곳의 수정 시트: 리터당 할인, 1회 할인 한도, 선호, 숨기기.
 * - 한도 칸을 비우면 회색 "한도 미적용"이 보이고 한도 없이 계산한다.
 * - 선호와 숨기기는 하나를 켜면 다른 하나가 꺼진다.
 * - 저장: 범위를 벗어난 칸이 있으면 저장하지 않고 그 칸 아래에 이유를 보여준다.
 * - 할인 지우기: 리터당 할인과 한도를 지우고 바로 저장한다 (선호, 숨기기는 화면에서 고른 값).
 * 화면을 돌려도 입력 중인 글자와 스위치는 시스템이 되살리므로, 처음 열 때만 저장된 값으로 채운다.
 */
public class BrandEditSheet extends BottomSheetDialogFragment {

    public static final String TAG = "BrandEditSheet";

    private static final String ARG_BRAND_CODE = "brand_code";

    private BrandSettingsViewModel viewModel;
    private String brandCode;

    private TextInputLayout perLiterLayout;
    private TextInputLayout capLayout;
    private EditText perLiterInput;
    private EditText capInput;
    private MaterialSwitch preferredSwitch;
    private MaterialSwitch hiddenSwitch;

    public static BrandEditSheet newInstance(String brandCode) {
        BrandEditSheet sheet = new BrandEditSheet();
        Bundle args = new Bundle();
        args.putString(ARG_BRAND_CODE, brandCode);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_brand_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(BrandSettingsViewModel.class);
        brandCode = requireArguments().getString(ARG_BRAND_CODE);
        BrandSetting setting = viewModel.getSetting(brandCode);

        ((TextView) view.findViewById(R.id.brand_edit_title)).setText(Brand.nameOf(brandCode));
        perLiterLayout = view.findViewById(R.id.brand_edit_per_liter_layout);
        capLayout = view.findViewById(R.id.brand_edit_cap_layout);
        perLiterInput = view.findViewById(R.id.brand_edit_per_liter);
        capInput = view.findViewById(R.id.brand_edit_cap);
        preferredSwitch = view.findViewById(R.id.brand_edit_preferred);
        hiddenSwitch = view.findViewById(R.id.brand_edit_hidden);
        View clearButton = view.findViewById(R.id.brand_edit_clear_discount);

        if (savedInstanceState == null) {
            DiscountRule rule = setting.getDiscountRule();
            perLiterInput.setText(rule.getPerLiter() > 0 ? String.valueOf(rule.getPerLiter()) : "");
            capInput.setText(rule.getCap() != null ? String.valueOf(rule.getCap()) : "");
            preferredSwitch.setChecked(setting.isPreferred());
            hiddenSwitch.setChecked(setting.isHidden());
        }
        DiscountRule saved = setting.getDiscountRule();
        clearButton.setVisibility(saved.hasDiscount() || saved.isCapEntered() ? View.VISIBLE : View.GONE);

        // 선호와 숨기기는 동시에 켤 수 없다.
        preferredSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                hiddenSwitch.setChecked(false);
            }
        });
        hiddenSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                preferredSwitch.setChecked(false);
            }
        });
        perLiterInput.addTextChangedListener(new ClearErrorWatcher(perLiterLayout));
        capInput.addTextChangedListener(new ClearErrorWatcher(capLayout));

        view.findViewById(R.id.brand_edit_save).setOnClickListener(v -> save());
        view.findViewById(R.id.brand_edit_cancel).setOnClickListener(v -> dismiss());
        clearButton.setOnClickListener(v -> {
            viewModel.clearDiscount(brandCode, preferredSwitch.isChecked(), hiddenSwitch.isChecked());
            dismiss();
        });
    }

    private void save() {
        BrandSettingsViewModel.SaveResult result = viewModel.save(brandCode,
                perLiterInput.getText().toString(), capInput.getText().toString(),
                preferredSwitch.isChecked(), hiddenSwitch.isChecked());
        if (result.isSaved()) {
            dismiss();
            return;
        }
        showError(perLiterLayout, result.perLiterError, R.string.brand_edit_error_per_liter_range);
        showError(capLayout, result.capError, R.string.brand_edit_error_cap_range);
    }

    private void showError(TextInputLayout layout, @Nullable BrandSettingValidator.Error error,
                           @StringRes int rangeMessage) {
        if (error == null) {
            layout.setError(null);
            return;
        }
        switch (error) {
            case OUT_OF_RANGE:
                layout.setError(getString(rangeMessage));
                break;
            case NOT_INTEGER:
                layout.setError(getString(R.string.brand_edit_error_integer));
                break;
            default:
                layout.setError(getString(R.string.car_info_error_not_number));
                break;
        }
    }

    /** 글자를 고치면 그 칸의 오류를 지운다. */
    private static final class ClearErrorWatcher implements TextWatcher {
        private final TextInputLayout layout;

        ClearErrorWatcher(TextInputLayout layout) {
            this.layout = layout;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            layout.setError(null);
        }
    }
}
