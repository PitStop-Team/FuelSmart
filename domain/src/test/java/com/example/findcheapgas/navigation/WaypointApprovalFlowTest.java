package com.example.findcheapgas.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class WaypointApprovalFlowTest {

    private static final NavPoint DEST = new NavPoint("집", 37.5, 127.0);
    private static final NavPoint GAS = new NavPoint("주유소", 37.51, 127.01);

    private static MockNavigationController navWithDestination() {
        MockNavigationController nav = new MockNavigationController();
        nav.setDestination(DEST);
        nav.getCalls().clear();
        return nav;
    }

    @Test
    public void proposalAloneNeverReachesNavigation() {
        MockNavigationController nav = navWithDestination();
        WaypointApprovalFlow flow = new WaypointApprovalFlow(nav);
        flow.propose(GAS, "약 1,900원 이득");
        assertTrue(flow.hasPending());
        assertTrue(nav.getCalls().isEmpty());
    }

    @Test
    public void approveAddsWaypointOnce() {
        MockNavigationController nav = navWithDestination();
        WaypointApprovalFlow flow = new WaypointApprovalFlow(nav);
        WaypointApprovalFlow.Proposal proposal = flow.propose(GAS, "요약");
        assertTrue(flow.approve(proposal));
        assertEquals(Arrays.asList("addWaypoint:주유소"), nav.getCalls());
        assertFalse(flow.approve(proposal));
        assertEquals(1, nav.getCalls().size());
        assertFalse(flow.hasPending());
    }

    @Test
    public void rejectSendsNothing() {
        MockNavigationController nav = navWithDestination();
        WaypointApprovalFlow flow = new WaypointApprovalFlow(nav);
        WaypointApprovalFlow.Proposal proposal = flow.propose(GAS, "요약");
        flow.reject(proposal);
        assertFalse(flow.approve(proposal));
        assertTrue(nav.getCalls().isEmpty());
    }

    @Test
    public void newProposalCancelsOldOne() {
        MockNavigationController nav = navWithDestination();
        WaypointApprovalFlow flow = new WaypointApprovalFlow(nav);
        WaypointApprovalFlow.Proposal first = flow.propose(GAS, "첫 번째");
        WaypointApprovalFlow.Proposal second = flow.propose(DEST, "두 번째");
        assertFalse(flow.approve(first));
        assertTrue(flow.approve(second));
        assertEquals(Arrays.asList("addWaypoint:집"), nav.getCalls());
    }

    @Test
    public void approveReportsNavigationFailure() {
        MockNavigationController nav = navWithDestination();
        nav.setFailing(true);
        WaypointApprovalFlow flow = new WaypointApprovalFlow(nav);
        assertFalse(flow.approve(flow.propose(GAS, "요약")));
    }
}
