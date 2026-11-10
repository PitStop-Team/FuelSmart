package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.data.repository.BrandSettingsRepository;
import com.example.findcheapgas.data.repository.PlaceSearchRepository;
import com.example.findcheapgas.data.repository.StationSearchRepository;
import com.example.findcheapgas.model.Place;
import com.example.findcheapgas.model.SearchError;
import com.example.findcheapgas.model.StationSearchResult;
import com.example.findcheapgas.util.StationSearchResults;

import java.util.Collections;
import java.util.List;

/**
 * 지도 탭 검색창의 상태. 지도 탭(MapFragment)과 검색 결과 시트가 함께 쓰므로 화면을 돌리거나 다른 탭에 갔다 와도
 * 검색 결과가 남아 있다. 검색은 사용자가 검색 버튼이나 키보드의 검색 키를 눌렀을 때만 한다 (자동완성 없음).
 * - 장소: 카카오 로컬 API (키워드 → 결과가 없으면 주소)
 * - 주유소 이름: 오피넷 상호 검색. 두 글자 이상일 때만, 같은 검색어는 30분 캐시. 숨긴 브랜드는 빼고 최대 30곳
 * 같은 검색을 연달아 누르면 한 번만 요청한다: 진행 중이면 무시하고, 이미 받은 결과가 있으면 그 결과를 다시 보여준다.
 * 결과를 골랐을 때 조회 위치를 바꾸는 일은 MapViewModel이 한다.
 */
public class SearchViewModel extends AndroidViewModel {

    public enum Mode {
        PLACE,    // 장소 검색
        STATION   // 주유소 이름 검색
    }

    public enum Status {
        IDLE,     // 아직 검색하지 않음
        LOADING,
        RESULTS,
        EMPTY,    // 결과 없음 (주유소 이름 검색이면 숨긴 브랜드를 뺀 뒤 없음 포함)
        ERROR
    }

    /** 검색 버튼을 눌렀을 때 결과 */
    public enum SubmitResult {
        STARTED,    // 검색을 시작했거나 같은 검색의 결과를 다시 보여줌 → 결과 시트를 연다
        BLANK,      // 검색어가 비었음
        TOO_SHORT   // 주유소 이름 검색어가 두 글자보다 짧음
    }

    private final PlaceSearchRepository placeRepository = new PlaceSearchRepository();
    private final StationSearchRepository stationRepository;
    private final BrandSettingsRepository brandSettingsRepository;

    private final MutableLiveData<Mode> mode = new MutableLiveData<>(Mode.PLACE);
    private final MutableLiveData<Status> status = new MutableLiveData<>(Status.IDLE);
    // 마지막으로 검색한 검색어와 방식 (결과 시트 제목)
    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<Mode> resultMode = new MutableLiveData<>(Mode.PLACE);
    private final MutableLiveData<List<Place>> places = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<StationSearchResult>> stations =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<SearchError> error = new MutableLiveData<>();

    // 숨긴 브랜드를 빼기 전 주유소 이름 검색 결과. 숨긴 브랜드 설정이 바뀌어도 다시 거를 수 있게 둔다.
    private List<StationSearchResult> rawStations = Collections.emptyList();
    // 마지막 검색의 방식 + 정리한 검색어. 같은 검색인지 비교한다.
    private String lastKey;
    // 검색이 겹치면 마지막 요청의 결과만 반영한다.
    private int requestId = 0;

    public SearchViewModel(@NonNull Application application) {
        super(application);
        stationRepository = new StationSearchRepository(application);
        brandSettingsRepository = BrandSettingsRepository.getInstance(application);
    }

    public LiveData<Mode> getMode() {
        return mode;
    }

    public LiveData<Status> getStatus() {
        return status;
    }

    public LiveData<String> getQuery() {
        return query;
    }

    public LiveData<Mode> getResultMode() {
        return resultMode;
    }

    public LiveData<List<Place>> getPlaces() {
        return places;
    }

