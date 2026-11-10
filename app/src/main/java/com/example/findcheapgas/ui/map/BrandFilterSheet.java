package com.example.findcheapgas.ui.map;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.Brand;
import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.BrandSetting;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.viewmodel.MapViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.function.Function;

/**
 * 브랜드 빠른 필터 시트. 지도 탭(MapFragment)의 MapViewModel을 함께 쓴다.
 * 체크할 때마다 바로 적용되고(API 호출 없음), 뒤의 지도와 목록이 바로 바뀐다.
 * - 전체: 다른 선택을 모두 지운다.
 * - 할인 브랜드만 / 선호 브랜드만: 해당 브랜드가 없으면 비활성화하고 이유를 보여준다.
 * - 브랜드별 체크: 숨긴 브랜드는 나오지 않는다.
 * 지금 선택은 저장된 값을 지금 브랜드 설정에 맞게 정리한 것(BrandFilter.effective)을 보여주고, 그 값에서 바꾼다.
 */
public class BrandFilterSheet extends BottomSheetDialogFragment {

    public static final String TAG = "BrandFilterSheet";

    private MapViewModel viewModel;
    private LinearLayout options;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_brand_filter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // MapFragment와 같은 Activity 범위 ViewModel을 써야 고른 필터가 지도 목록에 바로 반영된다.
        viewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
        options = view.findViewById(R.id.brand_filter_options);
        viewModel.getBrandFilter().observe(getViewLifecycleOwner(), filter -> render());
        viewModel.getBrandSettings().observe(getViewLifecycleOwner(), settings -> render());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        options = null;
    }

    private void render() {
        BrandSettings settings = viewModel.getBrandSettings().getValue();
        BrandFilter stored = viewModel.getBrandFilter().getValue();
        if (options == null || settings == null || stored == null) {
            return;
        }
        BrandFilter filter = stored.effective(settings);
        options.removeAllViews();

        addOption(getString(R.string.brand_filter_all), filter.isAll(), true, checked -> {
            // 전체는 끌 수 없다. 이미 전체면 체크 상태로 되돌린다.
            render();
            return checked ? BrandFilter.ALL : null;
        });

        boolean hasDiscount = settings.discountCount() > 0;
        addOption(getString(R.string.brand_filter_discount_only), filter.isDiscountOnly(), hasDiscount,
                filter::withDiscountOnly);
        if (!hasDiscount) {
            addNote(R.string.brand_filter_discount_disabled);
        }

        boolean hasPreferred = settings.preferredCount() > 0;
        addOption(getString(R.string.brand_filter_preferred_only), filter.isPreferredOnly(), hasPreferred,
                filter::withPreferredOnly);
        if (!hasPreferred) {
            addNote(R.string.brand_filter_preferred_disabled);
        }

        getLayoutInflater().inflate(R.layout.item_brand_filter_divider, options, true);

        for (String code : Brand.codes()) {
            BrandSetting setting = settings.get(code);
            if (setting.isHidden()) {
                continue;
            }
            addOption(brandLabel(code, setting), filter.contains(code), true,
                    checked -> filter.withBrand(code, checked));
        }

        int hidden = settings.hiddenCount();
        if (hidden > 0) {
            TextView note = addNote(0);
            note.setText(getString(R.string.brand_filter_hidden_note, hidden));
        }
    }

    /** "SK에너지", 선호면 "★ SK에너지", 할인이 있으면 " · 리터당 100원 할인"을 붙인다. */
    private String brandLabel(String code, BrandSetting setting) {
        StringBuilder label = new StringBuilder();
        if (setting.isPreferred()) {
            label.append("★ ");
        }
        label.append(Brand.nameOf(code));
        if (setting.hasDiscount()) {
            label.append(getString(R.string.brand_filter_discount_suffix,
                    FormatUtil.formatPrice(setting.getDiscountRule().getPerLiter())));
        }
        return label.toString();
    }

    /** onToggle은 새 체크 상태를 받아 새 필터를 돌려준다. null이면 바꾸지 않는다. */
    private void addOption(String label, boolean checked, boolean enabled, Function<Boolean, BrandFilter> onToggle) {
        CheckBox box = (CheckBox) getLayoutInflater().inflate(R.layout.item_brand_filter_option, options, false);
        box.setText(label);
        box.setChecked(checked);
        box.setEnabled(enabled);
        // 클릭으로 체크 상태가 먼저 바뀐 뒤에 불린다. 코드에서 setChecked할 때는 불리지 않는다.
        box.setOnClickListener(v -> {
            BrandFilter newFilter = onToggle.apply(box.isChecked());
            if (newFilter != null) {
                viewModel.setBrandFilter(newFilter);
            }
        });
        options.addView(box);
    }

    private TextView addNote(@StringRes int text) {
        TextView note = (TextView) getLayoutInflater().inflate(R.layout.item_brand_filter_note, options, false);
        if (text != 0) {
            note.setText(text);
        }
        options.addView(note);
        return note;
    }
}
