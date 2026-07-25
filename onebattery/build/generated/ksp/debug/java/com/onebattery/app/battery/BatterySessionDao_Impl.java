package com.onebattery.app.battery;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class BatterySessionDao_Impl implements BatterySessionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BatterySessionEntity> __insertionAdapterOfBatterySessionEntity;

  private final EntityInsertionAdapter<BatterySampleEntity> __insertionAdapterOfBatterySampleEntity;

  private final SharedSQLiteStatement __preparedStmtOfClearSessions;

  private final SharedSQLiteStatement __preparedStmtOfClearSamples;

  public BatterySessionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBatterySessionEntity = new EntityInsertionAdapter<BatterySessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `battery_sessions` (`id`,`kind`,`startedAt`,`endedAt`,`startPercent`,`endPercent`,`startChargeMah`,`endChargeMah`,`estimatedFullMah`,`deepSleepPercent`,`screenOffMs`,`deepSleepMs`,`note`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BatterySessionEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getKind());
        statement.bindLong(3, entity.getStartedAt());
        statement.bindLong(4, entity.getEndedAt());
        statement.bindLong(5, entity.getStartPercent());
        statement.bindLong(6, entity.getEndPercent());
        statement.bindLong(7, entity.getStartChargeMah());
        statement.bindLong(8, entity.getEndChargeMah());
        statement.bindLong(9, entity.getEstimatedFullMah());
        statement.bindLong(10, entity.getDeepSleepPercent());
        statement.bindLong(11, entity.getScreenOffMs());
        statement.bindLong(12, entity.getDeepSleepMs());
        statement.bindString(13, entity.getNote());
      }
    };
    this.__insertionAdapterOfBatterySampleEntity = new EntityInsertionAdapter<BatterySampleEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `battery_samples` (`id`,`sessionId`,`at`,`percent`,`chargeMah`,`screenOn`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BatterySampleEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getSessionId());
        statement.bindLong(3, entity.getAt());
        statement.bindLong(4, entity.getPercent());
        statement.bindLong(5, entity.getChargeMah());
        final int _tmp = entity.getScreenOn() ? 1 : 0;
        statement.bindLong(6, _tmp);
      }
    };
    this.__preparedStmtOfClearSessions = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM battery_sessions";
        return _query;
      }
    };
    this.__preparedStmtOfClearSamples = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM battery_samples";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final BatterySessionEntity entity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBatterySessionEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSample(final BatterySampleEntity sample,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBatterySampleEntity.insert(sample);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object clearSessions(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearSessions.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearSessions.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearSamples(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearSamples.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearSamples.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BatterySessionEntity>> observeAll() {
    final String _sql = "SELECT * FROM battery_sessions ORDER BY endedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"battery_sessions"}, new Callable<List<BatterySessionEntity>>() {
      @Override
      @NonNull
      public List<BatterySessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfStartPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "startPercent");
          final int _cursorIndexOfEndPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "endPercent");
          final int _cursorIndexOfStartChargeMah = CursorUtil.getColumnIndexOrThrow(_cursor, "startChargeMah");
          final int _cursorIndexOfEndChargeMah = CursorUtil.getColumnIndexOrThrow(_cursor, "endChargeMah");
          final int _cursorIndexOfEstimatedFullMah = CursorUtil.getColumnIndexOrThrow(_cursor, "estimatedFullMah");
          final int _cursorIndexOfDeepSleepPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "deepSleepPercent");
          final int _cursorIndexOfScreenOffMs = CursorUtil.getColumnIndexOrThrow(_cursor, "screenOffMs");
          final int _cursorIndexOfDeepSleepMs = CursorUtil.getColumnIndexOrThrow(_cursor, "deepSleepMs");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final List<BatterySessionEntity> _result = new ArrayList<BatterySessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BatterySessionEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final long _tmpEndedAt;
            _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            final int _tmpStartPercent;
            _tmpStartPercent = _cursor.getInt(_cursorIndexOfStartPercent);
            final int _tmpEndPercent;
            _tmpEndPercent = _cursor.getInt(_cursorIndexOfEndPercent);
            final int _tmpStartChargeMah;
            _tmpStartChargeMah = _cursor.getInt(_cursorIndexOfStartChargeMah);
            final int _tmpEndChargeMah;
            _tmpEndChargeMah = _cursor.getInt(_cursorIndexOfEndChargeMah);
            final int _tmpEstimatedFullMah;
            _tmpEstimatedFullMah = _cursor.getInt(_cursorIndexOfEstimatedFullMah);
            final int _tmpDeepSleepPercent;
            _tmpDeepSleepPercent = _cursor.getInt(_cursorIndexOfDeepSleepPercent);
            final long _tmpScreenOffMs;
            _tmpScreenOffMs = _cursor.getLong(_cursorIndexOfScreenOffMs);
            final long _tmpDeepSleepMs;
            _tmpDeepSleepMs = _cursor.getLong(_cursorIndexOfDeepSleepMs);
            final String _tmpNote;
            _tmpNote = _cursor.getString(_cursorIndexOfNote);
            _item = new BatterySessionEntity(_tmpId,_tmpKind,_tmpStartedAt,_tmpEndedAt,_tmpStartPercent,_tmpEndPercent,_tmpStartChargeMah,_tmpEndChargeMah,_tmpEstimatedFullMah,_tmpDeepSleepPercent,_tmpScreenOffMs,_tmpDeepSleepMs,_tmpNote);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<BatterySessionEntity>> observeKind(final String kind) {
    final String _sql = "SELECT * FROM battery_sessions WHERE kind = ? ORDER BY endedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, kind);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"battery_sessions"}, new Callable<List<BatterySessionEntity>>() {
      @Override
      @NonNull
      public List<BatterySessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfStartPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "startPercent");
          final int _cursorIndexOfEndPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "endPercent");
          final int _cursorIndexOfStartChargeMah = CursorUtil.getColumnIndexOrThrow(_cursor, "startChargeMah");
          final int _cursorIndexOfEndChargeMah = CursorUtil.getColumnIndexOrThrow(_cursor, "endChargeMah");
          final int _cursorIndexOfEstimatedFullMah = CursorUtil.getColumnIndexOrThrow(_cursor, "estimatedFullMah");
          final int _cursorIndexOfDeepSleepPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "deepSleepPercent");
          final int _cursorIndexOfScreenOffMs = CursorUtil.getColumnIndexOrThrow(_cursor, "screenOffMs");
          final int _cursorIndexOfDeepSleepMs = CursorUtil.getColumnIndexOrThrow(_cursor, "deepSleepMs");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final List<BatterySessionEntity> _result = new ArrayList<BatterySessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BatterySessionEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final long _tmpEndedAt;
            _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            final int _tmpStartPercent;
            _tmpStartPercent = _cursor.getInt(_cursorIndexOfStartPercent);
            final int _tmpEndPercent;
            _tmpEndPercent = _cursor.getInt(_cursorIndexOfEndPercent);
            final int _tmpStartChargeMah;
            _tmpStartChargeMah = _cursor.getInt(_cursorIndexOfStartChargeMah);
            final int _tmpEndChargeMah;
            _tmpEndChargeMah = _cursor.getInt(_cursorIndexOfEndChargeMah);
            final int _tmpEstimatedFullMah;
            _tmpEstimatedFullMah = _cursor.getInt(_cursorIndexOfEstimatedFullMah);
            final int _tmpDeepSleepPercent;
            _tmpDeepSleepPercent = _cursor.getInt(_cursorIndexOfDeepSleepPercent);
            final long _tmpScreenOffMs;
            _tmpScreenOffMs = _cursor.getLong(_cursorIndexOfScreenOffMs);
            final long _tmpDeepSleepMs;
            _tmpDeepSleepMs = _cursor.getLong(_cursorIndexOfDeepSleepMs);
            final String _tmpNote;
            _tmpNote = _cursor.getString(_cursorIndexOfNote);
            _item = new BatterySessionEntity(_tmpId,_tmpKind,_tmpStartedAt,_tmpEndedAt,_tmpStartPercent,_tmpEndPercent,_tmpStartChargeMah,_tmpEndChargeMah,_tmpEstimatedFullMah,_tmpDeepSleepPercent,_tmpScreenOffMs,_tmpDeepSleepMs,_tmpNote);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object all(final Continuation<? super List<BatterySessionEntity>> $completion) {
    final String _sql = "SELECT * FROM battery_sessions ORDER BY endedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<BatterySessionEntity>>() {
      @Override
      @NonNull
      public List<BatterySessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfStartPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "startPercent");
          final int _cursorIndexOfEndPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "endPercent");
          final int _cursorIndexOfStartChargeMah = CursorUtil.getColumnIndexOrThrow(_cursor, "startChargeMah");
          final int _cursorIndexOfEndChargeMah = CursorUtil.getColumnIndexOrThrow(_cursor, "endChargeMah");
          final int _cursorIndexOfEstimatedFullMah = CursorUtil.getColumnIndexOrThrow(_cursor, "estimatedFullMah");
          final int _cursorIndexOfDeepSleepPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "deepSleepPercent");
          final int _cursorIndexOfScreenOffMs = CursorUtil.getColumnIndexOrThrow(_cursor, "screenOffMs");
          final int _cursorIndexOfDeepSleepMs = CursorUtil.getColumnIndexOrThrow(_cursor, "deepSleepMs");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final List<BatterySessionEntity> _result = new ArrayList<BatterySessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BatterySessionEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final long _tmpEndedAt;
            _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            final int _tmpStartPercent;
            _tmpStartPercent = _cursor.getInt(_cursorIndexOfStartPercent);
            final int _tmpEndPercent;
            _tmpEndPercent = _cursor.getInt(_cursorIndexOfEndPercent);
            final int _tmpStartChargeMah;
            _tmpStartChargeMah = _cursor.getInt(_cursorIndexOfStartChargeMah);
            final int _tmpEndChargeMah;
            _tmpEndChargeMah = _cursor.getInt(_cursorIndexOfEndChargeMah);
            final int _tmpEstimatedFullMah;
            _tmpEstimatedFullMah = _cursor.getInt(_cursorIndexOfEstimatedFullMah);
            final int _tmpDeepSleepPercent;
            _tmpDeepSleepPercent = _cursor.getInt(_cursorIndexOfDeepSleepPercent);
            final long _tmpScreenOffMs;
            _tmpScreenOffMs = _cursor.getLong(_cursorIndexOfScreenOffMs);
            final long _tmpDeepSleepMs;
            _tmpDeepSleepMs = _cursor.getLong(_cursorIndexOfDeepSleepMs);
            final String _tmpNote;
            _tmpNote = _cursor.getString(_cursorIndexOfNote);
            _item = new BatterySessionEntity(_tmpId,_tmpKind,_tmpStartedAt,_tmpEndedAt,_tmpStartPercent,_tmpEndPercent,_tmpStartChargeMah,_tmpEndChargeMah,_tmpEstimatedFullMah,_tmpDeepSleepPercent,_tmpScreenOffMs,_tmpDeepSleepMs,_tmpNote);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object recentChargeEstimates(final int limit,
      final Continuation<? super List<Integer>> $completion) {
    final String _sql = "\n"
            + "        SELECT estimatedFullMah FROM battery_sessions\n"
            + "        WHERE kind = 'CHARGE' AND estimatedFullMah > 0\n"
            + "        ORDER BY endedAt DESC LIMIT ?\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Integer>>() {
      @Override
      @NonNull
      public List<Integer> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<Integer> _result = new ArrayList<Integer>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Integer _item;
            _item = _cursor.getInt(0);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object samplesFor(final String sessionId,
      final Continuation<? super List<BatterySampleEntity>> $completion) {
    final String _sql = "SELECT * FROM battery_samples WHERE sessionId = ? ORDER BY at ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<BatterySampleEntity>>() {
      @Override
      @NonNull
      public List<BatterySampleEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfAt = CursorUtil.getColumnIndexOrThrow(_cursor, "at");
          final int _cursorIndexOfPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "percent");
          final int _cursorIndexOfChargeMah = CursorUtil.getColumnIndexOrThrow(_cursor, "chargeMah");
          final int _cursorIndexOfScreenOn = CursorUtil.getColumnIndexOrThrow(_cursor, "screenOn");
          final List<BatterySampleEntity> _result = new ArrayList<BatterySampleEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BatterySampleEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final long _tmpAt;
            _tmpAt = _cursor.getLong(_cursorIndexOfAt);
            final int _tmpPercent;
            _tmpPercent = _cursor.getInt(_cursorIndexOfPercent);
            final int _tmpChargeMah;
            _tmpChargeMah = _cursor.getInt(_cursorIndexOfChargeMah);
            final boolean _tmpScreenOn;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfScreenOn);
            _tmpScreenOn = _tmp != 0;
            _item = new BatterySampleEntity(_tmpId,_tmpSessionId,_tmpAt,_tmpPercent,_tmpChargeMah,_tmpScreenOn);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BatterySampleEntity>> observeSamples(final String sessionId) {
    final String _sql = "SELECT * FROM battery_samples WHERE sessionId = ? ORDER BY at ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sessionId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"battery_samples"}, new Callable<List<BatterySampleEntity>>() {
      @Override
      @NonNull
      public List<BatterySampleEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfAt = CursorUtil.getColumnIndexOrThrow(_cursor, "at");
          final int _cursorIndexOfPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "percent");
          final int _cursorIndexOfChargeMah = CursorUtil.getColumnIndexOrThrow(_cursor, "chargeMah");
          final int _cursorIndexOfScreenOn = CursorUtil.getColumnIndexOrThrow(_cursor, "screenOn");
          final List<BatterySampleEntity> _result = new ArrayList<BatterySampleEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BatterySampleEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final long _tmpAt;
            _tmpAt = _cursor.getLong(_cursorIndexOfAt);
            final int _tmpPercent;
            _tmpPercent = _cursor.getInt(_cursorIndexOfPercent);
            final int _tmpChargeMah;
            _tmpChargeMah = _cursor.getInt(_cursorIndexOfChargeMah);
            final boolean _tmpScreenOn;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfScreenOn);
            _tmpScreenOn = _tmp != 0;
            _item = new BatterySampleEntity(_tmpId,_tmpSessionId,_tmpAt,_tmpPercent,_tmpChargeMah,_tmpScreenOn);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
