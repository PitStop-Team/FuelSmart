package com.example.findcheapgas.navigation;

import java.util.Objects;

/** 이름이 있는 지점 (WGS84). 목적지/경유지에 쓴다. */
public final class NavPoint {

    private final String name;
    private final double latitude;
    private final double longitude;

    public NavPoint(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NavPoint)) {
            return false;
        }
        NavPoint other = (NavPoint) o;
        return Double.compare(latitude, other.latitude) == 0
                && Double.compare(longitude, other.longitude) == 0
                && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, latitude, longitude);
    }
}
