package com.example.findcheapgas.ui.favorite;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.example.findcheapgas.R;
import com.example.findcheapgas.model.GasStation;
import com.example.findcheapgas.model.PlaceCompareCard;
import com.example.findcheapgas.model.SavingLevel;
import com.example.findcheapgas.model.StationError;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.ui.SavingColors;
import com.example.findcheapgas.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 즐겨찾기 탭의 집·회사 기준 비교 카드 한 장 (item_place_compare.xml)을 그린다.
 * - READY: 제목, 이득 1위 상호, 작은 보조 글자 한 줄(브랜드 · 거리 · 가격 · 가져온 시각),
 *   오른쪽에 예상 총비용과 "가장 저렴" 배지 또는 "약 1,200원 더 들어요"
 * - 등록 안 함: 카드 대신 점선 테두리 안에 "집 위치 등록하기" 버튼 하나
 * - 그 밖: 제목과 이유 (가져오는 중, 주유소 없음, 한도 도달 등)
 */
final class PlaceCompareCardView {

    interface Listener {
        /** 카드를 눌렀을 때 (등록하지 않은 자리는 카드가 없다) */
        void onCardClick(PlaceCompareCard card);

        /** 등록하지 않은 자리의 "집 위치 등록하기" 버튼 */
        void onRegisterClick(PlaceCompareCard card);
    }

    private final View card;
    private final TextView title;
    private final View row;
    private final TextView station;
    private final TextView detail;
    private final TextView total;
    private final TextView cheapest;
    private final TextView extra;
    private final TextView message;
    private final View registerBox;
    private final TextView register;

    PlaceCompareCardView(View root) {
        card = root.findViewById(R.id.compare_card);
        title = root.findViewById(R.id.compare_title);
        row = root.findViewById(R.id.compare_row);
        station = root.findViewById(R.id.compare_station);
        detail = root.findViewById(R.id.compare_detail);
        total = root.findViewById(R.id.compare_total);
        cheapest = root.findViewById(R.id.compare_cheapest);
        extra = root.findViewById(R.id.compare_extra);
        message = root.findViewById(R.id.compare_message);
        registerBox = root.findViewById(R.id.compare_register_box);
        register = root.findViewById(R.id.compare_register);
    }

    /**
     * @param titleText   예: "집 근처 · 선릉역"
     * @param kindLabel   등록 버튼에 넣을 이름 ("집", "회사"). 지금 조회 위치 카드면 쓰지 않는다.
     * @param radiusLabel 지금 반경 (예: "3km")
     */
    void bind(PlaceCompareCard compareCard, String titleText, String kindLabel, String radiusLabel, long now,
              Listener listener) {
        Context context = card.getContext();
        boolean notRegistered = compareCard.getStatus() == PlaceCompareCard.Status.NOT_REGISTERED;
        card.setVisibility(notRegistered ? View.GONE : View.VISIBLE);
        registerBox.setVisibility(notRegistered ? View.VISIBLE : View.GONE);
        if (notRegistered) {
            register.setText(context.getString(R.string.compare_register, kindLabel));
            register.setOnClickListener(v -> listener.onRegisterClick(compareCard));
            return;
        }

        title.setText(titleText);
        StationSaving best = compareCard.getBest();
        boolean ready = compareCard.getStatus() == PlaceCompareCard.Status.READY && best != null;
        row.setVisibility(ready ? View.VISIBLE : View.GONE);
        message.setVisibility(ready ? View.GONE : View.VISIBLE);
        cheapest.setVisibility(ready && compareCard.isCheapest() ? View.VISIBLE : View.GONE);
        extra.setVisibility(ready && compareCard.getExtraCostWon() > 0 ? View.VISIBLE : View.GONE);

        String summary;
        if (ready) {
            GasStation gasStation = best.getStation();
            station.setText(gasStation.getName());
            List<String> parts = new ArrayList<>();
            parts.add(gasStation.getBrandName());
            parts.add(FormatUtil.formatDistance(gasStation.getDistance()));
            parts.add(FormatUtil.formatPrice(gasStation.getPrice()));
            if (compareCard.getFetchedAt() > 0) {
                parts.add(FormatUtil.formatPriceAge(compareCard.getFetchedAt(), now));
            }
            detail.setText(join(parts));
            total.setText(FormatUtil.formatApproxWon(best.getTotalCost()));
            // 가장 저렴한 곳과 1,000원 미만 차이면 지도 탭의 "비슷"과 같이 회색 "비슷해요", 그보다 크면 빨강
            boolean similar = compareCard.isSimilarToCheapest();
            extra.setText(similar ? context.getString(R.string.compare_similar)
                    : FormatUtil.formatExtraCost(compareCard.getExtraCostWon()));
            extra.setTextColor(ContextCompat.getColor(context,
                    SavingColors.textColorOf(similar ? SavingLevel.SIMILAR : SavingLevel.LOSS)));
            summary = gasStation.getName() + ", " + detail.getText() + ", "
                    + context.getString(R.string.compare_card_total, total.getText())
                    + (compareCard.isCheapest() ? ", " + cheapest.getText() : "")
                    + (compareCard.getExtraCostWon() > 0
                    ? ", " + (compareCard.isSimilarToCheapest()
                    ? context.getString(R.string.compare_similar_description,
                    FormatUtil.formatWon(compareCard.getExtraCostWon()))
                    : extra.getText())
                    : "");
        } else {
            message.setText(messageFor(context, compareCard, radiusLabel));
            summary = message.getText().toString();
        }

        card.setOnClickListener(v -> listener.onCardClick(compareCard));
        card.setContentDescription(context.getString(R.string.compare_card_description, titleText, summary));
    }

    private static String join(List<String> parts) {
        // String.join은 API 26부터라서 직접 잇는다.
        StringBuilder text = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            text.append(" · ").append(parts.get(i));
        }
        return text.toString();
    }

    private static String messageFor(Context context, PlaceCompareCard card, String radiusLabel) {
        switch (card.getStatus()) {
            case WAITING:
                return context.getString(R.string.compare_waiting);
            case OUTSIDE_MOCK_AREA:
                return context.getString(R.string.compare_outside_mock);
            case EMPTY:
                switch (card.getEmptyReason()) {
                    case HIDDEN_BRANDS:
                        return context.getString(R.string.compare_empty_hidden);
                    case BRAND_FILTER:
                        return context.getString(R.string.compare_empty_filter);
                    default:
                        return context.getString(R.string.compare_empty_radius, radiusLabel);
                }
            case ERROR:
                return context.getString(errorMessage(card.getError()));
            default:
                return context.getString(R.string.compare_loading);
        }
    }

    @StringRes
    private static int errorMessage(StationError error) {
        if (error == StationError.DAILY_LIMIT) {
            return R.string.compare_error_daily_limit;
        }
        if (error == StationError.NETWORK) {
            return R.string.compare_error_network;
        }
        if (error == StationError.API_KEY) {
            return R.string.opinet_key_missing;
        }
        return R.string.compare_error_opinet;
    }
}
