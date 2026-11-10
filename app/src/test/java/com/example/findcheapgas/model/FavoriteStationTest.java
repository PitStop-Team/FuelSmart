package com.example.findcheapgas.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 즐겨찾기 최대 20곳 */
public class FavoriteStationTest {

    @Test
    public void maxCountIs20() {
        assertEquals(20, FavoriteStation.MAX_COUNT);
    }

    @Test
    public void addUntil20() {
        assertEquals(FavoriteStation.AddResult.ADDED, FavoriteStation.checkAdd(false, 0));
        assertEquals(FavoriteStation.AddResult.ADDED, FavoriteStation.checkAdd(false, 19));
    }

    @Test
    public void full_when20Saved() {
        assertEquals(FavoriteStation.AddResult.FULL, FavoriteStation.checkAdd(false, 20));
        assertEquals(FavoriteStation.AddResult.FULL, FavoriteStation.checkAdd(false, 25));
    }

    @Test
    public void alreadySaved_isNotFullEvenAt20() {
        assertEquals(FavoriteStation.AddResult.ALREADY_SAVED, FavoriteStation.checkAdd(true, 20));
    }

    @Test
    public void toGasStation_keepsCoordinates() {
        FavoriteStation favorite = new FavoriteStation("A1", "역삼주유소", "SKE", null, 314000, 544000,
                37.5, 127.03, 1L);
        GasStation station = favorite.toGasStation(1650, 1200);

        assertEquals("A1", station.getId());
        assertEquals(1650, station.getPrice());
        assertEquals(1200, station.getDistance(), 0);
        assertEquals(314000, station.getKatecX(), 0);
        assertEquals(37.5, station.getLatitude(), 0);
    }
}