    /** 숨긴 브랜드를 빼고 최대 30곳 */
    public LiveData<List<StationSearchResult>> getStations() {
        return stations;
    }

    /** 오류가 없으면 null */
    public LiveData<SearchError> getError() {
        return error;
    }

    /** 주유소 이름 검색 결과가 있었지만 모두 숨긴 브랜드여서 비었는지 */
    public boolean isAllHidden() {
        return !rawStations.isEmpty() && stations.getValue() != null && stations.getValue().isEmpty();
    }

    /** 숨긴 브랜드를 뺀 결과가 30곳보다 많아 잘렸는지 */
    public boolean isStationResultTruncated() {
        return StationSearchResults.isTruncated(rawStations, brandSettingsRepository.getCurrent());
    }

    public void setMode(Mode newMode) {
        if (newMode != mode.getValue()) {
            mode.setValue(newMode);
        }
    }

    /** 검색 버튼, 키보드의 검색 키 */
    public SubmitResult submit(String text) {
        Mode currentMode = mode.getValue();
        String normalized = StationSearchRepository.normalize(text);
        if (normalized.isEmpty()) {
            return SubmitResult.BLANK;
        }
        if (currentMode == Mode.STATION && !StationSearchRepository.isSearchable(normalized)) {
            return SubmitResult.TOO_SHORT;
        }

        String key = currentMode + "|" + normalized;
        if (key.equals(lastKey)) {
            Status current = status.getValue();
            if (current == Status.LOADING) {
                return SubmitResult.STARTED; // 같은 검색이 진행 중이면 요청하지 않는다.
            }
            if (current == Status.RESULTS || current == Status.EMPTY) {
                // 받은 결과를 다시 보여준다. 숨긴 브랜드 설정이 그사이 바뀌었을 수 있어 다시 거른다.
                if (currentMode == Mode.STATION) {
                    showStations(rawStations);
                }
                return SubmitResult.STARTED;
            }
            // 오류가 났던 검색은 다시 요청한다.
        }

        lastKey = key;
        int id = ++requestId;
        query.setValue(normalized);
        resultMode.setValue(currentMode);
        error.setValue(null);
        places.setValue(Collections.emptyList());
        stations.setValue(Collections.emptyList());
        rawStations = Collections.emptyList();
        status.setValue(Status.LOADING);

        if (currentMode == Mode.PLACE) {
            placeRepository.search(normalized, new PlaceSearchRepository.Callback() {
                @Override
                public void onSuccess(List<Place> result) {
                    if (id != requestId) {
                        return;
                    }
                    places.setValue(result);
                    status.setValue(result.isEmpty() ? Status.EMPTY : Status.RESULTS);
                }

                @Override
                public void onError(SearchError searchError) {
                    onSearchError(id, searchError);
                }
            });
        } else {
            stationRepository.search(normalized, new StationSearchRepository.Callback() {
                @Override
                public void onSuccess(List<StationSearchResult> result) {
                    if (id != requestId) {
                        return;
                    }
                    rawStations = result;
                    showStations(result);
                }

                @Override
                public void onError(SearchError searchError) {
                    onSearchError(id, searchError);
                }
            });
        }
        return SubmitResult.STARTED;
    }

    /** 결과 시트의 다시 시도 버튼 */
    public void retry() {
        String last = query.getValue();
        Mode lastMode = resultMode.getValue();
        if (last == null || last.isEmpty() || lastMode == null) {
            return;
        }
        mode.setValue(lastMode);
        submit(last);
    }

    private void showStations(List<StationSearchResult> all) {
        List<StationSearchResult> visible = StationSearchResults.visible(all, brandSettingsRepository.getCurrent());
        stations.setValue(visible);
        status.setValue(visible.isEmpty() ? Status.EMPTY : Status.RESULTS);
    }

    private void onSearchError(int id, SearchError searchError) {
        if (id != requestId) {
            return;
        }
        error.setValue(searchError);
        status.setValue(Status.ERROR);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        placeRepository.shutdown();
        stationRepository.shutdown();
    }
}
