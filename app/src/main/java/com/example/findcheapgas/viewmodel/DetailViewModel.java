package com.example.findcheapgas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.findcheapgas.data.repository.AveragePriceRepository;
import com.example.findcheapgas.data.repository.CarInfoRepository;
import com.example.findcheapgas.data.repository.FavoriteRepository;
import com.example.findcheapgas.data.repository.StationDetailRepository;
import com.example.findcheapgas.model.AverageComparison;
import com.example.findcheapgas.model.AveragePrices;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.DailyAveragePrice;
import com.example.findcheapgas.model.FavoriteStation;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.StationDetail;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.util.AverageComparisonBuilder;
import com.example.findcheapgas.util.PriceTrend;

import java.util.List;

/**
 * 주유소 상세 화면의 상태.
 * 목록에서 넘겨받은 기본 정보(상호, 브랜드, 가격, 거리)는 화면이 직접 들고 있고, 여기서는 상세 조회 결과만 가진다.
 * 상세 조회는 화면을 처음 열 때 한 번만 한다. 화면을 회전하면 ViewModel이 그대로 남아 있어서 다시 부르지 않는다.
 * 즐겨찾기 버튼: 이 주유소가 즐겨찾기인지 LiveData로 보여주고, 누르면 추가 / 삭제를 바꾼다.
 * 평균가 비교와 7일 추이도 상세 조회와 같이 처음 열 때 한 번만 가져온다 (AveragePriceRepository, 캐시 우선).
 * 둘 다 상세 조회와 따로 성공하거나 실패하고, 실패해도 상세정보와 예상 이득 계산 내역에는 영향이 없다.
 * - 평균가 비교: 평균가, 상세 조회 결과(그 유종 가격과 주소), 내 차 정보(주유량)가 모두 정해지면 계산한다.
 *   상세 조회가 실패하면 목록에서 넘겨받은 가격과 주소로 계산한다. 주유량을 바꾸면 다시 계산만 한다.
 * - 7일 추이: 지금 고른 유종의 최근 7일 전국 평균가
 */
public class DetailViewModel extends AndroidViewModel {

    /** 즐겨찾기 버튼을 눌렀을 때 결과 */
    public enum FavoriteMessage {
        ADDED,
        REMOVED,
        FULL
    }

    private final StationDetailRepository repository;
    private final FavoriteRepository favoriteRepository;
    // 이 주유소가 즐겨찾기인지. start 전에는 false
    private final MediatorLiveData<Boolean> favorite = new MediatorLiveData<>(false);
    // 즐겨찾기 버튼 결과 안내 (한 번 보여주면 null)
    private final MutableLiveData<FavoriteMessage> favoriteMessage = new MutableLiveData<>();
    // 추가나 삭제를 처리하는 중이면 버튼을 다시 눌러도 무시한다.
    private boolean favoriteBusy = false;

    // 조회 전이거나 실패했으면 null
    private final MutableLiveData<StationDetail> detail = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    // 오류가 없으면 null
    private final MutableLiveData<StationError> error = new MutableLiveData<>();

    private final AveragePriceRepository averageRepository;
    private final CarInfoRepository carInfoRepository;
    // 평균가 조회 결과와 실패 원인 (둘 다 null이면 불러오는 중)
    private final MutableLiveData<AveragePrices> averages = new MutableLiveData<>();
    private final MutableLiveData<StationError> averageError = new MutableLiveData<>();
    private final MediatorLiveData<SectionState<AverageComparison>> averageSection = new MediatorLiveData<>();
    private final MutableLiveData<SectionState<List<DailyAveragePrice>>> trendSection =
            new MutableLiveData<>(SectionState.<List<DailyAveragePrice>>loading());

    private String stationId;
    private FuelType fuelType = FuelType.GASOLINE;
    // 목록에서 넘겨받은 그 유종 가격(모르면 0)과 즐겨찾기에 저장된 주소. 상세 조회가 실패했을 때 평균가 비교에 쓴다.
    private int listedPrice;
    @Nullable
    private String fallbackAddress;

    public DetailViewModel(@NonNull Application application) {
        super(application);
        repository = StationDetailRepository.getInstance(application);
        favoriteRepository = FavoriteRepository.getInstance(application);
        averageRepository = AveragePriceRepository.getInstance(application);
        carInfoRepository = CarInfoRepository.getInstance(application);
        averageSection.setValue(SectionState.<AverageComparison>loading());
    }

    public LiveData<Boolean> isFavorite() {
        return favorite;
    }

    public LiveData<FavoriteMessage> getFavoriteMessage() {
        return favoriteMessage;
    }

    public void onFavoriteMessageShown() {
        favoriteMessage.setValue(null);
    }

    /**
     * 즐겨찾기 버튼. 즐겨찾기면 지우고, 아니면 저장한다 (20곳이 차 있으면 저장하지 않음).
     * 주소는 상세 조회가 끝났으면 그 주소를 함께 저장한다.
     */
    public void toggleFavorite(FavoriteStation candidate) {
        if (favoriteBusy) {
            return;
        }
        favoriteBusy = true;
        if (Boolean.TRUE.equals(favorite.getValue())) {
            favoriteRepository.remove(candidate.getId(), removed -> {
                favoriteBusy = false;
                favoriteMessage.setValue(FavoriteMessage.REMOVED);
            });
            return;
        }
        favoriteRepository.add(candidate, result -> {
            favoriteBusy = false;
            favoriteMessage.setValue(result == FavoriteStation.AddResult.FULL
                    ? FavoriteMessage.FULL : FavoriteMessage.ADDED);
        });
    }

