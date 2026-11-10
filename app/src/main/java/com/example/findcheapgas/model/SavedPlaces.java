package com.example.findcheapgas.model;

import androidx.annotation.Nullable;

/** 등록한 집·회사 위치 전체. 등록하지 않은 쪽은 null이다. */
public final class SavedPlaces {

    public static final SavedPlaces EMPTY = new SavedPlaces(null, null);

    @Nullable
    private final SavedPlace home;
    @Nullable
    private final SavedPlace work;

    public SavedPlaces(@Nullable SavedPlace home, @Nullable SavedPlace work) {
        this.home = home;
        this.work = work;
    }

    @Nullable
    public SavedPlace get(SavedPlace.Kind kind) {
        return kind == SavedPlace.Kind.HOME ? home : work;
    }
}
