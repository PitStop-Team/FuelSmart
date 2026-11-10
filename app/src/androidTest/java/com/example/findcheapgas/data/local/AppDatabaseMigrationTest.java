package com.example.findcheapgas.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * DB 버전을 올려도 앱이 열리고 데이터(즐겨찾기 등)가 지켜지는지 확인한다. 기준은 app/schemas에 내보낸 스키마 JSON이다.
 * 실제 기기나 에뮬레이터에서 connectedDebugAndroidTest로 실행한다.
 */
@RunWith(AndroidJUnit4.class)
public class AppDatabaseMigrationTest {

    private static final String TEST_DB = "migration-test.db";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @After
    public void tearDown() {
        context.deleteDatabase(TEST_DB);
    }

    /** 버전 3 DB를 4로 올리면 즐겨찾기 표가 생기고, 스키마가 4.json과 같다. */
    @Test
    public void migrate3To4_createsFavoriteTable() throws Exception {
        try (SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 3)) {
            ContentValues cache = new ContentValues();
            cache.put("cacheKey", "detail|mock|A0900001");
            cache.put("detailJson", "{}");
            cache.put("savedAt", 1L);
            db.insert("station_detail_cache", SQLiteDatabase.CONFLICT_REPLACE, cache);
        }

        // validateDroppedTables = true: 4.json과 표·열이 하나라도 다르면 실패한다.
        try (SupportSQLiteDatabase db = helper.runMigrationsAndValidate(TEST_DB, 4, true,
                AppDatabase.MIGRATION_3_4)) {
            assertTrue(tableExists(db, "favorite_station"));
            // 3 → 4는 캐시를 지우지 않는다.
            assertEquals(1, count(db, "station_detail_cache"));
        }
    }

    /**
     * 버전 4 DB에 즐겨찾기를 넣고 5로 올려도 즐겨찾기가 그대로 남고, 집·회사 위치 표가 생긴다. 스키마는 5.json과 같다.
     * 위치는 실제 주소 대신 공공 장소(선릉역)를 쓴다.
     */
    @Test
    public void migrate4To5_keepsFavoritesAndCreatesSavedPlaceTable() throws Exception {
        try (SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 4)) {
            ContentValues favorite = new ContentValues();
            favorite.put("stationId", "A0900001");
            favorite.put("name", "테스트주유소");
            favorite.put("brandCode", "SKE");
            favorite.put("address", "서울 강남구 테헤란로");
            favorite.put("katecX", 314000.5);
            favorite.put("katecY", 544000.25);
            favorite.put("latitude", 37.5);
            favorite.put("longitude", 127.03);
            favorite.put("savedAt", 1234L);
            db.insert("favorite_station", SQLiteDatabase.CONFLICT_REPLACE, favorite);

            ContentValues cache = new ContentValues();
            cache.put("cacheKey", "detail|mock|A0900001");
            cache.put("detailJson", "{}");
            cache.put("savedAt", 1L);
            db.insert("station_detail_cache", SQLiteDatabase.CONFLICT_REPLACE, cache);
        }

        try (SupportSQLiteDatabase db = helper.runMigrationsAndValidate(TEST_DB, 5, true,
                AppDatabase.MIGRATION_4_5)) {
            assertTrue(tableExists(db, "saved_place"));
            assertEquals(0, count(db, "saved_place"));
            assertEquals(1, count(db, "station_detail_cache"));
            try (Cursor cursor = db.query("SELECT stationId, name, brandCode, address, katecX, katecY, "
                    + "latitude, longitude, savedAt FROM favorite_station")) {
                assertEquals(1, cursor.getCount());
                cursor.moveToFirst();
                assertEquals("A0900001", cursor.getString(0));
                assertEquals("테스트주유소", cursor.getString(1));
                assertEquals("SKE", cursor.getString(2));
                assertEquals("서울 강남구 테헤란로", cursor.getString(3));
                assertEquals(314000.5, cursor.getDouble(4), 0.0);
                assertEquals(544000.25, cursor.getDouble(5), 0.0);
                assertEquals(37.5, cursor.getDouble(6), 0.0);
                assertEquals(127.03, cursor.getDouble(7), 0.0);
                assertEquals(1234L, cursor.getLong(8));
            }
        }
    }

    /** 앱이 쓰는 설정(AppDatabase.builder)으로 4 → 5 DB를 열고, 즐겨찾기는 남아 있고 집·회사 위치를 저장하고 읽을 수 있다. */
    @Test
    public void migrate4To5_openWithAppBuilder() throws Exception {
        try (SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 4)) {
            ContentValues favorite = new ContentValues();
            favorite.put("stationId", "A0900001");
            favorite.put("name", "테스트주유소");
            favorite.put("katecX", 314000);
            favorite.put("katecY", 544000);
            favorite.put("latitude", 37.5);
            favorite.put("longitude", 127.03);
            favorite.put("savedAt", 1000L);
            db.insert("favorite_station", SQLiteDatabase.CONFLICT_REPLACE, favorite);
        }

        AppDatabase db = AppDatabase.builder(context, TEST_DB).build();
        helper.closeWhenFinished(db);
        assertEquals(1, db.favoriteStationDao().count());

        SavedPlaceDao dao = db.savedPlaceDao();
        dao.save(new SavedPlaceEntity("HOME", "선릉역", "서울 강남구 선릉로 지하 580", 37.5045, 127.0490, 2000L));
        dao.save(new SavedPlaceEntity("HOME", "양재역", null, 37.4844, 127.0343, 3000L));
        // 종류마다 하나: 같은 종류로 다시 저장하면 바뀐다.
        assertEquals("양재역", dao.find("HOME").name);
        assertEquals(1, dao.delete("HOME"));
        assertNull(dao.find("HOME"));
    }

    /** 앱이 쓰는 설정(AppDatabase.builder)으로 3 → 5 DB를 열고, 즐겨찾기를 저장하고 읽을 수 있다. */
    @Test
    public void migrate3To5_openWithAppBuilder() throws Exception {
        helper.createDatabase(TEST_DB, 3).close();

        AppDatabase db = AppDatabase.builder(context, TEST_DB).build();
        helper.closeWhenFinished(db);
        FavoriteStationDao dao = db.favoriteStationDao();
        dao.insert(new FavoriteStationEntity("A0900001", "테스트주유소", "SKE", null,
                314000, 544000, 37.5, 127.03, 1000L));
        assertEquals(1, dao.count());
        assertEquals("테스트주유소", dao.find("A0900001").name);
    }

    /** 캐시만 있던 버전 2 DB는 지우고 지금 버전(5)으로 새로 만든다 (fallbackToDestructiveMigrationFrom(1, 2)). */
    @Test
    public void version2_isRecreated() {
        File file = context.getDatabasePath(TEST_DB);
        file.getParentFile().mkdirs();
        try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(file, null)) {
            old.execSQL("CREATE TABLE station_cache (cacheKey TEXT PRIMARY KEY NOT NULL, stationsJson TEXT NOT NULL, "
                    + "savedAt INTEGER NOT NULL)");
            old.setVersion(2);
        }

        AppDatabase db = AppDatabase.builder(context, TEST_DB).build();
        helper.closeWhenFinished(db);
        SupportSQLiteDatabase opened = db.getOpenHelper().getWritableDatabase();
        assertEquals(5, opened.getVersion());
        assertTrue(tableExists(opened, "favorite_station"));
        assertTrue(tableExists(opened, "saved_place"));
        assertEquals(0, db.favoriteStationDao().count());
    }

    private static boolean tableExists(SupportSQLiteDatabase db, String table) {
        try (Cursor cursor = db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                new Object[]{table})) {
            return cursor.moveToFirst();
        }
    }

    private static int count(SupportSQLiteDatabase db, String table) {
        try (Cursor cursor = db.query("SELECT COUNT(*) FROM " + table)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }
}
