package com.example.findcheapgas.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.findcheapgas.model.BrandFilter;
import com.example.findcheapgas.model.FuelType;
import com.example.findcheapgas.model.SearchRadius;
import com.example.findcheapgas.model.SortOrder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 지도 탭에서 마지막으로 고른 유종, 반경, 정렬, 브랜드 빠른 필터 (SharedPreferences).
 * 앱을 다시 켜도 이 값으로 시작한다. 설정 탭의 기본 유종·반경도 따로 저장하지 않고 이 값을 보여주고 바꾼다.
 * 홈 화면 위젯도 이 값(유종, 반경, 빠른 필터)으로 계산하고, 바뀌면 WidgetSettingsWatcher가 위젯을 다시 그리게 한다.
 * 정렬을 저장한 적이 없으면(새로 설치) 예상 이득순으로 시작하고, 이미 고른 정렬이 있으면 그 값을 쓴다.
 */
public class StationFilterPreferences {

    public static final FuelType DEFAULT_FUEL_TYPE = FuelType.GASOLINE;
    public static final SearchRadius DEFAULT_RADIUS = SearchRadius.KM_3;
    public static final SortOrder DEFAULT_SORT_ORDER = SortOrder.SAVING;

    public static final String PREFS_NAME = "station_filter";
    private static final String KEY_FUEL_TYPE = "fuel_type";      // 유종 코드 (B027 등)
    private static final String KEY_RADIUS = "radius_meters";      // 1000, 3000, 5000
    public static final String KEY_SORT_ORDER = "sort_order";     // SortOrder.name()
    // 브랜드 빠른 필터. 아무것도 저장하지 않았으면 전체
    private static final String KEY_BRAND_DISCOUNT_ONLY = "brand_discount_only";
    private static final String KEY_BRAND_PREFERRED_ONLY = "brand_preferred_only";
    private static final String KEY_BRAND_CODES = "brand_codes";   // 체크한 브랜드 코드 (SKE 등)

    private final SharedPreferences prefs;

    public StationFilterPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public FuelType getFuelType() {
        return FuelType.fromCode(prefs.getString(KEY_FUEL_TYPE, null), DEFAULT_FUEL_TYPE);
    }

    public SearchRadius getRadius() {
        return SearchRadius.fromMeters(prefs.getInt(KEY_RADIUS, 0), DEFAULT_RADIUS);
    }

    public SortOrder getSortOrder() {
        return SortOrder.fromName(prefs.getString(KEY_SORT_ORDER, null), DEFAULT_SORT_ORDER);
    }

    public BrandFilter getBrandFilter() {
        Set<String> codes = prefs.getStringSet(KEY_BRAND_CODES, Collections.emptySet());
        return new BrandFilter(
                prefs.getBoolean(KEY_BRAND_DISCOUNT_ONLY, false),
                prefs.getBoolean(KEY_BRAND_PREFERRED_ONLY, false),
                codes != null ? codes : Collections.emptySet());
    }

    public void setFuelType(FuelType fuelType) {
        prefs.edit().putString(KEY_FUEL_TYPE, fuelType.getCode()).apply();
    }

    public void setRadius(SearchRadius radius) {
        prefs.edit().putInt(KEY_RADIUS, radius.getMeters()).apply();
    }

    public void setSortOrder(SortOrder sortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, sortOrder.name()).apply();
    }

    public void setBrandFilter(BrandFilter filter) {
        prefs.edit()
                .putBoolean(KEY_BRAND_DISCOUNT_ONLY, filter.isDiscountOnly())
                .putBoolean(KEY_BRAND_PREFERRED_ONLY, filter.isPreferredOnly())
                // getStringSet이 돌려준 Set을 고치면 안 되므로 항상 새 Set을 넣는다.
                .putStringSet(KEY_BRAND_CODES, new HashSet<>(filter.getBrandCodes()))
                .apply();
    }
}
