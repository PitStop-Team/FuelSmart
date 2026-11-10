package com.example.findcheapgas.model;

import java.util.Comparator;

/**
 * 주유소 목록 정렬 기준. 정렬은 오피넷에 맡기지 않고 앱에서 받아 둔 목록으로 한다.
 * 정렬을 늘리려면 상수를 하나 추가하면 된다. 목록 시트의 정렬 메뉴는 values() 순서대로 만들어진다.
 * 저장할 때는 name()을 쓰므로 상수 이름은 바꾸지 않는다.
 */
public enum SortOrder {
    // 정렬용 점수(원 단위 예상 이득, 선호 브랜드는 +500원)가 큰 곳 먼저. 같으면 싼 곳, 그다음 가까운 곳
    // "차이가 500원 이내면 같다고 보는" 비교는 전이성이 깨져서 쓰지 않는다.
    SAVING("예상 이득순", Comparator.comparingLong((StationSaving s) -> -s.getSortScoreWon())
            .thenComparingInt(s -> s.getStation().getPrice())
            .thenComparingDouble(s -> s.getStation().getDistance())),
    // 같은 가격이면 가까운 곳 먼저. 가격순과 거리순은 선호 브랜드를 반영하지 않는다.
    PRICE("가격순", Comparator.comparingInt((StationSaving s) -> s.getStation().getPrice())
            .thenComparingDouble(s -> s.getStation().getDistance())),
    // 같은 거리면 싼 곳 먼저
    DISTANCE("거리순", Comparator.comparingDouble((StationSaving s) -> s.getStation().getDistance())
            .thenComparingInt(s -> s.getStation().getPrice()));

    private final String label;
    private final Comparator<StationSaving> comparator;

    SortOrder(String label, Comparator<StationSaving> comparator) {
        this.label = label;
        this.comparator = comparator;
    }

    public String getLabel() {
        return label;
    }

    public Comparator<StationSaving> getComparator() {
        return comparator;
    }

    /** 저장해 둔 이름으로 찾는다. 없거나 모르는 값이면 defaultValue */
    public static SortOrder fromName(String name, SortOrder defaultValue) {
        for (SortOrder order : values()) {
            if (order.name().equals(name)) {
                return order;
            }
        }
        return defaultValue;
    }
}
