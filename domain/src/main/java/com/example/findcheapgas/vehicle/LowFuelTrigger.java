package com.example.findcheapgas.vehicle;

import java.util.OptionalInt;

/**
 * 연료 부족 자동 제안을 낼 때를 정한다. 연료 잔량은 차량 SDK가 %로만 주므로 %로만 판단한다.
 *
 * - 잔량이 {@link #SUGGEST_BELOW_PERCENT} 미만으로 내려가면(정확히 그 값은 아님) 제안을 한 번 낸다.
 * - 한 번 내면 잔량이 {@link #REARM_AT_PERCENT} 이상으로 회복(주유)할 때까지 다시 내지 않는다.
 *   운전자가 거절했을 때도 같다 (같은 연료 하락 구간에서는 다시 묻지 않는다).
 * - 회복한 뒤 다시 기준 아래로 내려가면 새로 제안한다.
 * - 값이 없으면(미지원, 읽기 실패) 아무 것도 하지 않는다.
 * 앱을 다시 시작하면 상태가 초기화되므로, 기준 아래인 채로 시작하면 한 번 제안한다.
 * 스레드 안전하지 않다. 한 스레드(메인)에서만 쓴다.
 */
public final class LowFuelTrigger {

    /** 이 값 미만이면 제안한다. 바꾸려면 여기만 고친다. */
    public static final int SUGGEST_BELOW_PERCENT = 25;
    /** 이 값 이상으로 회복하면 다시 제안할 수 있게 된다. */
    public static final int REARM_AT_PERCENT = 35;

    private boolean armed = true;

    /**
     * 새 연료 잔량을 알려 준다.
     * @return 지금 제안을 새로 내야 하면 true
     */
    public boolean onFuelPercent(OptionalInt percent) {
        if (percent == null || !percent.isPresent()) {
            return false;
        }
        int value = percent.getAsInt();
        if (armed && value < SUGGEST_BELOW_PERCENT) {
            armed = false;
            return true;
        }
        if (!armed && value >= REARM_AT_PERCENT) {
            armed = true;
        }
        return false;
    }
}
