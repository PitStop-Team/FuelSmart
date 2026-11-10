package com.example.findcheapgas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.findcheapgas.data.local.AppDatabase;
import com.example.findcheapgas.data.local.SavedPlaceDao;
import com.example.findcheapgas.data.local.SavedPlaceEntity;
import com.example.findcheapgas.model.SavedPlace;
import com.example.findcheapgas.model.SavedPlaces;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 집·회사 위치 저장소 (Room). 앱 전체에서 하나만 쓴다.
 * Context만 받고 Activity나 Fragment에 의존하지 않아서 홈 화면 위젯에서도 쓸 수 있다.
 * - 위치는 LiveData로 내보낸다. Room이 백그라운드에서 읽고, 표가 바뀌면 새 값을 보낸다.
 * - 저장, 삭제는 한 스레드에서 차례로 처리하고, 삭제 결과는 메인 스레드에서 Callback으로 돌려준다.
 *   메인 스레드에서는 DB에 접근하지 않는다.
 * - 이 기기의 DB에만 저장하고 다른 곳으로 보내지 않는다 (백업에서도 뺀다). 이름, 주소, 좌표는 로그에 남기지 않는다.
 */
public class SavedPlaceRepository {

    public interface RemoveCallback {
        /** 지운 위치 (실행 취소용). 이미 없었으면 null */
        void onRemoved(@Nullable SavedPlace removed);
    }

    private static volatile SavedPlaceRepository instance;

    private final SavedPlaceDao dao;
    private final LiveData<SavedPlaces> places;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static SavedPlaceRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (SavedPlaceRepository.class) {
                if (instance == null) {
                    instance = new SavedPlaceRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private SavedPlaceRepository(Context context) {
        dao = AppDatabase.getInstance(context).savedPlaceDao();
        places = Transformations.map(dao.observeAll(), SavedPlaceRepository::toModels);
    }

    /** 등록한 집·회사 위치. DB를 읽기 전에는 null */
    public LiveData<SavedPlaces> getPlaces() {
        return places;
    }

    /** 홈 화면 위젯 작업(WorkManager)용. 지금 등록된 위치를 이 스레드에서 바로 읽는다. 메인 스레드에서 부르면 안 된다. */
    @WorkerThread
    public SavedPlaces loadNow() {
        return toModels(dao.getAll());
    }

    /** 같은 종류가 이미 있으면 바꾼다. 실행 취소로 되돌릴 때도 쓴다. */
    public void save(SavedPlace place) {
        SavedPlaceEntity entity = toEntity(place);
        executor.execute(() -> dao.save(entity));
    }

    public void remove(SavedPlace.Kind kind, RemoveCallback callback) {
        executor.execute(() -> {
            SavedPlaceEntity entity = dao.find(kind.name());
            if (entity != null) {
                dao.delete(kind.name());
            }
            SavedPlace removed = entity != null ? toModel(entity) : null;
            mainHandler.post(() -> callback.onRemoved(removed));
        });
    }

    private static SavedPlaces toModels(List<SavedPlaceEntity> entities) {
        SavedPlace home = null;
        SavedPlace work = null;
        for (SavedPlaceEntity entity : entities) {
            SavedPlace place = toModel(entity);
            if (place == null) {
                continue;
            }
            if (place.getKind() == SavedPlace.Kind.HOME) {
                home = place;
            } else {
                work = place;
            }
        }
        return new SavedPlaces(home, work);
    }

    /** 알 수 없는 종류면 null */
    @Nullable
    private static SavedPlace toModel(SavedPlaceEntity e) {
        SavedPlace.Kind kind;
        try {
            kind = SavedPlace.Kind.valueOf(e.kind);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
        return new SavedPlace(kind, e.name, e.address, e.latitude, e.longitude, e.savedAt);
    }

    private static SavedPlaceEntity toEntity(SavedPlace p) {
        return new SavedPlaceEntity(p.getKind().name(), p.getName(), p.getAddress(),
                p.getLatitude(), p.getLongitude(), p.getSavedAt());
    }
}
