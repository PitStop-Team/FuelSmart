package com.example.findcheapgas.ui.map;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.Place;
import com.example.findcheapgas.model.SearchError;
import com.example.findcheapgas.model.StationSearchResult;
import com.example.findcheapgas.util.StationSearchResults;
import com.example.findcheapgas.viewmodel.MapViewModel;
import com.example.findcheapgas.viewmodel.SearchViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * 검색 결과 시트. 지도 탭(MapFragment)의 SearchViewModel과 MapViewModel을 함께 쓴다.
 * 검색 중, 결과 없음, 오류를 안내하고, 결과를 고르면 그 위치를 조회 위치로 바꾸고 닫힌다. 뒤로 가기로도 닫힌다.
 * 결과는 SearchViewModel에 있어서 화면을 돌려도 다시 검색하지 않는다.
 */
public class SearchResultSheet extends BottomSheetDialogFragment {

    public static final String TAG = "SearchResultSheet";

    private SearchViewModel searchViewModel;
    private MapViewModel mapViewModel;
    private SearchResultAdapter adapter;

    private TextView title;
    private TextView note;
    private View status;
    private View statusProgress;
    private TextView statusText;
    private View retryButton;
    private RecyclerView list;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_search_results, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 검색 상태는 지도 탭 범위, 조회 위치는 MapFragment와 같은 Activity 범위 ViewModel을 쓴다.
        searchViewModel = new ViewModelProvider(requireParentFragment()).get(SearchViewModel.class);
        mapViewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);

        title = view.findViewById(R.id.search_results_title);
        note = view.findViewById(R.id.search_results_note);
        status = view.findViewById(R.id.search_results_status);
        statusProgress = view.findViewById(R.id.search_results_progress);
        statusText = view.findViewById(R.id.search_results_status_text);
        retryButton = view.findViewById(R.id.search_results_retry);
        list = view.findViewById(R.id.search_results_list);

        adapter = new SearchResultAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        retryButton.setOnClickListener(v -> searchViewModel.retry());

        searchViewModel.getStatus().observe(getViewLifecycleOwner(), s -> render());
        searchViewModel.getPlaces().observe(getViewLifecycleOwner(), places -> render());
        searchViewModel.getStations().observe(getViewLifecycleOwner(), stations -> render());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        adapter = null;
        list = null;
    }

    private void render() {
        if (adapter == null) {
            return;
        }
        SearchViewModel.Mode mode = searchViewModel.getResultMode().getValue();
        SearchViewModel.Status current = searchViewModel.getStatus().getValue();
        boolean stationMode = mode == SearchViewModel.Mode.STATION;

        title.setText(getString(stationMode ? R.string.search_results_title_station : R.string.search_results_title_place,
                searchViewModel.getQuery().getValue()));
        if (stationMode && current == SearchViewModel.Status.RESULTS && searchViewModel.isStationResultTruncated()) {
            note.setText(getString(R.string.search_results_truncated, StationSearchResults.MAX_RESULTS));
        } else {
            note.setText(stationMode ? R.string.search_results_note_station : R.string.search_results_note_place);
        }

        List<SearchResultAdapter.Row> rows = new ArrayList<>();
        if (current == SearchViewModel.Status.RESULTS) {
            if (stationMode) {
                for (StationSearchResult station : listOrEmpty(searchViewModel.getStations().getValue())) {
                    rows.add(new SearchResultAdapter.Row(station.getName(), station.getBrandName(),
                            station.getAddress(), () -> onStationChosen(station)));
                }
            } else {
                for (Place place : listOrEmpty(searchViewModel.getPlaces().getValue())) {
                    rows.add(new SearchResultAdapter.Row(place.getName(), place.getCategory(), place.getAddress(),
                            () -> onPlaceChosen(place)));
                }
            }
        }
        adapter.submit(rows);
        list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        renderStatus(current, stationMode);
    }

    private void renderStatus(SearchViewModel.Status current, boolean stationMode) {
        String message = null;
        boolean showProgress = false;
        boolean showRetry = false;
        if (current == SearchViewModel.Status.LOADING) {
            message = getString(R.string.search_loading);
            showProgress = true;
        } else if (current == SearchViewModel.Status.EMPTY) {
            if (!stationMode) {
                message = getString(R.string.search_empty_place);
            } else if (searchViewModel.isAllHidden()) {
                message = getString(R.string.search_empty_station_hidden);
            } else {
                message = getString(R.string.search_empty_station);
            }
        } else if (current == SearchViewModel.Status.ERROR) {
            SearchError error = searchViewModel.getError().getValue();
            message = getString(errorMessage(error));
            // 키가 틀렸거나 없거나 오늘 한도에 도달했으면 다시 시도해도 같은 결과다.
            showRetry = error != SearchError.KAKAO_AUTH && error != SearchError.DAILY_LIMIT
                    && error != SearchError.OPINET_KEY;
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

    @StringRes
    private static int errorMessage(SearchError error) {
        if (error == null) {
            return R.string.search_error_network;
        }
        switch (error) {
            case KAKAO_AUTH:
                return R.string.search_error_kakao_auth;
            case KAKAO:
                return R.string.search_error_kakao;
            case NETWORK:
                return R.string.search_error_network;
            case DAILY_LIMIT:
                return R.string.search_error_daily_limit;
            case OPINET_KEY:
                return R.string.search_error_opinet_key;
            default:
                return R.string.search_error_opinet;
        }
    }

    private void onPlaceChosen(Place place) {
        mapViewModel.selectPlace(place.getName(), place.getLatitude(), place.getLongitude());
        dismiss();
    }

    private void onStationChosen(StationSearchResult station) {
        mapViewModel.selectSearchedStation(station);
        dismiss();
    }

    private static <T> List<T> listOrEmpty(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }
}
