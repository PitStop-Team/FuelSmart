package com.example.findcheapgas.util;

import androidx.annotation.Nullable;

import com.example.findcheapgas.model.AroundStations;
import com.example.findcheapgas.model.BrandSettings;
import com.example.findcheapgas.model.CarInfo;
import com.example.findcheapgas.model.FavoriteItem;
import com.example.findcheapgas.model.FavoritePrice;
import com.example.findcheapgas.model.FavoriteStation;
import com.example.findcheapgas.model.SearchOrigin;
import com.example.findcheapgas.model.StationQuery;
import com.example.findcheapgas.model.StationSaving;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 즐겨찾기 탭 목록을 만든다. API는 부르지 않고, 가져온 가격과 지도 탭의 주변 조회 결과로 계산만 한다.
 * - 순서: 즐겨찾기 목록 순서 그대로 (최근에 저장한 순)
 * - 거리: 지도 탭 조회 위치에서 직선거리
 * - 예상 이득: 조회 위치에서 5km 안이면 지도 탭과 같은 기준 주유소, 내 차 정보, 브랜드 할인으로 계산한다.
 *   지도 탭 화면 목록에 그 주유소가 있으면 그 계산 결과를 그대로 쓰고, 반경이나 필터 때문에 빠져 있으면
 *   같은 기준 주유소와 비교해서 계산한다. 5km 밖이면 계산하지 않는다.
 * 안드로이드에 의존하지 않아서 단위 테스트로 확인한다.
 */
public final class FavoriteListBuilder {

    private FavoriteListBuilder() {
    }

    /**
     * @param prices 주유소 ID → 가격. 아직 가져오는 중이면 없다.
     * @param around 지도 탭의 마지막 주변 조회 결과. 조회 위치를 아직 모르면 null
     */
    public static List<FavoriteItem> build(List<FavoriteStation> favorites, Map<String, FavoritePrice> prices,
                                           @Nullable AroundStations around, CarInfo carInfo,
                                           BrandSettings brandSettings) {
        List<FavoriteItem> items = new ArrayList<>(favorites.size());
        for (FavoriteStation favorite : favorites) {
            items.add(buildItem(favorite, prices.get(favorite.getId()), around, carInfo, brandSettings));
        }
        return Collections.unmodifiableList(items);
    }

    static FavoriteItem buildItem(FavoriteStation favorite, @Nullable FavoritePrice price,
                                  @Nullable AroundStations around, CarInfo carInfo, BrandSettings brandSettings) {
        if (around == null) {
            return new FavoriteItem(favorite, price, null, null, FavoriteItem.SavingStatus.NO_ORIGIN);
        }
        double distance = distanceFrom(around.getOrigin(), favorite);
        if (distance > StationQuery.FETCH_RADIUS_METERS) {
            return new FavoriteItem(favorite, price, distance, null, FavoriteItem.SavingStatus.TOO_FAR);
        }
        if (price == null || !price.isAvailable() || price.getFuelType() != around.getFuelType()) {
            return new FavoriteItem(favorite, price, distance, null, FavoriteItem.SavingStatus.NO_PRICE);
        }

        // 지도 탭 화면 목록에 있으면 같은 계산 결과 (이득 1위 표시 포함)
        StationSaving shown = around.findDisplayed(favorite.getId());
        if (shown != null && shown.getStation().getPrice() == price.getPrice()) {
            return new FavoriteItem(favorite, price, distance, shown, FavoriteItem.SavingStatus.CALCULATED);
        }
        List<StationSaving> displayed = around.getDisplayed();
        if (displayed.isEmpty()) {
            return new FavoriteItem(favorite, price, distance, null, FavoriteItem.SavingStatus.NO_BASELINE);
        }
        StationSaving saving = SavingCalculator.estimateAgainst(
                favorite.toGasStation(price.getPrice(), distance),
                displayed.get(0).getBaseline(), carInfo, brandSettings);
        return new FavoriteItem(favorite, price, distance, saving, FavoriteItem.SavingStatus.CALCULATED);
    }

    /** 조회 위치에서 즐겨찾기 주유소까지 직선거리(m). 오피넷 거리와 같게 KATEC 좌표로 잰다. */
    static double distanceFrom(SearchOrigin origin, FavoriteStation favorite) {
        CoordinateConverter.Katec katec = CoordinateConverter.toKatec(origin.getLatitude(), origin.getLongitude());
        return CoordinateConverter.distanceMeters(katec, favorite.getKatecX(), favorite.getKatecY());
    }
}
