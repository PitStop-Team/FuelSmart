package com.example.findcheapgas.model;

/**
 * 화면에 보여줄 반경. 오피넷에는 항상 최대 반경(StationQuery.FETCH_RADIUS_METERS)으로 요청하고,
 * 이 반경 안의 주유소만 앱에서 골라 보여준다.
 */
public enum SearchRadius {
    KM_1(1000, "1km"),
    KM_3(3000, "3km"),
    KM_5(5000, "5km");

    private final int meters;
    private final String label;

    SearchRadius(int meters, String label) {
        this.meters = meters;
        this.label = label;
    }

    public int getMeters() {
        return meters;
    }

    public String getLabel() {
        return label;
    }

    /** 저장해 둔 미터 값으로 찾는다. 없거나 모르는 값이면 defaultValue */
    public static SearchRadius fromMeters(int meters, SearchRadius defaultValue) {
        for (SearchRadius radius : values()) {
            if (radius.meters == meters) {
                return radius;
            }
        }
        return defaultValue;
    }
}
