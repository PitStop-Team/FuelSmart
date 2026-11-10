package com.example.findcheapgas.widget;

import androidx.annotation.Nullable;

import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavingLevel;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.util.StationListBuilder;

/**
 * 위젯 한 장에 보여줄 내용. WidgetContentBuilder가 만들고 WidgetRenderer가 그린다.
 * 마지막으로 그린 내용은 WidgetPreferences에 Gson으로 저장해 두고, 크기를 바꾸거나 갱신에 실패했을 때 다시 쓴다.
 * 집·회사의 이름, 주소, 좌표는 들어 있지 않다. 위치는 종류(집 / 회사)와 등록한 시각으로만 구분한다.
 */
public final class WidgetContent {

    public enum State {
        LOADING,            // 아직 가져온 적이 없음
        NOT_REGISTERED,     // 고른 위치(집 / 회사)를 등록하지 않음
        READY,              // 이득 1위 주유소가 있음
        EMPTY,              // 반경, 숨긴 브랜드, 빠른 필터 때문에 주유소가 없음
        OUTSIDE_MOCK_AREA,  // 가짜 데이터 모드인데 강남역 주변 밖
        ERROR               // 가져오지 못했고 보여줄 이전 결과도 없음
    }

    SavedPlace.Kind kind;
    State state;
    // 어떤 위치·유종의 결과인지. 갱신에 실패했을 때 이전 결과를 계속 보여줘도 되는지 확인한다.
    long placeSavedAt;
    String fuelCode;

    // ERROR이거나 READY인데 이번 갱신에 실패했을 때 실패 원인
    @Nullable
    StationError error;
    boolean updateFailed;

    // EMPTY일 때 이유와 반경
    StationListBuilder.EmptyReason emptyReason = StationListBuilder.EmptyReason.NONE;
    int radiusMeters;

    // READY일 때 이득 1위 주유소
    String stationId;
    String stationName;
    String brandName;
    int price;
    String savingText;          // "약 1,547원 이득"
    SavingLevel savingLevel;
    double distanceMeters;      // 그 위치에서의 직선거리
    String basis;               // "12km/L · 40L (기본값)"
    long fetchedAt;

    WidgetContent() {
        // Gson
    }

    WidgetContent(SavedPlace.Kind kind, State state) {
        this.kind = kind;
        this.state = state;
    }

    public SavedPlace.Kind getKind() {
        return kind;
    }

    public State getState() {
        return state;
    }

    public long getPlaceSavedAt() {
        return placeSavedAt;
    }

    public String getFuelCode() {
        return fuelCode;
    }

    @Nullable
    public StationError getError() {
        return error;
    }

    /** READY인데 이번 갱신에 실패해서 이전 결과를 보여주는 중 */
    public boolean isUpdateFailed() {
        return updateFailed;
    }

    public StationListBuilder.EmptyReason getEmptyReason() {
        return emptyReason;
    }

    public int getRadiusMeters() {
        return radiusMeters;
    }

    public String getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public String getBrandName() {
        return brandName;
    }

    public int getPrice() {
        return price;
    }

    public String getSavingText() {
        return savingText;
    }

    public SavingLevel getSavingLevel() {
        return savingLevel;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getBasis() {
        return basis;
    }

    public long getFetchedAt() {
        return fetchedAt;
    }

    /** 같은 위치(등록한 시각)와 유종의 결과인지 */
    boolean isFor(long otherPlaceSavedAt, String otherFuelCode) {
        return placeSavedAt == otherPlaceSavedAt && fuelCode != null && fuelCode.equals(otherFuelCode);
    }

    /** 이전 결과를 그대로 두고 이번 갱신에 실패했다는 표시만 붙인 사본 */
    WidgetContent withUpdateFailed(StationError failure) {
        WidgetContent copy = copy();
        copy.updateFailed = true;
        copy.error = failure;
        return copy;
    }

    private WidgetContent copy() {
        WidgetContent c = new WidgetContent(kind, state);
        c.placeSavedAt = placeSavedAt;
        c.fuelCode = fuelCode;
        c.error = error;
        c.updateFailed = updateFailed;
        c.emptyReason = emptyReason;
        c.radiusMeters = radiusMeters;
        c.stationId = stationId;
        c.stationName = stationName;
        c.brandName = brandName;
        c.price = price;
        c.savingText = savingText;
        c.savingLevel = savingLevel;
        c.distanceMeters = distanceMeters;
        c.basis = basis;
        c.fetchedAt = fetchedAt;
        return c;
    }
}
