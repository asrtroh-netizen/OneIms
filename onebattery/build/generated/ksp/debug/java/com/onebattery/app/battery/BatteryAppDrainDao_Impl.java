package com.onebattery.app.battery;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
public final class BatteryAppDrainDao_Impl implements BatteryAppDrainDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BatteryAppDrainEntity> __insertionAdapterOfBatteryAppDrainEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteBefore;

  public BatteryAppDrainDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBatteryAppDrainEntity = new EntityInsertionAdapter<BatteryAppDrainEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `battery_app_drain` (`id`,`dayKey`,`packageName`,`label`,`mahTotal`,`mahScreenOn`,`mahScreenOff`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BatteryAppDrainEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getDayKey());
        statement.bindString(3, entity.getPackageName());
        statement.bindString(4, entity.getLabel());
        statement.bindDouble(5, entity.getMahTotal());
        statement.bindDouble(6, entity.getMahScreenOn());
        statement.bindDouble(7, entity.getMahScreenOff());
        statement.bindLong(8, entity.getUpdatedAt());
      }
    };
    this.__preparedStmtOfDeleteBefore = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM battery_app_drain WHERE dayKey < ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final BatteryAppDrainEntity entity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBatteryAppDrainEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteBefore(final String dayKey, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteBefore.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, dayKey);
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
          __preparedStmtOfDeleteBefore.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BatteryAppDrainEntity>> observeDay(final String dayKey) {
    final String _sql = "\n"
            + "        SELECT * FROM battery_app_drain\n"
            + "        WHERE dayKey = ?\n"
            + "        ORDER BY mahTotal DESC\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, dayKey);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"battery_app_drain"}, new Callable<List<BatteryAppDrainEntity>>() {
      @Override
      @NonNull
      public List<BatteryAppDrainEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDayKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dayKey");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfMahTotal = CursorUtil.getColumnIndexOrThrow(_cursor, "mahTotal");
          final int _cursorIndexOfMahScreenOn = CursorUtil.getColumnIndexOrThrow(_cursor, "mahScreenOn");
          final int _cursorIndexOfMahScreenOff = CursorUtil.getColumnIndexOrThrow(_cursor, "mahScreenOff");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<BatteryAppDrainEntity> _result = new ArrayList<BatteryAppDrainEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BatteryAppDrainEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpDayKey;
            _tmpDayKey = _cursor.getString(_cursorIndexOfDayKey);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final double _tmpMahTotal;
            _tmpMahTotal = _cursor.getDouble(_cursorIndexOfMahTotal);
            final double _tmpMahScreenOn;
            _tmpMahScreenOn = _cursor.getDouble(_cursorIndexOfMahScreenOn);
            final double _tmpMahScreenOff;
            _tmpMahScreenOff = _cursor.getDouble(_cursorIndexOfMahScreenOff);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new BatteryAppDrainEntity(_tmpId,_tmpDayKey,_tmpPackageName,_tmpLabel,_tmpMahTotal,_tmpMahScreenOn,_tmpMahScreenOff,_tmpUpdatedAt);
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
  public Object get(final String id,
      final Continuation<? super BatteryAppDrainEntity> $completion) {
    final String _sql = "SELECT * FROM battery_app_drain WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BatteryAppDrainEntity>() {
      @Override
      @Nullable
      public BatteryAppDrainEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDayKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dayKey");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfMahTotal = CursorUtil.getColumnIndexOrThrow(_cursor, "mahTotal");
          final int _cursorIndexOfMahScreenOn = CursorUtil.getColumnIndexOrThrow(_cursor, "mahScreenOn");
          final int _cursorIndexOfMahScreenOff = CursorUtil.getColumnIndexOrThrow(_cursor, "mahScreenOff");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final BatteryAppDrainEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpDayKey;
            _tmpDayKey = _cursor.getString(_cursorIndexOfDayKey);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final double _tmpMahTotal;
            _tmpMahTotal = _cursor.getDouble(_cursorIndexOfMahTotal);
            final double _tmpMahScreenOn;
            _tmpMahScreenOn = _cursor.getDouble(_cursorIndexOfMahScreenOn);
            final double _tmpMahScreenOff;
            _tmpMahScreenOff = _cursor.getDouble(_cursorIndexOfMahScreenOff);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new BatteryAppDrainEntity(_tmpId,_tmpDayKey,_tmpPackageName,_tmpLabel,_tmpMahTotal,_tmpMahScreenOn,_tmpMahScreenOff,_tmpUpdatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
