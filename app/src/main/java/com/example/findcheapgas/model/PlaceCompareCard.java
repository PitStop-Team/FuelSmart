package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

import com.example.findcheapgas.util.StationListBuilder;

/**
 * 집·회사 기준 비교 카드 한 장 (지금 조회 위치 / 집 근처 / 회사 근처). PlaceCompareBuilder가 만든다.
 * READY면 그 위치에서 출발한다고 볼 때의 이득 1위 주유소(best)가 있고, 위치끼리는 예상 총비용으로 비교한다.
 * 예상 이득은 위치마다 기준 주유소가 달라서 위치끼리 비교하지 않는다.
 */
public final class PlaceCompareCard {

    public enum Slot {
        CURRENT,  // 지금 조회 위치 (지도 탭이 받아 둔 결과)
        HOME,
        WORK
    }

    public enum Status {
        NOT_REGISTERED,     // 집·회사 위치를 등록하지 않음
        WAITING,            // 지금 조회 위치: 지도 탭이 아직 조회하지 않음
        LOADING,            // 가져오는 중
        OUTSIDE_MOCK_AREA,  // 가짜 데이터 모드인데 강남역 주변 밖이라 비교할 수 없음
        ERROR,              // 조회 실패 (하루 한도, 네트워크, 오피넷)
        EMPTY,              // 반경, 숨긴 브랜드, 빠른 필터 때문에 보여줄 주유소가 없음
        READY               // 이득 1위 주유소가 있음
    }

    private final Slot slot;
    private final Status status;
    @Nullable
    private final StationError error;
    private final StationListBuilder.EmptyReason emptyReason;
    @Nullable
    private final StationSaving best;
    private final long fetchedAt;
    private final boolean cheapest;
    private final long extraCostWon;

    public PlaceCompareCard(Slot slot, Status status, @Nullable StationError error,
                            StationListBuilder.EmptyReason emptyReason, @Nullable StationSaving best,
                            long fetchedAt, boolean cheapest, long extraCostWon) {
        this.slot = slot;
        this.status = status;
        this.error = error;
        this.emptyReason = emptyReason;
        this.best = best;
        this.fetchedAt = fetchedAt;
        this.cheapest = cheapest;
        this.extraCostWon = extraCostWon;
    }

    public Slot getSlot() {
        return slot;
    }

    public Status getStatus() {
        return status;
    }

    /** ERROR일 때 실패 원인. 그 밖에는 null */
    @Nullable
    public StationError getError() {
        return error;
    }

    /** EMPTY일 때 이유. 그 밖에는 NONE */
    public StationListBuilder.EmptyReason getEmptyReason() {
        return emptyReason;
    }

    /** READY일 때 그 위치의 이득 1위 주유소와 계산 결과. 그 밖에는 null */
    @Nullable
    public StationSaving getBest() {
        return best;
    }

    /** 원 단위로 반올림한 예상 총비용. READY가 아니면 0 */
    public long getTotalCostWon() {
        return best != null ? Math.round(best.getTotalCost()) : 0;
    }

    /** 가격을 가져온 시각. 지금 조회 위치(지도 탭 결과)이거나 모르면 0 */
    public long getFetchedAt() {
        return fetchedAt;
    }

    /** READY 카드가 두 장 이상일 때, 예상 총비용이 가장 낮은 카드인지 (원 단위로 같으면 둘 다) */
    public boolean isCheapest() {
        return cheapest;
    }

    /** 가장 저렴한 카드보다 예상 총비용이 얼마나 더 드는지(원). 비교하지 않았거나 가장 저렴하면 0 */
    public long getExtraCostWon() {
        return extraCostWon;
    }

    /**
     * 가장 저렴한 카드보다 더 들지만 그 차이가 지도 탭의 "비슷" 구간(SavingLevel, ±1,000원) 안인지.
     * 가장 저렴한 카드 자신과 비교하지 않은 카드는 false
     */
    public boolean isSimilarToCheapest() {
        return extraCostWon > 0 && SavingLevel.of(-extraCostWon) == SavingLevel.SIMILAR;
    }
}
