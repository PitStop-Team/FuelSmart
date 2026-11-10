package com.example.findcheapgas.navigation;

/**
 * 경유지 추가를 운전자 승인 뒤에만 내비게이션에 보내는 흐름.
 * 화면은 {@link #propose}로 제안을 만들어 승인 화면에 보여 주고, 운전자가 누른 결과로 {@link #approve}나
 * {@link #reject}를 부른다. 승인하지 않은 제안은 NavigationController에 닿지 않는다.
 * 한 번에 제안 하나만 대기한다. 새 제안을 만들면 이전 제안은 취소된다.
 */
public final class WaypointApprovalFlow {

    /** 승인을 기다리는 경유지 제안. 승인 화면에 보여 줄 문구를 함께 들고 있다. */
    public static final class Proposal {
        private final NavPoint waypoint;
        private final String summary;

        private Proposal(NavPoint waypoint, String summary) {
            this.waypoint = waypoint;
            this.summary = summary;
        }

        public NavPoint getWaypoint() {
            return waypoint;
        }

        /** 승인 화면에 보여 줄 요약 (예: 예상 이득) */
        public String getSummary() {
            return summary;
        }
    }

    private final NavigationController controller;
    private Proposal pending;

    public WaypointApprovalFlow(NavigationController controller) {
        this.controller = controller;
    }

    public synchronized Proposal propose(NavPoint waypoint, String summary) {
        pending = new Proposal(waypoint, summary);
        return pending;
    }

    /** 승인: 대기 중인 제안일 때만 경유지를 추가한다. 이미 처리했거나 취소된 제안이면 false */
    public synchronized boolean approve(Proposal proposal) {
        if (proposal == null || proposal != pending) {
            return false;
        }
        pending = null;
        return controller.addWaypoint(proposal.waypoint);
    }

    /** 거절 또는 닫기: 내비게이션에는 아무것도 보내지 않는다. */
    public synchronized void reject(Proposal proposal) {
        if (proposal != null && proposal == pending) {
            pending = null;
        }
    }

    public synchronized boolean hasPending() {
        return pending != null;
    }
}
