package com.example.findcheapgas.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class NavigationControllerTest {

    private static final NavPoint HOME = new NavPoint("집", 37.5, 127.0);
    private static final NavPoint GAS = new NavPoint("주유소", 37.51, 127.01);

    @Test
    public void noRouteBeforeDestination() {
        UserInputNavigationController nav = new UserInputNavigationController();
        assertFalse(nav.getCurrentRouteInfo().isPresent());
        assertFalse(nav.addWaypoint(GAS));
    }

    @Test
    public void addAndRemoveWaypoint() {
        UserInputNavigationController nav = new UserInputNavigationController();
        assertTrue(nav.setDestination(HOME));
        assertTrue(nav.addWaypoint(GAS));
        assertEquals(Arrays.asList(GAS), nav.getCurrentRouteInfo().get().getWaypoints());
        assertEquals(HOME, nav.getCurrentRouteInfo().get().getDestination());
        assertTrue(nav.removeWaypoint(GAS));
        assertFalse(nav.removeWaypoint(GAS));
        assertTrue(nav.getCurrentRouteInfo().get().getWaypoints().isEmpty());
    }

    @Test
    public void newDestinationClearsWaypoints() {
        UserInputNavigationController nav = new UserInputNavigationController();
        nav.setDestination(HOME);
        nav.addWaypoint(GAS);
        nav.setDestination(GAS);
        assertTrue(nav.getCurrentRouteInfo().get().getWaypoints().isEmpty());
    }

    @Test
    public void mockRecordsCallsAndCanFail() {
        MockNavigationController nav = new MockNavigationController();
        nav.setDestination(HOME);
        nav.setFailing(true);
        assertFalse(nav.addWaypoint(GAS));
        assertEquals(Arrays.asList("setDestination:집", "addWaypoint:주유소"), nav.getCalls());
        assertTrue(nav.getCurrentRouteInfo().get().getWaypoints().isEmpty());
    }
}
