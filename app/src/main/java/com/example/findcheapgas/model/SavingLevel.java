package com.example.findcheapgas.model;

/**
 * 예상 이득 구간. 지도 마커 색과 목록의 이득 글자 색에 쓴다.
 * 기준 주유소 대비 1,000원 이상 이득이면 GAIN(초록), 1,000원 이상 손해면 LOSS(빨강), 그 사이는 SIMILAR(회색).
 * 기준 주유소 자신은 이득 0원이라 SIMILAR이다.
 */
public enum SavingLevel {
    GAIN,
    SIMILAR,
    LOSS;

    public static final long THRESHOLD_WON = 1_000;

    /** 원 단위로 반올림한 예상 이득으로 구간을 정한다 (화면에 보이는 금액과 색이 어긋나지 않게). */
    public static SavingLevel of(long savingWon) {
        if (savingWon >= THRESHOLD_WON) {
            return GAIN;
        }
        if (savingWon <= -THRESHOLD_WON) {
            return LOSS;
        }
        return SIMILAR;
    }
}
