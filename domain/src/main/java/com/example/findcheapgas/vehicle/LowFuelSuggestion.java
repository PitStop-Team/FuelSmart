package com.example.findcheapgas.vehicle;

import com.example.findcheapgas.model.SavingLevel;
import com.example.findcheapgas.model.StationSaving;
import com.example.findcheapgas.util.FormatUtil;
import com.example.findcheapgas.util.PlaceCompareBuilder;

import java.util.List;
import java.util.Optional;

/**
 * 연료 부족 제안 한 건: 근처에서 예상 총비용이 가장 낮은(이득 1위) 주유소 1곳과 보여 줄 문구.
 * 후보 고르기는 기존 예상 이득 계산 결과(목록의 이득 1위)를 그대로 쓴다.
 * 목적지(경로)가 있으면 경유지로 추가, 없으면 그곳으로 안내하는 제안이다.
 * (경로 위 주유소만 비교하는 경유 모드는 아직 없어서, 두 경우 모두 주변 목록에서 고른다.)
 */
public final class LowFuelSuggestion {

    /** 승인했을 때 할 일 */
    public enum Action {
        /** 안내 중인 경로가 있다: 경유지로 추가 (경유지 승인 화면을 거친다) */
        ADD_WAYPOINT,
        /** 안내 중인 경로가 없다: 이 주유소로 안내 */
        ROUTE_HERE
    }

    private final int fuelPercent;
    private final StationSaving saving;
    private final Action action;

    private LowFuelSuggestion(int fuelPercent, StationSaving saving, Action action) {
        this.fuelPercent = fuelPercent;
        this.saving = saving;
        this.action = action;
    }

    /**
     * @param displayed 지금 화면에 보이는 주변 주유소 목록 (예상 이득 계산이 끝난 것)
     * @return 목록이 비었으면 empty
     */
    public static Optional<LowFuelSuggestion> create(int fuelPercent, List<StationSaving> displayed,
                                                     boolean hasRoute) {
        if (displayed == null || displayed.isEmpty()) {
            return Optional.empty();
        }
        StationSaving best = PlaceCompareBuilder.findBest(displayed);
        if (best == null) {
            best = displayed.get(0);
        }
        return Optional.of(new LowFuelSuggestion(fuelPercent, best,
                hasRoute ? Action.ADD_WAYPOINT : Action.ROUTE_HERE));
    }

    public int getFuelPercent() {
        return fuelPercent;
    }

    public StationSaving getSaving() {
        return saving;
    }

    public Action getAction() {
        return action;
    }

    /**
     * 카드 문구. 이득이 1,000원 미만이면 기존 규칙대로 "가장 가까운 곳과 비슷해요"로 쓰고 금액은 말하지 않는다.
     * 예: "연료가 부족해요(약 20%). 역삼셀프주유소가 가장 가까운 곳보다 약 1,925원 이득이에요."
     */
    public String message() {
        String name = saving.getStation().getName();
        String subject = name + subjectParticle(name);
        String head = "연료가 부족해요(약 " + fuelPercent + "%). ";
        if (saving.isBaseline()) {
            return head + subject + " 가장 가까운 주유소예요.";
        }
        if (saving.getLevel() == SavingLevel.GAIN) {
            return head + subject + " 가장 가까운 곳보다 약 " + FormatUtil.formatWon(saving.getSavingWon())
                    + " 이득이에요.";
        }
        return head + subject + " 가장 가까운 곳과 비슷해요.";
    }

    /** 승인 버튼 문구 */
    public String actionLabel() {
        return action == Action.ADD_WAYPOINT ? "경유지로 추가" : "여기로 안내";
    }

    /** 이름 끝 글자에 받침이 있으면 "이", 없으면 "가". 한글이 아니면 "이(가)" */
    static String subjectParticle(String name) {
        if (name == null || name.isEmpty()) {
            return "이(가)";
        }
        char last = name.charAt(name.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return "이(가)";
        }
        return (last - 0xAC00) % 28 != 0 ? "이" : "가";
    }
}
