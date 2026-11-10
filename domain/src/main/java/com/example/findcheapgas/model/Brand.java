package com.example.findcheapgas.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 오피넷 브랜드 코드(POLL_DIV_CD)를 한글 이름으로 바꾼다.
 * 내 브랜드 설정과 브랜드 빠른 필터는 이 표의 코드 전체를 표 순서대로 보여준다.
 */
public final class Brand {

    /** 코드가 없거나 표에 없는 주유소는 자가상표로 본다 (내 브랜드 설정, 빠른 필터). */
    public static final String OTHER = "ETC";

    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    // 빠른 필터 칩처럼 좁은 곳에 쓰는 짧은 이름
    private static final Map<String, String> SHORT_NAMES = new LinkedHashMap<>();

    static {
        add("SKE", "SK에너지", "SK");
        add("GSC", "GS칼텍스", "GS");
        add("HDO", "현대오일뱅크", "현대");
        add("SOL", "S-OIL", "S-OIL");
        add("RTE", "자영알뜰", "자영알뜰");
        add("RTX", "고속도로알뜰", "고속도로알뜰");
        add("NHO", "농협알뜰", "농협알뜰");
        add("E1G", "E1", "E1");
        add("SKG", "SK가스", "SK가스");
        add(OTHER, "자가상표", "자가상표");
    }

    private static final List<String> CODES = Collections.unmodifiableList(new ArrayList<>(NAMES.keySet()));

    private Brand() {
    }

    private static void add(String code, String name, String shortName) {
        NAMES.put(code, name);
        SHORT_NAMES.put(code, shortName);
    }

    /** 표에 없는 코드는 코드를 그대로 보여준다. */
    public static String nameOf(String code) {
        if (code == null) {
            return NAMES.get(OTHER);
        }
        String name = NAMES.get(code);
        return name != null ? name : code;
    }

    /** 표의 코드 전체 (SK에너지 … 자가상표 순서) */
    public static List<String> codes() {
        return CODES;
    }

    /** 브랜드 설정을 찾을 때 쓰는 코드. 없거나 표에 없는 코드는 OTHER */
    public static String normalize(String code) {
        return code != null && NAMES.containsKey(code) ? code : OTHER;
    }

    public static String shortNameOf(String code) {
        return SHORT_NAMES.get(normalize(code));
    }
}
