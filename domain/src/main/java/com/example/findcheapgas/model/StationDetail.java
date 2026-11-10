package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 오피넷 주유소 상세정보 (detailById.do). 상세 캐시에 Gson으로 그대로 저장된다.
 * 문자열은 응답에 없으면 빈 문자열이고, 부가시설은 "Y"일 때만 true다.
 */
public class StationDetail {

    /** 상호에 이 말이 들어 있으면 셀프 주유소로 추정한다. */
    private static final String SELF_KEYWORD = "셀프";

    private final String id;
    private final String name;
    private final String brandCode;
    private final String roadAddress;   // NEW_ADR
    private final String lotAddress;    // VAN_ADR
    private final String phone;         // TEL
    private final boolean carWash;      // CAR_WASH_YN
    private final boolean convenienceStore;  // CVS_YN
    private final boolean maintenance;  // MAINT_YN
    private final boolean qualityCertified;  // KPETRO_YN (한국석유관리원 품질인증)
    // 응답에 셀프 여부가 있으면 그 값, 없으면 null (상호로 추정)
    @Nullable
    private final Boolean selfService;
    private final double katecX;
    private final double katecY;
    // FuelType 순서(휘발유, 경유, 고급휘발유, LPG)로 정렬된, 파는 유종만
    private final List<FuelPrice> prices;

    public StationDetail(String id, String name, String brandCode, String roadAddress, String lotAddress,
                         String phone, boolean carWash, boolean convenienceStore, boolean maintenance,
                         boolean qualityCertified, @Nullable Boolean selfService,
                         double katecX, double katecY, List<FuelPrice> prices) {
        this.id = id;
        this.name = name;
        this.brandCode = brandCode;
        this.roadAddress = roadAddress;
        this.lotAddress = lotAddress;
        this.phone = phone;
        this.carWash = carWash;
        this.convenienceStore = convenienceStore;
        this.maintenance = maintenance;
        this.qualityCertified = qualityCertified;
        this.selfService = selfService;
        this.katecX = katecX;
        this.katecY = katecY;
        this.prices = prices;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBrandCode() {
        return brandCode;
    }

    /** 도로명 주소, 없으면 지번 주소. 둘 다 없으면 null */
    @Nullable
    public String getAddress() {
        if (!isBlank(roadAddress)) {
            return roadAddress.trim();
        }
        if (!isBlank(lotAddress)) {
            return lotAddress.trim();
        }
        return null;
    }

    /** 전화번호. 없으면 null */
    @Nullable
    public String getPhone() {
        return isBlank(phone) ? null : phone.trim();
    }

    public boolean hasCarWash() {
        return carWash;
    }

    public boolean hasConvenienceStore() {
        return convenienceStore;
    }

    public boolean hasMaintenance() {
        return maintenance;
    }

    public boolean isQualityCertified() {
        return qualityCertified;
    }

    /** 셀프 주유소인지. 응답에 값이 없으면 상호로 추정한다 (isSelfServiceEstimated). */
    public boolean isSelfService() {
        if (selfService != null) {
            return selfService;
        }
        return name != null && name.contains(SELF_KEYWORD);
    }

    /** 셀프 여부를 응답이 아니라 상호로 추정했는지 */
    public boolean isSelfServiceEstimated() {
        return selfService == null;
    }

    public double getKatecX() {
        return katecX;
    }

    public double getKatecY() {
        return katecY;
    }

    public List<FuelPrice> getPrices() {
        return prices != null ? prices : Collections.emptyList();
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
