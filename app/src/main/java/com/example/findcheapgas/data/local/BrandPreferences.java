package com.example.findcheapgas.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.findcheapgas.model.Brand;
import com.example.findcheapgas.model.BrandSetting;
import com.example.findcheapgas.model.BrandSettings;

import java.util.HashMap;
import java.util.Map;

/**
 * 내 브랜드 설정 (SharedPreferences). 브랜드 코드마다 아래 값을 저장한다.
 * 예: SKE_per_liter=100, SKE_cap=3000, SKE_preferred=true
 * 한도를 비워 두면 _cap 키를 지운다. 아무것도 설정하지 않은 브랜드는 키를 모두 지운다.
 */
public class BrandPreferences {

    private static final String PREFS_NAME = "brand_settings";
    private static final String SUFFIX_PER_LITER = "_per_liter";   // 원/L
    private static final String SUFFIX_CAP = "_cap";               // 원, 없으면 한도 없음
    private static final String SUFFIX_PREFERRED = "_preferred";
    private static final String SUFFIX_HIDDEN = "_hidden";

    private final SharedPreferences prefs;

    public BrandPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public BrandSettings getBrandSettings() {
        Map<String, BrandSetting> settings = new HashMap<>();
        for (String code : Brand.codes()) {
            BrandSetting setting = read(code);
            if (setting != null) {
                settings.put(code, setting);
            }
        }
        return new BrandSettings(settings);
    }

    public void setBrandSetting(String code, BrandSetting setting) {
        SharedPreferences.Editor editor = prefs.edit();
        if (setting.isEmpty()) {
            editor.remove(code + SUFFIX_PER_LITER)
                    .remove(code + SUFFIX_CAP)
                    .remove(code + SUFFIX_PREFERRED)
                    .remove(code + SUFFIX_HIDDEN)
                    .apply();
            return;
        }
        editor.putInt(code + SUFFIX_PER_LITER, setting.getDiscountRule().getPerLiter())
                .putBoolean(code + SUFFIX_PREFERRED, setting.isPreferred())
                .putBoolean(code + SUFFIX_HIDDEN, setting.isHidden());
        Integer cap = setting.getDiscountRule().getCap();
        if (cap != null) {
            editor.putInt(code + SUFFIX_CAP, cap);
        } else {
            editor.remove(code + SUFFIX_CAP);
        }
        editor.apply();
    }

    /** 저장된 값이 없거나 범위를 벗어나면 null (설정 없음으로 본다) */
    private BrandSetting read(String code) {
        if (!prefs.contains(code + SUFFIX_PER_LITER)) {
            return null;
        }
        int perLiter = prefs.getInt(code + SUFFIX_PER_LITER, 0);
        Integer cap = prefs.contains(code + SUFFIX_CAP) ? prefs.getInt(code + SUFFIX_CAP, 0) : null;
        boolean preferred = prefs.getBoolean(code + SUFFIX_PREFERRED, false);
        boolean hidden = prefs.getBoolean(code + SUFFIX_HIDDEN, false);
        if (perLiter < 0 || perLiter > BrandSetting.MAX_PER_LITER
                || (cap != null && (cap < 0 || cap > BrandSetting.MAX_CAP))) {
            return null;
        }
        // 둘 다 켜진 값은 저장하지 않지만, 혹시 있으면 숨기기를 우선한다.
        return new BrandSetting(perLiter, cap, preferred && !hidden, hidden);
    }
}
