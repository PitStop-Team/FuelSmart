package com.example.findcheapgas.model;

/**
 * 주유소 이름 검색(오피넷 상호 검색) 결과 한 곳. 응답에 가격이 없을 수 있어서 가격은 넣지 않는다.
 * 상호 검색 캐시에 Gson으로 그대로 저장된다.
 */
public final class StationSearchResult {

    private final String id;          // UNI_ID
    private final String brandCode;   // POLL_DIV_CD (또는 POLL_DIV_CO)
    private final String name;        // OS_NM
    private final String address;     // 도로명 주소가 있으면 도로명, 없으면 지번
    private final double latitude;
    private final double longitude;

    public StationSearchResult(String id, String brandCode, String name, String address,
                               double latitude, double longitude) {
        this.id = id;
        this.brandCode = brandCode;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() {
        return id;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public String getBrandName() {
        return Brand.nameOf(brandCode);
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
