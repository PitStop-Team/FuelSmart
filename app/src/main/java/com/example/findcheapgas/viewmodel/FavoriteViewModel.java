package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.findcheapgas.data.repository.BrandSettingsRepository;
import com.example.findcheapgas.data.repository.CarInfoRepository;
import com.example.findcheapgas.data.repository.FavoriteRepository;
import com.example.findcheapgas.data.repository.StationDetailRepository;
import com.example.findcheapgas.model.AroundStations;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FavoriteItem;
import com.example.findcheapgas.model.FavoritePrice;
import com.example.findcheapgas.model.FavoriteStation;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.util.FavoriteListBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 즐겨찾기 탭의 상태.
 * - 저장한 주유소: FavoriteRepository의 LiveData (최근 저장순)
 * - 조회 위치, 유종, 기준 주유소: 지도 탭의 마지막 주변 조회 결과(AroundStations)를 화면이 넘겨준다.
 * - 가격: StationDetailRepository.fetchFavoritePrices (주변 목록 → 상세 캐시 → 조회)
 * - 예상 이득: FavoriteListBuilder로 계산한다. 내 차 정보나 브랜드 설정이 바뀌면 API 없이 다시 계산한다.
 *
 * 가격은 탭이 보일 때만 가져온다. 탭을 열 때는 하루 안의 캐시를 그대로 쓰고,
 * 당겨서 새로고침할 때만 30분이 지난 캐시를 다시 가져온다.
 */
public class FavoriteViewModel extends AndroidViewModel {

    private final FavoriteRepository favoriteRepository;
    private final StationDetailRepository detailRepository;
    private final CarInfoRepository carInfoRepository;
    private final BrandSettingsRepository brandSettingsRepository;

    private final MediatorLiveData<List<FavoriteItem>> items = new MediatorLiveData<>();
    // 당겨서 새로고침 중 (화면 위쪽 원형 표시)
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);
    // 방금 지운 즐겨찾기 (실행 취소 안내를 한 번 보여주면 null로 되돌린다)
    private final MutableLiveData<FavoriteStation> lastRemoved = new MutableLiveData<>();
    // 실행 취소했는데 그사이 20곳이 차서 되돌리지 못했을 때 한 번 true
    private final MutableLiveData<Boolean> restoreFailed = new MutableLiveData<>(false);

    // 지도 탭의 마지막 주변 조회 결과. 아직 없으면 null
    @Nullable
    private AroundStations around;
    // 지도 탭 조회 결과가 아직 없을 때 쓸 유종 (지도 탭에서 고른 유종)
    private FuelType fallbackFuelType = FuelType.GASOLINE;
    // 주유소 ID → 가격. 유종이 바뀌면 비운다.
    private Map<String, FavoritePrice> prices = new HashMap<>();
    private FuelType pricesFuelType;
    private boolean visible = false;
    private int requestId = 0;

    public FavoriteViewModel(@NonNull Application application) {
        super(application);
        favoriteRepository = FavoriteRepository.getInstance(application);
        detailRepository = StationDetailRepository.getInstance(application);
        carInfoRepository = CarInfoRepository.getInstance(application);
        brandSettingsRepository = BrandSettingsRepository.getInstance(application);

        items.addSource(favoriteRepository.getFavorites(), list -> {
            rebuild();
            if (visible) {
                loadPrices(false);  // 새로 저장한 주유소의 가격
            }
        });
        items.addSource(carInfoRepository.getCarInfo(), carInfo -> rebuild());
        items.addSource(brandSettingsRepository.getBrandSettings(), settings -> rebuild());
    }

    /** 목록. 즐겨찾기를 아직 읽지 못했으면 null */
    public LiveData<List<FavoriteItem>> getItems() {
        return items;
    }

    public LiveData<Boolean> getRefreshing() {
        return refreshing;
    }

    public LiveData<FavoriteStation> getLastRemoved() {
        return lastRemoved;
    }

    public void onRemovedShown() {
        lastRemoved.setValue(null);
    }

    public LiveData<Boolean> getRestoreFailed() {
        return restoreFailed;
    }

    public void onRestoreFailedShown() {
        restoreFailed.setValue(false);
    }

    /** 계산 기준 문구 */
    public LiveData<CarInfo> getCarInfo() {
        return carInfoRepository.getCarInfo();
    }

    /** 지금 가격을 보여주는 유종 */
    public FuelType getFuelType() {
        return around != null ? around.getFuelType() : fallbackFuelType;
    }

    /** 지도 탭의 주변 조회 결과가 바뀌었을 때 (조회 위치, 유종, 필터, 기준 주유소). */
    public void setAround(@Nullable AroundStations newAround, FuelType mapFuelType) {
        around = newAround;
        fallbackFuelType = mapFuelType;
        rebuild();
        if (visible) {
            loadPrices(false);
        }
    }

    /** 탭이 보이거나 숨겨질 때. 보이면 가격을 가져온다 (캐시가 있으면 캐시). */
    public void setVisible(boolean newVisible) {
        boolean becameVisible = newVisible && !visible;
        visible = newVisible;
        if (becameVisible) {
            loadPrices(false);
        }
    }

    /** 당겨서 새로고침: 주변 목록에 없고 30분이 지난 가격만 다시 가져온다. */
    public void refresh() {
        loadPrices(true);
    }

    public void remove(String stationId) {
        favoriteRepository.remove(stationId, removed -> {
            if (removed != null) {
                lastRemoved.setValue(removed);
            }
        });
    }

    /** 실행 취소 */
    public void restore(FavoriteStation favorite) {
        favoriteRepository.restore(favorite, result -> {
            if (result == FavoriteStation.AddResult.FULL) {
                restoreFailed.setValue(true);
            }
        });
    }

    private void loadPrices(boolean refresh) {
        List<FavoriteStation> favorites = favoriteRepository.getFavorites().getValue();
        if (favorites == null) {
            refreshing.setValue(false);
            return; // 즐겨찾기를 읽으면 다시 불린다.
        }
        if (favorites.isEmpty()) {
            refreshing.setValue(false);
            return;
        }
        FuelType fuelType = getFuelType();
        if (fuelType != pricesFuelType) {
            prices = new HashMap<>();
            pricesFuelType = fuelType;
            rebuild();
        }
        int id = ++requestId;
        if (refresh) {
            refreshing.setValue(true);
        }
        detailRepository.fetchFavoritePrices(favorites, fuelType, around, refresh,
                (favorite, detail) -> favoriteRepository.fillAddress(favorite.getId(), detail.getAddress()),
                result -> {
                    if (id != requestId) {
                        return; // 그사이 새 요청이 나갔다.
                    }
                    refreshing.setValue(false);
                    if (fuelType != pricesFuelType) {
                        return;
                    }
                    prices = new HashMap<>(prices);
                    prices.putAll(result);
                    rebuild();
                });
    }

    private void rebuild() {
        List<FavoriteStation> favorites = favoriteRepository.getFavorites().getValue();
        if (favorites == null) {
            return;
        }
        Map<String, FavoritePrice> current = getFuelType() == pricesFuelType ? prices : Collections.emptyMap();
        items.setValue(FavoriteListBuilder.build(favorites, current, around,
                carInfoRepository.getCurrent(), brandSettingsRepository.getCurrent()));
    }
}
