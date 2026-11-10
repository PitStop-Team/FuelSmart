package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.data.repository.PlaceSearchRepository;
import com.example.findcheapgas.data.repository.SavedPlaceRepository;
import com.example.findcheapgas.model.Place;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;
import com.example.findcheapgas.model.SearchError;

import java.util.Collections;
import java.util.List;

/**
 * 집·회사 위치 화면의 상태.
 * - 등록한 위치: SavedPlaceRepository의 LiveData
 * - 장소 검색: 지도 탭과 같은 카카오 로컬 검색(PlaceSearchRepository, 키워드 → 결과가 없으면 주소).
 *   입력 중에는 검색하지 않고 검색 버튼이나 키보드의 검색 키를 눌렀을 때만 검색한다.
 * - 고르면 저장하고, 지우면 실행 취소를 한 번 보여준다.
 * 검색어, 장소 이름, 주소, 좌표는 로그에 남기지 않는다.
 */
public class PlaceSettingsViewModel extends AndroidViewModel {

    public enum Status {
        IDLE,
        LOADING,
        RESULTS,
        EMPTY,
        ERROR
    }

    private final SavedPlaceRepository savedPlaceRepository;
    private final PlaceSearchRepository placeSearchRepository = new PlaceSearchRepository();

    private final MutableLiveData<Status> status = new MutableLiveData<>(Status.IDLE);
    private final MutableLiveData<List<Place>> results = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<SearchError> error = new MutableLiveData<>();
    // 방금 지운 위치 (실행 취소 안내를 한 번 보여주면 null로 되돌린다)
    private final MutableLiveData<SavedPlace> lastRemoved = new MutableLiveData<>();
    // 방금 저장한 종류 (안내를 한 번 보여주면 null로 되돌린다)
    private final MutableLiveData<SavedPlace.Kind> lastSaved = new MutableLiveData<>();

    @Nullable
    private String lastQuery;
    private int searchId = 0;

    public PlaceSettingsViewModel(@NonNull Application application) {
        super(application);
        savedPlaceRepository = SavedPlaceRepository.getInstance(application);
    }

    public LiveData<SavedPlaces> getPlaces() {
        return savedPlaceRepository.getPlaces();
    }

    public LiveData<Status> getStatus() {
        return status;
    }

    public LiveData<List<Place>> getResults() {
        return results;
    }

    public LiveData<SearchError> getError() {
        return error;
    }

    public LiveData<SavedPlace> getLastRemoved() {
        return lastRemoved;
    }

    public void onRemovedShown() {
        lastRemoved.setValue(null);
    }

    public LiveData<SavedPlace.Kind> getLastSaved() {
        return lastSaved;
    }

    public void onSavedShown() {
        lastSaved.setValue(null);
    }

    /** 검색 시트를 새로 열 때 이전 검색 결과를 지운다. */
    public void clearSearch() {
        searchId++;
        lastQuery = null;
        status.setValue(Status.IDLE);
        results.setValue(Collections.emptyList());
        error.setValue(null);
    }

    /** @return 검색했으면 true, 빈 검색어라 하지 않았으면 false */
    public boolean search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            return false;
        }
        // 같은 검색어로 이미 결과가 있거나 검색 중이면 다시 요청하지 않는다.
        if (query.equals(lastQuery) && status.getValue() != Status.ERROR) {
            return true;
        }
        lastQuery = query;
        int id = ++searchId;
        status.setValue(Status.LOADING);
        error.setValue(null);
        placeSearchRepository.search(query, new PlaceSearchRepository.Callback() {
            @Override
            public void onSuccess(List<Place> places) {
                if (id != searchId) {
                    return;
                }
                results.setValue(places);
                status.setValue(places.isEmpty() ? Status.EMPTY : Status.RESULTS);
            }

            @Override
            public void onError(SearchError searchError) {
                if (id != searchId) {
                    return;
                }
                results.setValue(Collections.emptyList());
                error.setValue(searchError);
                status.setValue(Status.ERROR);
            }
        });
        return true;
    }

    /** 오류 안내의 다시 시도 */
    public void retry() {
        String query = lastQuery;
        if (query != null) {
            lastQuery = null;
            search(query);
        }
    }

    /** 검색 결과를 골랐을 때. 같은 종류가 이미 있으면 바꾼다. */
    public void save(SavedPlace.Kind kind, Place place) {
        String address = place.getAddress();
        savedPlaceRepository.save(new SavedPlace(kind, place.getName(),
                address == null || address.trim().isEmpty() ? null : address.trim(),
                place.getLatitude(), place.getLongitude(), System.currentTimeMillis()));
        lastSaved.setValue(kind);
    }

    public void remove(SavedPlace.Kind kind) {
        savedPlaceRepository.remove(kind, removed -> {
            if (removed != null) {
                lastRemoved.setValue(removed);
            }
        });
    }

    /** 실행 취소: 지운 위치를 그대로 되돌린다. */
    public void restore(SavedPlace place) {
        savedPlaceRepository.save(place);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        placeSearchRepository.shutdown();
    }
}
