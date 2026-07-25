package com.onebattery.app.battery;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class BatterySessionDatabase_Impl extends BatterySessionDatabase {
  private volatile BatterySessionDao _batterySessionDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `battery_sessions` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `startPercent` INTEGER NOT NULL, `endPercent` INTEGER NOT NULL, `startChargeMah` INTEGER NOT NULL, `endChargeMah` INTEGER NOT NULL, `estimatedFullMah` INTEGER NOT NULL, `deepSleepPercent` INTEGER NOT NULL, `screenOffMs` INTEGER NOT NULL, `deepSleepMs` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `battery_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `at` INTEGER NOT NULL, `percent` INTEGER NOT NULL, `chargeMah` INTEGER NOT NULL, `screenOn` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b3b0a59810b2608851fb26c3f354a38a')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `battery_sessions`");
        db.execSQL("DROP TABLE IF EXISTS `battery_samples`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsBatterySessions = new HashMap<String, TableInfo.Column>(13);
        _columnsBatterySessions.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("kind", new TableInfo.Column("kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("startedAt", new TableInfo.Column("startedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("endedAt", new TableInfo.Column("endedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("startPercent", new TableInfo.Column("startPercent", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("endPercent", new TableInfo.Column("endPercent", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("startChargeMah", new TableInfo.Column("startChargeMah", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("endChargeMah", new TableInfo.Column("endChargeMah", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("estimatedFullMah", new TableInfo.Column("estimatedFullMah", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("deepSleepPercent", new TableInfo.Column("deepSleepPercent", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("screenOffMs", new TableInfo.Column("screenOffMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("deepSleepMs", new TableInfo.Column("deepSleepMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySessions.put("note", new TableInfo.Column("note", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBatterySessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBatterySessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBatterySessions = new TableInfo("battery_sessions", _columnsBatterySessions, _foreignKeysBatterySessions, _indicesBatterySessions);
        final TableInfo _existingBatterySessions = TableInfo.read(db, "battery_sessions");
        if (!_infoBatterySessions.equals(_existingBatterySessions)) {
          return new RoomOpenHelper.ValidationResult(false, "battery_sessions(com.onebattery.app.battery.BatterySessionEntity).\n"
                  + " Expected:\n" + _infoBatterySessions + "\n"
                  + " Found:\n" + _existingBatterySessions);
        }
        final HashMap<String, TableInfo.Column> _columnsBatterySamples = new HashMap<String, TableInfo.Column>(6);
        _columnsBatterySamples.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySamples.put("sessionId", new TableInfo.Column("sessionId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySamples.put("at", new TableInfo.Column("at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySamples.put("percent", new TableInfo.Column("percent", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySamples.put("chargeMah", new TableInfo.Column("chargeMah", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatterySamples.put("screenOn", new TableInfo.Column("screenOn", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBatterySamples = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBatterySamples = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBatterySamples = new TableInfo("battery_samples", _columnsBatterySamples, _foreignKeysBatterySamples, _indicesBatterySamples);
        final TableInfo _existingBatterySamples = TableInfo.read(db, "battery_samples");
        if (!_infoBatterySamples.equals(_existingBatterySamples)) {
          return new RoomOpenHelper.ValidationResult(false, "battery_samples(com.onebattery.app.battery.BatterySampleEntity).\n"
                  + " Expected:\n" + _infoBatterySamples + "\n"
                  + " Found:\n" + _existingBatterySamples);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "b3b0a59810b2608851fb26c3f354a38a", "82c64b192f52b7bf07d43d0273821969");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "battery_sessions","battery_samples");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `battery_sessions`");
      _db.execSQL("DELETE FROM `battery_samples`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(BatterySessionDao.class, BatterySessionDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public BatterySessionDao dao() {
    if (_batterySessionDao != null) {
      return _batterySessionDao;
    } else {
      synchronized(this) {
        if(_batterySessionDao == null) {
          _batterySessionDao = new BatterySessionDao_Impl(this);
        }
        return _batterySessionDao;
      }
    }
  }
}
