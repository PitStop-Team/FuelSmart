package com.example.findcheapgas.data.repository;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import com.example.findcheapgas.model.UserLocation;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * 현재 위치를 가져온다.
 * getCurrentLocation → (실패하거나 시간 초과) lastLocation → (그것도 없으면) 기본 위치 순서로 시도한다.
 */
public class LocationRepository {

    private static final String TAG = "LocationRepository";

    // 서울시청
    public static final double DEFAULT_LATITUDE = 37.5666;
    public static final double DEFAULT_LONGITUDE = 126.9784;

    // 이 시간 안에 현재 위치를 못 받으면 getCurrentLocation이 null을 돌려준다.
    private static final long CURRENT_LOCATION_TIMEOUT_MS = 8_000;
    // 이 정도로 최근 위치라면 새로 측정하지 않고 바로 쓴다.
    private static final long MAX_LOCATION_AGE_MS = 60_000;

    public interface Callback {
        void onResult(UserLocation location);
    }

    private final Context appContext;
    private final FusedLocationProviderClient fusedClient;

    public LocationRepository(Context context) {
        appContext = context.getApplicationContext();
        fusedClient = LocationServices.getFusedLocationProviderClient(appContext);
    }

    /** 정확한 위치나 대략적 위치 중 하나라도 허용됐는지 */
    public boolean hasPermission() {
        return isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
                || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /** 기기 위치(GPS) 설정이 켜져 있는지 */
    public boolean isLocationEnabled() {
        LocationManager manager = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        return manager != null && LocationManagerCompat.isLocationEnabled(manager);
    }

    public UserLocation getDefaultLocation() {
        return new UserLocation(DEFAULT_LATITUDE, DEFAULT_LONGITUDE, UserLocation.Source.DEFAULT);
    }

    @SuppressLint("MissingPermission") // hasPermission()으로 확인한다.
    public void fetchCurrentLocation(Callback callback) {
        if (!hasPermission()) {
            callback.onResult(getDefaultLocation());
            return;
        }

        // 대략적 위치만 허용된 경우 정확도를 낮춰서 요청한다.
        int priority = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
                ? Priority.PRIORITY_HIGH_ACCURACY
                : Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setDurationMillis(CURRENT_LOCATION_TIMEOUT_MS)
                .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MS)
                .build();

        try {
            fusedClient.getCurrentLocation(request, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            callback.onResult(toUserLocation(location, UserLocation.Source.CURRENT));
                        } else {
                            Log.w(TAG, "현재 위치를 받지 못해 마지막 위치를 사용합니다.");
                            fetchLastLocation(callback);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "현재 위치 조회 실패. 마지막 위치를 사용합니다.", e);
                        fetchLastLocation(callback);
                    });
        } catch (SecurityException e) {
            Log.w(TAG, "위치 권한이 없어 기본 위치를 사용합니다.", e);
            callback.onResult(getDefaultLocation());
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLastLocation(Callback callback) {
        try {
            fusedClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            callback.onResult(toUserLocation(location, UserLocation.Source.LAST_KNOWN));
                        } else {
                            Log.w(TAG, "마지막 위치도 없어 기본 위치를 사용합니다.");
                            callback.onResult(getDefaultLocation());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "마지막 위치 조회 실패. 기본 위치를 사용합니다.", e);
                        callback.onResult(getDefaultLocation());
                    });
        } catch (SecurityException e) {
            Log.w(TAG, "위치 권한이 없어 기본 위치를 사용합니다.", e);
            callback.onResult(getDefaultLocation());
        }
    }

    private boolean isGranted(String permission) {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static UserLocation toUserLocation(Location location, UserLocation.Source source) {
        return new UserLocation(location.getLatitude(), location.getLongitude(), source);
    }
}
