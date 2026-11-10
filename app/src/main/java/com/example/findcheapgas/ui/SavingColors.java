package com.example.findcheapgas.ui;

import androidx.annotation.ColorRes;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.SavingLevel;

/**
 * 예상 이득 구간별 글자색 (초록 이득, 회색 비슷, 빨강 손해).
 * 지도 탭 목록, 즐겨찾기 목록, 상세 화면 계산 내역이 같이 쓴다. 지도 마커 색은 StationMarkers에 따로 있다.
 * 주인공 숫자(이득 1위 카드, 상세 화면 맨 위 카드)는 heroTextColorOf를 쓴다. 어두운 테마에서 이득이 라임이다.
 */
public final class SavingColors {

    private SavingColors() {
    }

    @ColorRes
    public static int textColorOf(SavingLevel level) {
        switch (level) {
            case GAIN:
                return R.color.saving_gain_text;
            case LOSS:
                return R.color.saving_loss_text;
            default:
                return R.color.saving_similar_text;
        }
    }

    @ColorRes
    public static int heroTextColorOf(SavingLevel level) {
        return level == SavingLevel.GAIN ? R.color.hero_saving_gain_text : textColorOf(level);
    }
}
