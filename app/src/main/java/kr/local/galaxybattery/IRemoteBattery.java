/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: cmd not shown due to `--omit_invocation`
 */
package kr.local.galaxybattery;
public interface IRemoteBattery extends android.os.IInterface
{
  /** Default implementation for IRemoteBattery. */
  public static class Default implements kr.local.galaxybattery.IRemoteBattery
  {
    @Override public java.lang.String readBattery() throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String readHardware() throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String readThermal() throws android.os.RemoteException
    {
      return null;
    }
    @Override public void destroy() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements kr.local.galaxybattery.IRemoteBattery
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an kr.local.galaxybattery.IRemoteBattery interface,
     * generating a proxy if needed.
     */
    public static kr.local.galaxybattery.IRemoteBattery asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof kr.local.galaxybattery.IRemoteBattery))) {
        return ((kr.local.galaxybattery.IRemoteBattery)iin);
      }
      return new kr.local.galaxybattery.IRemoteBattery.Stub.Proxy(obj);
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
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_readBattery:
        {
          java.lang.String _result = this.readBattery();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_readHardware:
        {
          java.lang.String _result = this.readHardware();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_readThermal:
        {
          java.lang.String _result = this.readThermal();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_destroy:
        {
          this.destroy();
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements kr.local.galaxybattery.IRemoteBattery
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
      @Override public java.lang.String readBattery() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_readBattery, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String readHardware() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_readHardware, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String readThermal() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_readThermal, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void destroy() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_destroy, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_readBattery = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_readHardware = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_readThermal = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_destroy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777114);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "kr.local.galaxybattery.IRemoteBattery";
  public java.lang.String readBattery() throws android.os.RemoteException;
  public java.lang.String readHardware() throws android.os.RemoteException;
  public java.lang.String readThermal() throws android.os.RemoteException;
  public void destroy() throws android.os.RemoteException;
}
