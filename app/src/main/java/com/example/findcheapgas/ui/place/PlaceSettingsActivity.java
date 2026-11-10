package com.example.findcheapgas.ui.place;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;
import com.example.findcheapgas.viewmodel.PlaceSettingsViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

/**
 * 집·회사 위치 화면. 설정 탭의 "집·회사 위치"나 즐겨찾기 탭 비교 카드의 "등록"을 누르면 열린다.
 * 집, 회사 각각 등록한 장소 이름과 주소(없으면 "등록 안 함")를 보여주고, 등록·바꾸기를 누르면 장소 검색 시트를 연다.
 * 지우면 실행 취소를 보여준다. 위치는 이 기기의 DB에만 저장한다.
 */
public class PlaceSettingsActivity extends AppCompatActivity {

    private static final String EXTRA_OPEN_KIND = "open_kind";

    private PlaceSettingsViewModel viewModel;
    private View root;

    public static Intent createIntent(Context context) {
        return new Intent(context, PlaceSettingsActivity.class);
    }

    /** 열자마자 그 종류의 검색 시트를 연다 (즐겨찾기 탭 비교 카드의 "등록"). */
    public static Intent createRegisterIntent(Context context, SavedPlace.Kind kind) {
        return createIntent(context).putExtra(EXTRA_OPEN_KIND, kind.name());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_settings);
        viewModel = new ViewModelProvider(this).get(PlaceSettingsViewModel.class);
        root = findViewById(R.id.place_settings_root);

        MaterialToolbar toolbar = findViewById(R.id.place_settings_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        View homeRow = findViewById(R.id.place_settings_home);
        View workRow = findViewById(R.id.place_settings_work);
        viewModel.getPlaces().observe(this, places -> {
            bindRow(homeRow, SavedPlace.Kind.HOME, places);
            bindRow(workRow, SavedPlace.Kind.WORK, places);
        });
        viewModel.getLastSaved().observe(this, kind -> {
            if (kind != null) {
                Snackbar.make(root, getString(R.string.place_saved, kindLabel(kind)), Snackbar.LENGTH_SHORT).show();
                viewModel.onSavedShown();
            }
        });
        viewModel.getLastRemoved().observe(this, this::showUndo);

        // 화면을 돌려서 다시 만들어질 때는 시트를 또 열지 않는다.
        String openKind = getIntent().getStringExtra(EXTRA_OPEN_KIND);
        if (savedInstanceState == null && openKind != null) {
            try {
                openSearch(SavedPlace.Kind.valueOf(openKind));
            } catch (IllegalArgumentException ignored) {
                // 모르는 종류면 화면만 보여준다.
            }
        }
    }

    private void bindRow(View row, SavedPlace.Kind kind, @Nullable SavedPlaces places) {
        SavedPlace place = places != null ? places.get(kind) : null;
        TextView kindText = row.findViewById(R.id.saved_place_kind);
        TextView nameText = row.findViewById(R.id.saved_place_name);
        TextView addressText = row.findViewById(R.id.saved_place_address);
        MaterialButton editButton = row.findViewById(R.id.saved_place_edit);
        MaterialButton removeButton = row.findViewById(R.id.saved_place_remove);

        String label = kindLabel(kind);
        kindText.setText(label);
        if (place == null) {
            nameText.setText(R.string.place_not_registered);
            addressText.setVisibility(View.GONE);
            editButton.setText(R.string.place_register);
            removeButton.setVisibility(View.GONE);
        } else {
            nameText.setText(place.getName());
            String address = place.getAddress();
            addressText.setText(address);
            addressText.setVisibility(address != null ? View.VISIBLE : View.GONE);
            editButton.setText(R.string.place_change);
            removeButton.setVisibility(View.VISIBLE);
        }
        editButton.setContentDescription(getString(place == null
                ? R.string.place_register_description : R.string.place_change_description, label));
        removeButton.setContentDescription(getString(R.string.place_remove_description, label));
        editButton.setOnClickListener(v -> openSearch(kind));
        removeButton.setOnClickListener(v -> viewModel.remove(kind));
    }

    /** 이미 열려 있으면 다시 열지 않는다 (두 번 빨리 누른 경우). */
    private void openSearch(SavedPlace.Kind kind) {
        if (getSupportFragmentManager().findFragmentByTag(PlaceSearchSheet.TAG) == null) {
            PlaceSearchSheet.newInstance(kind).show(getSupportFragmentManager(), PlaceSearchSheet.TAG);
        }
    }

    private void showUndo(@Nullable SavedPlace removed) {
        if (removed == null) {
            return;
        }
        Snackbar.make(root, getString(R.string.place_removed, kindLabel(removed.getKind())), Snackbar.LENGTH_LONG)
                .setAction(R.string.favorite_undo, v -> viewModel.restore(removed))
                .show();
        viewModel.onRemovedShown();
    }

    private String kindLabel(SavedPlace.Kind kind) {
        return getString(kind == SavedPlace.Kind.HOME ? R.string.place_kind_home : R.string.place_kind_work);
    }
}
