package app.havalh3.telemetry;

import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.lang.reflect.Method;

final class GwmAdapterClient {
    interface Callback {
        void onConnectionChanged(boolean connected, String message);
        void onValue(String key, String value);
    }

    private static final String TAG = "H3GwmAdapter";
    private static final String SERVICE_NAME = "gwm_adapter";
    private static final String SERVICE_INTERFACE = "com.gwm.android.adapter.IGwmAdapterService";
    private static final String LISTENER_INTERFACE = "com.gwm.android.adapter.IDataChangedListener";
    private static final long POLL_INTERVAL_MS = 2_000L;

    private final Context context;
    private final Callback callback;
    private final HandlerThread workerThread = new HandlerThread("H3TelemetryPoll");
    private Handler worker;
    private IBinder service;
    private boolean registered;

    private final Binder listener = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(LISTENER_INTERFACE);
                return true;
            }
            if (code == 1) {
                data.enforceInterface(LISTENER_INTERFACE);
                String key = data.readString();
                String value = data.readString();
                if (key != null && value != null) callback.onValue(key, value);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    private final IBinder.DeathRecipient deathRecipient;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (service == null || !service.isBinderAlive()) {
                connect();
                return;
            }
            pollOnce();
            worker.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    GwmAdapterClient(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.deathRecipient = () -> {
            registered = false;
            service = null;
            this.callback.onConnectionChanged(false, "Сервис GWM отключился");
            if (worker != null) worker.postDelayed(this::connect, 2_000L);
        };
    }

    void start() {
        if (!workerThread.isAlive()) workerThread.start();
        worker = new Handler(workerThread.getLooper());
        worker.post(this::connect);
    }

    void stop() {
        if (worker != null) worker.removeCallbacksAndMessages(null);
        unregisterListener();
        if (service != null) service.unlinkToDeath(deathRecipient, 0);
        service = null;
        workerThread.quitSafely();
    }

    private void connect() {
        if (worker == null) return;
        worker.removeCallbacks(pollTask);
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getMethod("getService", String.class);
            service = (IBinder) getService.invoke(null, SERVICE_NAME);
            if (service == null || !service.isBinderAlive()) {
                callback.onConnectionChanged(false, "Сервис gwm_adapter не найден");
                worker.postDelayed(this::connect, 2_000L);
                return;
            }
            service.linkToDeath(deathRecipient, 0);
            int result = registerListener();
            registered = result >= 0;
            callback.onConnectionChanged(true, "GWM подключён, регистрация=" + result);
            pollOnce();
            worker.postDelayed(pollTask, POLL_INTERVAL_MS);
        } catch (Throwable error) {
            Log.e(TAG, "Connect failed", error);
            callback.onConnectionChanged(false, "Ошибка подключения: " + error.getClass().getSimpleName());
            worker.postDelayed(this::connect, 2_000L);
        }
    }

    private int registerListener() throws RemoteException {
        for (int attempt = 0; attempt < 3; attempt++) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SERVICE_INTERFACE);
                data.writeString(context.getPackageName());
                data.writeStringArray(TelemetrySignals.SUBSCRIPTIONS);
                data.writeStrongBinder(listener);
                if (!service.transact(3, data, reply, 0)) return -1;
                int status = reply.dataAvail() >= 4 ? reply.readInt() : 0;
                if (status == 0) return reply.dataAvail() >= 4 ? reply.readInt() : 0;
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
        return -2;
    }

    private void unregisterListener() {
        if (!registered || service == null || !service.isBinderAlive()) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_INTERFACE);
            data.writeString(context.getPackageName());
            data.writeStrongBinder(listener);
            service.transact(4, data, reply, 0);
        } catch (Throwable error) {
            Log.w(TAG, "Unregister failed", error);
        } finally {
            data.recycle();
            reply.recycle();
            registered = false;
        }
    }

    private void pollOnce() {
        if (service == null) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_INTERFACE);
            data.writeInt(1);
            data.writeString(context.getPackageName());
            data.writeStringArray(TelemetrySignals.ALL);
            data.writeStringArray(null);
            data.writeInt(1);
            if (!service.transact(1, data, reply, 0)) return;
            int status = reply.dataAvail() >= 4 ? reply.readInt() : -1;
            if (status != 0 || reply.dataAvail() <= 0) return;
            String[] values = reply.createStringArray();
            if (values == null) return;
            int count = Math.min(values.length, TelemetrySignals.ALL.length);
            for (int i = 0; i < count; i++) {
                if (values[i] != null) callback.onValue(TelemetrySignals.ALL[i], values[i]);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Poll failed", error);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
