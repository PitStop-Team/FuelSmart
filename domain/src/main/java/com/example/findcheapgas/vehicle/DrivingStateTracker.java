package com.example.findcheapgas.vehicle;

/**
 * 브레이크 페달 상태만으로 정차 여부를 추정한다 (Vehicle SDK 문서에서 쓸 수 있는 것은 브레이크 페달과 주행 모드뿐이고,
 * 주행 모드(NORMAL/ECO/SPORT)에는 정차 정보가 없다).
 * 페달이 {@link #STOP_HOLD_MILLIS} 이상 계속 눌려 있어야 STOPPED로 본다. 짧게 밟는 감속을 정차로 착각해서
 * 주행 중에 목록 화면이 나오지 않게 하려는 것이다. 이 값은 추정이므로 실제 차량에서 확인해야 한다.
 * 페달을 떼면 바로 DRIVING이다. 값을 받은 적이 없거나 잃으면 UNKNOWN이다.
 * 스레드 안전하지 않다. 한 스레드(메인)에서만 쓴다.
 */
public final class DrivingStateTracker {

    public static final long STOP_HOLD_MILLIS = 3_000;

    private boolean available = false;
    private boolean pressed = false;
    private long pressedSince;

    /** 브레이크 페달 상태가 바뀌거나 처음 읽혔을 때 */
    public void onBrakePedal(boolean nowPressed, long nowMillis) {
        if (nowPressed && !(available && pressed)) {
            pressedSince = nowMillis;
        }
        available = true;
        pressed = nowPressed;
    }

    /** 차량이 페달 상태를 주지 못할 때 (미지원, 읽기 실패) */
    public void onUnavailable() {
        available = false;
        pressed = false;
    }

    public DrivingState stateAt(long nowMillis) {
        if (!available) {
            return DrivingState.UNKNOWN;
        }
        if (pressed && nowMillis - pressedSince >= STOP_HOLD_MILLIS) {
            return DrivingState.STOPPED;
        }
        return DrivingState.DRIVING;
    }

    /** 페달이 눌린 채 아직 STOPPED가 아니면, STOPPED가 되기까지 남은 시간(ms). 아니면 -1 */
    public long millisUntilStopped(long nowMillis) {
        if (!available || !pressed) {
            return -1;
        }
        long remain = STOP_HOLD_MILLIS - (nowMillis - pressedSince);
        return remain > 0 ? remain : -1;
    }
}
