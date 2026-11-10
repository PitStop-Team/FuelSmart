package com.example.findcheapgas.model;

/** 오피넷 유종 코드 */
public enum FuelType {
    GASOLINE("B027", "휘발유"),
    DIESEL("D047", "경유"),
    PREMIUM_GASOLINE("B034", "고급휘발유"),
    LPG("K015", "LPG");

    private final String code;
    private final String label;

    FuelType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 저장해 둔 유종 코드로 찾는다. 없거나 모르는 값이면 defaultValue */
    public static FuelType fromCode(String code, FuelType defaultValue) {
        for (FuelType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return defaultValue;
    }
}
