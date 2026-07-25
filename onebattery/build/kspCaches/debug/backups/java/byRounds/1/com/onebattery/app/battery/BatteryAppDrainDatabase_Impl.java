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
public final class BatteryAppDrainDatabase_Impl extends BatteryAppDrainDatabase {
  private volatile BatteryAppDrainDao _batteryAppDrainDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `battery_app_drain` (`id` TEXT NOT NULL, `dayKey` TEXT NOT NULL, `packageName` TEXT NOT NULL, `label` TEXT NOT NULL, `mahTotal` REAL NOT NULL, `mahScreenOn` REAL NOT NULL, `mahScreenOff` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '8250dd64ac8c01adf8fddf754201cba0')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `battery_app_drain`");
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
        final HashMap<String, TableInfo.Column> _columnsBatteryAppDrain = new HashMap<String, TableInfo.Column>(8);
        _columnsBatteryAppDrain.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatteryAppDrain.put("dayKey", new TableInfo.Column("dayKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatteryAppDrain.put("packageName", new TableInfo.Column("packageName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatteryAppDrain.put("label", new TableInfo.Column("label", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatteryAppDrain.put("mahTotal", new TableInfo.Column("mahTotal", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatteryAppDrain.put("mahScreenOn", new TableInfo.Column("mahScreenOn", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatteryAppDrain.put("mahScreenOff", new TableInfo.Column("mahScreenOff", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBatteryAppDrain.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBatteryAppDrain = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBatteryAppDrain = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBatteryAppDrain = new TableInfo("battery_app_drain", _columnsBatteryAppDrain, _foreignKeysBatteryAppDrain, _indicesBatteryAppDrain);
        final TableInfo _existingBatteryAppDrain = TableInfo.read(db, "battery_app_drain");
        if (!_infoBatteryAppDrain.equals(_existingBatteryAppDrain)) {
          return new RoomOpenHelper.ValidationResult(false, "battery_app_drain(com.onebattery.app.battery.BatteryAppDrainEntity).\n"
                  + " Expected:\n" + _infoBatteryAppDrain + "\n"
                  + " Found:\n" + _existingBatteryAppDrain);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "8250dd64ac8c01adf8fddf754201cba0", "caa2d83fe1665ccd1db42c38dd1e8a16");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "battery_app_drain");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `battery_app_drain`");
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
    _typeConvertersMap.put(BatteryAppDrainDao.class, BatteryAppDrainDao_Impl.getRequiredConverters());
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
  public BatteryAppDrainDao dao() {
    if (_batteryAppDrainDao != null) {
      return _batteryAppDrainDao;
    } else {
      synchronized(this) {
        if(_batteryAppDrainDao == null) {
          _batteryAppDrainDao = new BatteryAppDrainDao_Impl(this);
        }
        return _batteryAppDrainDao;
      }
    }
  }
}
