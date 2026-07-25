/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.onebattery.app.battery;
public interface IBatteryShell extends android.os.IInterface
{
  /** Default implementation for IBatteryShell. */
  public static class Default implements com.onebattery.app.battery.IBatteryShell
  {
    @Override public java.lang.String ping() throws android.os.RemoteException
    {
      return null;
    }
    /**
     * Run `dumpsys batterystats` under the Shizuku shell UID and return truncated text.
     * Empty string on failure; detail may appear in [lastError].
     */
    @Override public java.lang.String dumpBatteryStats(int maxChars) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String lastError() throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.onebattery.app.battery.IBatteryShell
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.onebattery.app.battery.IBatteryShell interface,
     * generating a proxy if needed.
     */
    public static com.onebattery.app.battery.IBatteryShell asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.onebattery.app.battery.IBatteryShell))) {
        return ((com.onebattery.app.battery.IBatteryShell)iin);
      }
      return new com.onebattery.app.battery.IBatteryShell.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_ping:
        {
          java.lang.String _result = this.ping();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_dumpBatteryStats:
        {
          int _arg0;
          _arg0 = data.readInt();
          java.lang.String _result = this.dumpBatteryStats(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_lastError:
        {
          java.lang.String _result = this.lastError();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.onebattery.app.battery.IBatteryShell
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public java.lang.String ping() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ping, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /**
       * Run `dumpsys batterystats` under the Shizuku shell UID and return truncated text.
       * Empty string on failure; detail may appear in [lastError].
       */
      @Override public java.lang.String dumpBatteryStats(int maxChars) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(maxChars);
          boolean _status = mRemote.transact(Stub.TRANSACTION_dumpBatteryStats, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String lastError() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_lastError, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_ping = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_dumpBatteryStats = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_lastError = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
  }
  public static final java.lang.String DESCRIPTOR = "com.onebattery.app.battery.IBatteryShell";
  public java.lang.String ping() throws android.os.RemoteException;
  /**
   * Run `dumpsys batterystats` under the Shizuku shell UID and return truncated text.
   * Empty string on failure; detail may appear in [lastError].
   */
  public java.lang.String dumpBatteryStats(int maxChars) throws android.os.RemoteException;
  public java.lang.String lastError() throws android.os.RemoteException;
}
