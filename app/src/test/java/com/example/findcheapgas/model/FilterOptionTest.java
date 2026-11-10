package com.example.findcheapgas.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 저장해 둔 선택 값을 다시 읽을 때 쓰는 변환. 모르는 값이면 기본값으로 돌아가야 한다. */
public class FilterOptionTest {

    @Test
    public void fuelType_fromCode() {
        assertEquals(FuelType.DIESEL, FuelType.fromCode("D047", FuelType.GASOLINE));
        assertEquals(FuelType.GASOLINE, FuelType.fromCode(null, FuelType.GASOLINE));
        assertEquals(FuelType.GASOLINE, FuelType.fromCode("X999", FuelType.GASOLINE));
    }

    @Test
    public void radius_fromMeters() {
        assertEquals(SearchRadius.KM_5, SearchRadius.fromMeters(5000, SearchRadius.KM_3));
        assertEquals(SearchRadius.KM_3, SearchRadius.fromMeters(0, SearchRadius.KM_3));
        assertEquals(SearchRadius.KM_3, SearchRadius.fromMeters(2000, SearchRadius.KM_3));
    }

    @Test
    public void sortOrder_fromName() {
        assertEquals(SortOrder.DISTANCE, SortOrder.fromName("DISTANCE", SortOrder.PRICE));
        assertEquals(SortOrder.PRICE, SortOrder.fromName(null, SortOrder.PRICE));
        assertEquals(SortOrder.PRICE, SortOrder.fromName("BENEFIT", SortOrder.PRICE));
    }
}
