package com.example.findcheapgas.pleos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.Brake;
import ai.pleos.playground.vehicle.api.Fuel;
import ai.pleos.playground.vehicle.api.model.capability.AccessType;
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig;
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener;
import ai.pleos.playground.vehicle.listener.FuelEconomyListener;
import ai.pleos.playground.vehicle.listener.FuelPercentListener;

import com.example.findcheapgas.BuildConfig;
import com.example.findcheapgas.vehicle.CachedVehicleDataSource;
import com.example.findcheapgas.vehicle.DrivingState;
import com.example.findcheapgas.vehicle.DrivingStateTracker;
import com.example.findcheapgas.vehicle.VehicleDataSource;

import kotlin.Unit;

/**
 * Pleos Vehicle SDK 연결. 앱에 하나만 둔다.
 *
 * - 연료: Fuel 도메인(권한 CAR_ENERGY)에서 연료 잔량(%)과 현재 연비(L/100km)를 읽어 CachedVehicleDataSource에 담는다.
 * - 정차: Brake 도메인(권한 CAR_DRIVE)의 브레이크 페달 상태를 DrivingStateTracker로 정차 여부로 바꾼다.
 *
 * 모든 속성은 읽기 전에 capability(checkXxxCapabilities)를 확인한다. READ 또는 READ_WRITE가 아니거나,
 * 호출이 onFailed로 끝나거나, SDK 연결 자체가 실패하면(폰, 차량 서비스 없음) 그 값은 "없음"으로 두고
 * 호출하는 쪽이 사용자 입력값으로 대체한다. SDK 호출은 모두 예외를 잡아서 앱이 죽지 않게 한다.
 */
public final class PleosVehicleSession {

    private static final String TAG = "PleosVehicleSession";
    private static final long STATE_TICK_MILLIS = 500;

    private static PleosVehicleSession instance;

    public static synchronized PleosVehicleSession getInstance(Context context) {
        if (instance == null) {
            instance = new PleosVehicleSession(context.getApplicationContext());
        }
        return instance;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final CachedVehicleDataSource dataSource = new CachedVehicleDataSource();
    private final DrivingStateTracker tracker = new DrivingStateTracker();
    private final MutableLiveData<DrivingState> drivingState = new MutableLiveData<>(DrivingState.UNKNOWN);
    private final MutableLiveData<Long> fuelDataVersion = new MutableLiveData<>(0L);

    private Vehicle vehicle;
    // 디버그 빌드 전용: 연료 %를 직접 정해 연료 부족 제안을 시험한다. 값이 있으면 차량이 준 연료 %는 무시한다.
    private volatile Integer debugFuelPercent;
    private boolean started = false;

    private final Runnable stateTick = new Runnable() {
        @Override
        public void run() {
            publishState();
            if (tracker.millisUntilStopped(System.currentTimeMillis()) > 0) {
                main.postDelayed(this, STATE_TICK_MILLIS);
            }
        }
    };

    private PleosVehicleSession(Context appContext) {
        this.appContext = appContext;
    }

    private final Context appContext;

    /** 연료 데이터 (사용자 입력값 대체는 CarInfoResolver가 한다). */
    public VehicleDataSource getDataSource() {
        return dataSource;
    }

    /** 연료 값을 새로 받을 때마다 바뀌는 숫자. 계산을 다시 하라는 신호로 쓴다. */
    public LiveData<Long> getFuelDataVersion() {
        return fuelDataVersion;
    }

    public LiveData<DrivingState> getDrivingState() {
        return drivingState;
    }

    /** 앱이 시작될 때 한 번. 여러 번 불러도 한 번만 연결한다. */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        try {
            vehicle = new Vehicle(appContext);
            vehicle.initialize();
        } catch (Throwable t) {
            Log.w(TAG, "Vehicle SDK를 시작할 수 없습니다. 차량 데이터 없이 동작합니다.", t);
            vehicle = null;
            return;
        }
        try {
            startFuel(vehicle.getFuel());
        } catch (Throwable t) {
            Log.w(TAG, "연료 도메인을 쓸 수 없습니다.", t);
        }
        try {
            startBrake(vehicle.getBrake());
        } catch (Throwable t) {
            Log.w(TAG, "브레이크 도메인을 쓸 수 없습니다.", t);
            onBrakeUnavailable();
        }
    }

    // ---------- 연료 ----------

