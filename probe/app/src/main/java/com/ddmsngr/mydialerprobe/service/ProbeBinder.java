package com.ddmsngr.mydialerprobe.service;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

public abstract class ProbeBinder extends Binder {
    private static final String DESCRIPTOR = "com.ddmsngr.mydialerprobe.service.ProbeBinder";
    private static final int TRANSACTION_START = IBinder.FIRST_CALL_TRANSACTION;
    private static final int TRANSACTION_STOP = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TRANSACTION_IDENTITY = IBinder.FIRST_CALL_TRANSACTION + 2;

    protected ProbeBinder() {
        attachInterface(null, DESCRIPTOR);
    }

    public abstract String startRecording(String path, int durationMs);
    public abstract String stopRecording();
    public abstract String identity();

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        if (code < TRANSACTION_START || code > TRANSACTION_IDENTITY) return super.onTransact(code, data, reply, flags);
        data.enforceInterface(DESCRIPTOR);
        String result;
        if (code == TRANSACTION_START) result = startRecording(data.readString(), data.readInt());
        else if (code == TRANSACTION_STOP) result = stopRecording();
        else result = identity();
        reply.writeNoException();
        reply.writeString(result);
        return true;
    }

    public static final class Proxy {
        private final IBinder remote;

        public Proxy(IBinder remote) {
            this.remote = remote;
        }

        public String startRecording(String path, int durationMs) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(path);
                data.writeInt(durationMs);
                remote.transact(TRANSACTION_START, data, reply, 0);
                reply.readException();
                return reply.readString();
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        public String stopRecording() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                remote.transact(TRANSACTION_STOP, data, reply, 0);
                reply.readException();
                return reply.readString();
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        public String identity() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                remote.transact(TRANSACTION_IDENTITY, data, reply, 0);
                reply.readException();
                return reply.readString();
            } finally {
                reply.recycle();
                data.recycle();
            }
        }
    }
}
