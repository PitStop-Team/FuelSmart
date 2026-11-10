package com.example.findcheapgas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.findcheapgas.data.local.AppDatabase;
import com.example.findcheapgas.data.local.FavoriteStationDao;
import com.example.findcheapgas.data.local.FavoriteStationEntity;
import com.example.findcheapgas.model.FavoriteStation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 즐겨찾기 저장소 (Room). 앱 전체에서 하나만 쓴다.
 * - 목록은 LiveData로 내보낸다. Room이 백그라운드에서 읽고, 표가 바뀌면 새 목록을 보낸다.
 * - 추가, 삭제, 되돌리기는 한 스레드에서 차례로 처리하고 결과는 메인 스레드에서 Callback으로 돌려준다.
 *   메인 스레드에서는 DB에 접근하지 않는다.
 * - 최대 20곳(FavoriteStation.MAX_COUNT). 개수 확인과 저장은 한 트랜잭션에서 해서 빠르게 여러 번 눌러도 넘지 않는다.
 */
public class FavoriteRepository {

    public interface AddCallback {
        void onResult(FavoriteStation.AddResult result);
    }

    public interface RemoveCallback {
        /** 지운 즐겨찾기 (실행 취소용). 이미 없었으면 null */
        void onRemoved(@Nullable FavoriteStation removed);
    }

    private static volatile FavoriteRepository instance;

    private final AppDatabase database;
    private final FavoriteStationDao dao;
    private final LiveData<List<FavoriteStation>> favorites;
    private final LiveData<Set<String>> favoriteIds;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static FavoriteRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (FavoriteRepository.class) {
                if (instance == null) {
                    instance = new FavoriteRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private FavoriteRepository(Context context) {
        database = AppDatabase.getInstance(context);
        dao = database.favoriteStationDao();
        favorites = Transformations.map(dao.observeAll(), FavoriteRepository::toModels);
        favoriteIds = Transformations.map(favorites, list -> {
            Set<String> ids = new HashSet<>();
            for (FavoriteStation favorite : list) {
                ids.add(favorite.getId());
            }
            return Collections.unmodifiableSet(ids);
        });
    }

    /** 최근에 저장한 순서 */
    public LiveData<List<FavoriteStation>> getFavorites() {
        return favorites;
    }

    /** 즐겨찾기한 주유소 ID (지도 탭 목록의 북마크 표시, 상세 화면 버튼) */
    public LiveData<Set<String>> getFavoriteIds() {
        return favoriteIds;
    }

    /** 20곳이 차 있으면 저장하지 않고 FULL을 돌려준다. */
    public void add(FavoriteStation favorite, AddCallback callback) {
        executor.execute(() -> {
            FavoriteStation.AddResult result = database.runInTransaction(() -> {
                FavoriteStation.AddResult check = FavoriteStation.checkAdd(
                        dao.countById(favorite.getId()) > 0, dao.count());
                if (check == FavoriteStation.AddResult.ADDED) {
                    dao.insert(toEntity(favorite));
                }
                return check;
            });
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void remove(String stationId, RemoveCallback callback) {
        executor.execute(() -> {
            FavoriteStation removed = database.runInTransaction(() -> {
                FavoriteStationEntity entity = dao.find(stationId);
                if (entity != null) {
                    dao.delete(stationId);
                }
                return entity != null ? toModel(entity) : null;
            });
            mainHandler.post(() -> callback.onRemoved(removed));
        });
    }

    /** 실행 취소: 지운 즐겨찾기를 저장한 시각 그대로 되돌린다 (목록에서 원래 자리로 돌아감). */
    public void restore(FavoriteStation favorite, AddCallback callback) {
        add(favorite, callback);
    }

    /** 주소를 모르고 저장한 즐겨찾기에 상세정보의 주소를 채운다. 어느 스레드에서 불러도 된다. */
    public void fillAddress(String stationId, @Nullable String address) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        executor.execute(() -> dao.fillAddress(stationId, address.trim()));
    }

    private static List<FavoriteStation> toModels(List<FavoriteStationEntity> entities) {
        List<FavoriteStation> result = new ArrayList<>(entities.size());
        for (FavoriteStationEntity entity : entities) {
            result.add(toModel(entity));
        }
        return Collections.unmodifiableList(result);
    }

    private static FavoriteStation toModel(FavoriteStationEntity e) {
        return new FavoriteStation(e.stationId, e.name, e.brandCode, e.address,
                e.katecX, e.katecY, e.latitude, e.longitude, e.savedAt);
    }

    private static FavoriteStationEntity toEntity(FavoriteStation f) {
        return new FavoriteStationEntity(f.getId(), f.getName(), f.getBrandCode(), f.getAddress(),
                f.getKatecX(), f.getKatecY(), f.getLatitude(), f.getLongitude(), f.getSavedAt());
    }
}
