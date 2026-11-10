package com.example.findcheapgas.navigation;

import java.util.ArrayList;
import java.util.List;

/** 에뮬레이터/테스트용 모의 구현. 동작은 UserInput 구현과 같고, 호출 기록을 남기며 실패를 흉내 낼 수 있다. */
public final class MockNavigationController extends UserInputNavigationController {

    private final List<String> calls = new ArrayList<>();
    private boolean failing;

    /** true면 경로를 바꾸는 호출이 모두 false를 돌려준다 (내비게이션 연결 실패 흉내). */
    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    /** "setDestination:이름" 형태의 호출 기록 */
    public List<String> getCalls() {
        return calls;
    }

    @Override
    public boolean setDestination(NavPoint destination) {
        calls.add("setDestination:" + (destination == null ? null : destination.getName()));
        return !failing && super.setDestination(destination);
    }

    @Override
    public boolean addWaypoint(NavPoint waypoint) {
        calls.add("addWaypoint:" + (waypoint == null ? null : waypoint.getName()));
        return !failing && super.addWaypoint(waypoint);
    }

    @Override
    public boolean removeWaypoint(NavPoint waypoint) {
        calls.add("removeWaypoint:" + (waypoint == null ? null : waypoint.getName()));
        return !failing && super.removeWaypoint(waypoint);
    }
}
