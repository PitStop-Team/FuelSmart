package com.example.findcheapgas.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * 앱 로컬 DB. 주변 주유소 조회, 상세정보, 주유소 이름 검색 캐시와 즐겨찾기, 집·회사 위치가 있다.
 * 이 기기에만 저장한다. 집·회사 위치가 들어 있어서 DB 파일은 클라우드 백업과 기기 이전에서 뺀다 (res/xml의 백업 규칙).
 * 버전 2: 상세정보 캐시 추가
 * 버전 3: 주유소 이름 검색 캐시 추가
 * 버전 4: 즐겨찾기 추가 (처음으로 사용자가 저장한 데이터가 들어감)
 * 버전 5: 집·회사 위치 추가 (사용자가 저장한 데이터)
 *
 * 마이그레이션 규칙
 * - 버전을 올릴 때마다 Migration(이전, 다음)을 작성해 MIGRATIONS에 넣는다. 스키마는 app/schemas에 내보내 커밋한다.
 * - 캐시 표(station_*_cache)는 다시 받으면 되므로 Migration에서 지우고 새로 만들어도 된다.
 * - 즐겨찾기, 집·회사 위치처럼 사용자가 저장한 표는 데이터를 반드시 옮겨서 보존한다.
 * - 캐시만 있던 버전 1, 2에서 올라올 때만 DB를 지우고 새로 만든다 (그때는 사용자 데이터가 없었다).
 */
@Database(entities = {StationCacheEntity.class, StationDetailCacheEntity.class, StationSearchCacheEntity.class,
        FavoriteStationEntity.class, SavedPlaceEntity.class},
        version = 5, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public static final String DB_NAME = "findcheapgas.db";

    /** 3 → 4: 즐겨찾기 표를 만든다. 캐시 표는 그대로 둔다. */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // app/schemas/.../4.json의 createSql과 같아야 Room이 열 때 검사를 통과한다.
            db.execSQL("CREATE TABLE IF NOT EXISTS `favorite_station` ("
                    + "`stationId` TEXT NOT NULL, `name` TEXT NOT NULL, `brandCode` TEXT, `address` TEXT, "
                    + "`katecX` REAL NOT NULL, `katecY` REAL NOT NULL, "
                    + "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, "
                    + "`savedAt` INTEGER NOT NULL, PRIMARY KEY(`stationId`))");
        }
    };

    /** 4 → 5: 집·회사 위치 표를 만든다. 즐겨찾기와 캐시 표는 그대로 둔다. */
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // app/schemas/.../5.json의 createSql과 같아야 Room이 열 때 검사를 통과한다.
            db.execSQL("CREATE TABLE IF NOT EXISTS `saved_place` ("
                    + "`kind` TEXT NOT NULL, `name` TEXT NOT NULL, `address` TEXT, "
                    + "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, "
                    + "`savedAt` INTEGER NOT NULL, PRIMARY KEY(`kind`))");
        }
    };

    public static final Migration[] MIGRATIONS = {MIGRATION_3_4, MIGRATION_4_5};

    private static volatile AppDatabase instance;

    public abstract StationCacheDao stationCacheDao();

    public abstract StationDetailCacheDao stationDetailCacheDao();

    public abstract StationSearchCacheDao stationSearchCacheDao();

    public abstract FavoriteStationDao favoriteStationDao();

    public abstract SavedPlaceDao savedPlaceDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = builder(context.getApplicationContext(), DB_NAME).build();
                }
            }
        }
        return instance;
    }

    /**
     * 앱과 마이그레이션 테스트가 같은 설정으로 DB를 열도록 한곳에서 만든다.
     * 맞는 Migration이 없으면 Room이 예외를 던지므로, 사용자 데이터가 조용히 지워지지 않는다.
     */
    public static RoomDatabase.Builder<AppDatabase> builder(Context context, String name) {
        return Room.databaseBuilder(context, AppDatabase.class, name)
                .addMigrations(MIGRATIONS)
                .fallbackToDestructiveMigrationFrom(1, 2);
    }
}
