package com.example.findcheapgas.viewmodel;

import androidx.annotation.Nullable;

import com.example.findcheapgas.model.StationError;

/**
 * 상세 화면에서 따로 불러오는 구역(평균가 비교, 7일 추이)의 상태.
 * 불러오는 중, 결과, 실패 중 하나이고, 실패해도 상세 화면의 다른 구역에는 영향을 주지 않는다.
 */
public final class SectionState<T> {

    public enum Status {
        LOADING,
        READY,
        ERROR
    }

    private final Status status;
    @Nullable
    private final T data;
    @Nullable
    private final StationError error;

    private SectionState(Status status, @Nullable T data, @Nullable StationError error) {
        this.status = status;
        this.data = data;
        this.error = error;
    }

    public static <T> SectionState<T> loading() {
        return new SectionState<>(Status.LOADING, null, null);
    }

    public static <T> SectionState<T> ready(T data) {
        return new SectionState<>(Status.READY, data, null);
    }

    public static <T> SectionState<T> error(StationError error) {
        return new SectionState<>(Status.ERROR, null, error);
    }

    public Status getStatus() {
        return status;
    }

    /** READY일 때만 있다. */
    @Nullable
    public T getData() {
        return data;
    }

    /** ERROR일 때만 있다. */
    @Nullable
    public StationError getError() {
        return error;
    }
}
