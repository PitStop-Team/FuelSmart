package com.example.findcheapgas.data.remote;

import androidx.annotation.Nullable;

/**
 * local.properties에 OPINET_API_KEY가 없어서(빌드에 키가 비어 있어서) 오피넷을 부르지 않았을 때.
 * 화면에는 "오피넷 API 키가 설정되지 않았어요"로 보여준다 (StationError.API_KEY, SearchError.OPINET_KEY).
 */
public class OpinetKeyMissingException extends OpinetException {

    public OpinetKeyMissingException() {
        super("OPINET_API_KEY가 설정되지 않았습니다.");
    }

    /**
     * 키가 비어 있으면 던진다. 실제 호출 전에 Repository들이 BuildConfig.OPINET_API_KEY를 넘겨서 부르고,
     * 테스트에서는 빈 값을 넘겨 확인한다.
     */
    public static void requireKey(@Nullable String key) throws OpinetKeyMissingException {
        if (key == null || key.trim().isEmpty()) {
            throw new OpinetKeyMissingException();
        }
    }
}