    public LiveData<StationDetail> getDetail() {
        return detail;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<StationError> getError() {
        return error;
    }

    /** 평균가 비교 구역. 불러오는 중이면 LOADING, 평균가를 못 가져왔으면 ERROR */
    public LiveData<SectionState<AverageComparison>> getAverageSection() {
        return averageSection;
    }

    /** 7일 추이 구역. READY면 지금 고른 유종의 날짜순 평균가 (2개 미만일 수 있음) */
    public LiveData<SectionState<List<DailyAveragePrice>>> getTrendSection() {
        return trendSection;
    }

    /**
     * 화면을 만들 때마다 부르지만, 처음 한 번만 조회한다 (상세정보, 평균가, 7일 추이).
     * 화면을 회전해도 ViewModel이 남아 있으므로 다시 조회하지 않는다.
     *
     * @param selectedFuelType 지금 고른 유종 (평균가 비교와 추이에 쓴다)
     * @param priceFromList    목록에서 넘겨받은 그 유종 가격, 모르면 0
     * @param savedAddress     즐겨찾기에 저장된 주소, 없으면 null
     */
    public void start(String id, FuelType selectedFuelType, int priceFromList, @Nullable String savedAddress) {
        if (stationId != null) {
            return;
        }
        stationId = id;
        fuelType = selectedFuelType;
        listedPrice = priceFromList;
        fallbackAddress = savedAddress;
        favorite.addSource(Transformations.map(favoriteRepository.getFavoriteIds(), ids -> ids.contains(id)),
                favorite::setValue);

        averageSection.addSource(detail, value -> updateAverageSection());
        averageSection.addSource(error, value -> updateAverageSection());
        averageSection.addSource(loading, value -> updateAverageSection());
        averageSection.addSource(averages, value -> updateAverageSection());
        averageSection.addSource(averageError, value -> updateAverageSection());
        averageSection.addSource(carInfoRepository.getCarInfo(), value -> updateAverageSection());

        load();
        loadAverages();
        loadTrend();
    }

    private void loadAverages() {
        averageRepository.fetchAverages(new AveragePriceRepository.Callback<AveragePrices>() {
            @Override
            public void onSuccess(AveragePrices result) {
                averages.setValue(result);
            }

            @Override
            public void onError(StationError stationError) {
                averageError.setValue(stationError);
            }
        });
    }

    private void loadTrend() {
        averageRepository.fetchRecentAverages(new AveragePriceRepository.Callback<List<DailyAveragePrice>>() {
            @Override
            public void onSuccess(List<DailyAveragePrice> result) {
                trendSection.setValue(SectionState.ready(PriceTrend.forFuel(result, fuelType)));
            }

            @Override
            public void onError(StationError stationError) {
                trendSection.setValue(SectionState.<List<DailyAveragePrice>>error(stationError));
            }
        });
    }

    /**
     * 평균가 비교를 다시 계산한다 (API는 부르지 않는다).
     * 평균가를 못 가져왔으면 실패, 평균가나 상세 조회 결과(성공이든 실패든)가 아직 없으면 불러오는 중이다.
     */
    private void updateAverageSection() {
        StationError averageFailure = averageError.getValue();
        if (averageFailure != null) {
            averageSection.setValue(SectionState.<AverageComparison>error(averageFailure));
            return;
        }
        AveragePrices prices = averages.getValue();
        StationDetail stationDetail = detail.getValue();
        boolean detailSettled = stationDetail != null
                || (error.getValue() != null && !Boolean.TRUE.equals(loading.getValue()));
        if (prices == null || !detailSettled) {
            averageSection.setValue(SectionState.<AverageComparison>loading());
            return;
        }
        CarInfo carInfo = carInfoRepository.getCurrent();
        int fuelAmount = carInfo != null ? carInfo.getFuelAmount() : CarInfo.DEFAULT.getFuelAmount();
        averageSection.setValue(SectionState.ready(AverageComparisonBuilder.build(fuelType, stationDetail,
                listedPrice, fallbackAddress, prices, fuelAmount)));
    }

    /** 오류 안내의 다시 시도 버튼 */
    public void retry() {
        if (stationId != null && !Boolean.TRUE.equals(loading.getValue())) {
            load();
        }
    }

    private void load() {
        loading.setValue(true);
        error.setValue(null);
        repository.fetchDetail(stationId, new StationDetailRepository.Callback() {
            @Override
            public void onSuccess(StationDetail result) {
                loading.setValue(false);
                detail.setValue(result);
                // 주소를 모르고 저장한 즐겨찾기면 주소를 채운다.
                favoriteRepository.fillAddress(stationId, result.getAddress());
            }

            @Override
            public void onError(StationError stationError) {
                loading.setValue(false);
                error.setValue(stationError);
            }
        });
    }
}
