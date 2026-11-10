package com.example.findcheapgas.ui.place;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.Place;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SearchError;
import com.example.findcheapgas.ui.map.SearchResultAdapter;
import com.example.findcheapgas.viewmodel.PlaceSettingsViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * 집·회사 위치 검색 시트. 지도 탭과 같은 카카오 로컬 장소 검색(키워드 → 결과가 없으면 주소)으로 찾고,
 * 결과를 고르면 그 종류(집 / 회사)로 저장하고 닫힌다. 상태는 PlaceSettingsActivity 범위 ViewModel에 있다.
 * 입력 중에는 검색하지 않고 검색 버튼이나 키보드의 검색 키를 눌렀을 때만 검색한다.
 */
public class PlaceSearchSheet extends BottomSheetDialogFragment {

    public static final String TAG = "PlaceSearchSheet";

    private static final String ARG_KIND = "kind";

    private PlaceSettingsViewModel viewModel;
    private SavedPlace.Kind kind;
    private SearchResultAdapter adapter;

    private EditText input;
    private View status;
    private View statusProgress;
    private TextView statusText;
    private View retryButton;
    private RecyclerView list;

    public static PlaceSearchSheet newInstance(SavedPlace.Kind kind) {
        PlaceSearchSheet sheet = new PlaceSearchSheet();
        Bundle args = new Bundle();
        args.putString(ARG_KIND, kind.name());
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_place_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(PlaceSettingsViewModel.class);
        kind = SavedPlace.Kind.valueOf(requireArguments().getString(ARG_KIND, SavedPlace.Kind.HOME.name()));
        if (savedInstanceState == null) {
            viewModel.clearSearch();
        }

        TextView title = view.findViewById(R.id.place_search_title);
        title.setText(getString(R.string.place_search_title, getString(kind == SavedPlace.Kind.HOME
                ? R.string.place_kind_home : R.string.place_kind_work)));
        input = view.findViewById(R.id.place_search_input);
        status = view.findViewById(R.id.place_search_status);
        statusProgress = view.findViewById(R.id.place_search_progress);
        statusText = view.findViewById(R.id.place_search_status_text);
        retryButton = view.findViewById(R.id.place_search_retry);
        list = view.findViewById(R.id.place_search_list);

        adapter = new SearchResultAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        view.findViewById(R.id.place_search_button).setOnClickListener(v -> submit());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submit();
                return true;
            }
            return false;
        });
        retryButton.setOnClickListener(v -> viewModel.retry());

        viewModel.getStatus().observe(getViewLifecycleOwner(), s -> render());
        viewModel.getResults().observe(getViewLifecycleOwner(), results -> render());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        adapter = null;
    }

    private void submit() {
        if (!viewModel.search(input.getText().toString())) {
            Toast.makeText(requireContext(), R.string.search_blank, Toast.LENGTH_SHORT).show();
        }
    }

    private void render() {
        if (adapter == null) {
            return;
        }
        PlaceSettingsViewModel.Status current = viewModel.getStatus().getValue();
        List<SearchResultAdapter.Row> rows = new ArrayList<>();
        if (current == PlaceSettingsViewModel.Status.RESULTS) {
            List<Place> places = viewModel.getResults().getValue();
            if (places != null) {
                for (Place place : places) {
                    String address = place.getAddress() != null ? place.getAddress() : "";
                    String category = place.getCategory() != null ? place.getCategory() : "";
                    rows.add(new SearchResultAdapter.Row(place.getName(), category, address,
                            () -> onChosen(place),
                            getString(R.string.place_search_result_description, place.getName(), category,
                                    address)));
                }
            }
        }
        adapter.submit(rows);
        list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        renderStatus(current);
    }

    private void renderStatus(PlaceSettingsViewModel.Status current) {
        String message = null;
        boolean showProgress = false;
        boolean showRetry = false;
        if (current == PlaceSettingsViewModel.Status.LOADING) {
            message = getString(R.string.search_loading);
            showProgress = true;
        } else if (current == PlaceSettingsViewModel.Status.EMPTY) {
            message = getString(R.string.search_empty_place);
        } else if (current == PlaceSettingsViewModel.Status.ERROR) {
            SearchError error = viewModel.getError().getValue();
            message = getString(errorMessage(error));
            // 키가 틀렸으면 다시 시도해도 같은 결과다.
            showRetry = error != SearchError.KAKAO_AUTH;
        }
        if (message == null) {
            status.setVisibility(View.GONE);
            return;
        }
        statusText.setText(message);
        statusProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(showRetry ? View.VISIBLE : View.GONE);
        status.setVisibility(View.VISIBLE);
    }

    /** 장소 검색은 카카오만 부르므로 오피넷 오류는 나오지 않는다. */
    @StringRes
    private static int errorMessage(@Nullable SearchError error) {
        if (error == SearchError.KAKAO_AUTH) {
            return R.string.search_error_kakao_auth;
        }
        if (error == SearchError.KAKAO) {
            return R.string.search_error_kakao;
        }
        return R.string.search_error_network;
    }

    private void onChosen(Place place) {
        viewModel.save(kind, place);
        dismiss();
    }
}