    private void startFuel(Fuel fuel) {
        fuel.checkFuelPercentCapabilities(config -> {
            if (isReadable(config)) {
                fuel.getFuelPercent(percent -> {
                    onVehicleFuelPercent(percent);
                    return Unit.INSTANCE;
                }, e -> failed("연료 잔량 읽기", e));
                fuel.registerFuelPercent(new FuelPercentListener() {
                    @Override
                    public void onFuelPercentUpdated(Integer percent) {
                        onVehicleFuelPercent(percent);
                    }

                    @Override
                    public void onFailed(@NonNull Exception e) {
                        failed("연료 잔량 알림", e);
                    }
                });
            } else {
                Log.i(TAG, "연료 잔량은 읽을 수 없는 차량입니다: " + config.getAccessType());
            }
            return Unit.INSTANCE;
        }, e -> failed("연료 잔량 capability", e));

        fuel.checkCurrentFuelEconomyCapabilities(config -> {
            if (isReadable(config)) {
                fuel.getCurrentFuelEconomy(economy -> {
                    dataSource.setFuelEconomyLitersPer100Km(economy);
                    notifyFuelChanged();
                    return Unit.INSTANCE;
                }, e -> failed("현재 연비 읽기", e));
                fuel.registerCurrentFuelEconomy(new FuelEconomyListener() {
                    @Override
                    public void onFuelEconomyUpdated(Float economy) {
                        dataSource.setFuelEconomyLitersPer100Km(economy);
                        notifyFuelChanged();
                    }

                    @Override
                    public void onFailed(@NonNull Exception e) {
                        failed("현재 연비 알림", e);
                    }
                });
            } else {
                Log.i(TAG, "현재 연비는 읽을 수 없는 차량입니다: " + config.getAccessType());
            }
            return Unit.INSTANCE;
        }, e -> failed("현재 연비 capability", e));
    }

    private void onVehicleFuelPercent(Integer percent) {
        if (debugFuelPercent != null) {
            return;
        }
        dataSource.setFuelPercent(percent);
        notifyFuelChanged();
    }

    /**
     * 디버그 빌드 전용 연료 % 지정 (null이면 지정을 푼다). 릴리스 빌드에서는 아무 일도 하지 않는다.
     */
    public void setDebugFuelPercent(Integer percent) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        debugFuelPercent = percent;
        dataSource.setFuelPercent(percent);
        notifyFuelChanged();
    }

    public Integer getDebugFuelPercent() {
        return BuildConfig.DEBUG ? debugFuelPercent : null;
    }

    private void notifyFuelChanged() {
        main.post(() -> {
            Long current = fuelDataVersion.getValue();
            fuelDataVersion.setValue(current == null ? 1 : current + 1);
        });
    }

    // ---------- 브레이크 ----------

    private void startBrake(Brake brake) {
        brake.checkBrakePedalStatusCapabilities(config -> {
            if (isReadable(config)) {
                brake.getCurrentBrakePedalStatus(pressed -> {
                    onBrakePedal(pressed);
                    return Unit.INSTANCE;
                }, e -> {
                    onBrakeUnavailable();
                    return failed("브레이크 페달 읽기", e);
                });
                brake.registerBrakePedalStatus(new BrakePedalStatusListener() {
                    @Override
                    public void onBrakePedalStatusUpdated(Boolean pressed) {
                        onBrakePedal(pressed);
                    }

                    @Override
                    public void onFailed(@NonNull Exception e) {
                        onBrakeUnavailable();
                        failed("브레이크 페달 알림", e);
                    }
                });
            } else {
                Log.i(TAG, "브레이크 페달은 읽을 수 없는 차량입니다: " + config.getAccessType());
                onBrakeUnavailable();
            }
            return Unit.INSTANCE;
        }, e -> {
            onBrakeUnavailable();
            return failed("브레이크 페달 capability", e);
        });
    }

    private void onBrakePedal(Boolean pressed) {
        if (pressed == null) {
            return;
        }
        main.post(() -> {
            tracker.onBrakePedal(pressed, System.currentTimeMillis());
            publishState();
            main.removeCallbacks(stateTick);
            main.postDelayed(stateTick, STATE_TICK_MILLIS);
        });
    }

    private void onBrakeUnavailable() {
        main.post(() -> {
            tracker.onUnavailable();
            publishState();
        });
    }

    private void publishState() {
        DrivingState state = tracker.stateAt(System.currentTimeMillis());
        if (state != drivingState.getValue()) {
            drivingState.setValue(state);
        }
    }

    // ---------- 공통 ----------

    private static boolean isReadable(CapabilityConfig config) {
        AccessType type = config.getAccessType();
        return type == AccessType.READ || type == AccessType.READ_WRITE;
    }

    /** onFailed 처리: 기록만 하고 값은 "없음"으로 둔다 (호출하는 쪽이 사용자 입력값을 쓴다). */
    private static Unit failed(String what, Exception e) {
        Log.w(TAG, what + " 실패: " + e);
        return Unit.INSTANCE;
    }
}
