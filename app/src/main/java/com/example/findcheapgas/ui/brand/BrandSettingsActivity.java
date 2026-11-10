package com.example.findcheapgas.ui.brand;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.R;
import com.example.findcheapgas.viewmodel.BrandSettingsViewModel;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * 내 브랜드 화면. 설정 탭의 "내 브랜드"를 누르면 열린다.
 * 브랜드 목록을 보여주고, 줄을 누르면 수정 시트(BrandEditSheet)를 연다.
 * 목록은 BrandSettingsRepository의 LiveData를 보고 그리므로 시트에서 저장하면 바로 바뀐다.
 */
public class BrandSettingsActivity extends AppCompatActivity {

    public static Intent createIntent(Context context) {
        return new Intent(context, BrandSettingsActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_brand_settings);
        BrandSettingsViewModel viewModel = new ViewModelProvider(this).get(BrandSettingsViewModel.class);

        MaterialToolbar toolbar = findViewById(R.id.brand_settings_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        BrandSettingAdapter adapter = new BrandSettingAdapter(this::openEditSheet);
        RecyclerView list = findViewById(R.id.brand_settings_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        viewModel.getBrandSettings().observe(this, adapter::submit);
    }

    /** 이미 열려 있으면 다시 열지 않는다 (두 번 빨리 누른 경우). */
    private void openEditSheet(String brandCode) {
        if (getSupportFragmentManager().findFragmentByTag(BrandEditSheet.TAG) == null) {
            BrandEditSheet.newInstance(brandCode).show(getSupportFragmentManager(), BrandEditSheet.TAG);
        }
    }
}
