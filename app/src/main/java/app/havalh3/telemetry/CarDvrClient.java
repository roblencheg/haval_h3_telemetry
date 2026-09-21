package app.havalh3.telemetry;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Reflective client for the undocumented GWM DVR service shipped in android.car.jar. */
final class CarDvrClient {
    interface Callback {
        void onStatus(String message);
        void onRtspAddress(String address);
    }

    private static final String CALLBACK_INTERFACE =
            "android.car.hardware.camera.dvr.ICarDvrEventCallback";
    private static final String DVR_STUB =
            "android.car.hardware.camera.dvr.ICarDvrService$Stub";
    private static final String DVR_IDS =
            "android.car.hardware.camera.dvr.ids.GwmDvrIdDefine";
    private static final String PREVIEW_REQUEST =
            "android.car.hardware.camera.dvr.adapter.requests.GwmPreview";

    private final Context context;
    private final Callback callback;
    private final HandlerThread thread = new HandlerThread("H3CarDvr");
    private Handler worker;
    private Object dvrService;
    private Object eventCallback;
    private String stopRequestId;
    private int[] previewPayload;
    private volatile IBinder carBinder;
    private ServiceConnection carConnection;
    private boolean carServiceBound;

    CarDvrClient(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    void startFrontPreview() {
        if (!thread.isAlive()) {
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        worker.removeCallbacksAndMessages(null);
        worker.post(this::connectAndStart);
    }

    void stop() {
        if (worker != null) worker.post(this::stopInternal);
    }

    void release() {
        if (worker == null) return;
        worker.post(() -> {
            stopInternal();
            thread.quitSafely();
        });
        worker = null;
    }

    private void connectAndStart() {
        try {
            IBinder carBinder = bindCarService();
            if (carBinder == null) throw new IllegalStateException("car_service не найден");

            Class<?> carStub = Class.forName("android.car.ICar$Stub");
            Object car = invokeStatic(carStub, "asInterface", carBinder);
            IBinder dvrBinder = (IBinder) invoke(car, "getCarService", "car_dvr");
            if (dvrBinder == null) throw new IllegalStateException("car_dvr не найден");
            DiagnosticsStore.record(context,
                    "car_dvr binder descriptor=" + dvrBinder.getInterfaceDescriptor());

            Class<?> dvrStub = Class.forName(DVR_STUB);
            dvrService = invokeStatic(dvrStub, "asInterface", dvrBinder);
            registerCallback();

            String startRequestId = staticValue(DVR_IDS, "REQUEST_START_PREVIEW");
            stopRequestId = staticValue(DVR_IDS, "REQUEST_STOP_PREVIEW");
            previewPayload = buildPreviewPayload(1); // DVR ID 1 = upper/front recorder camera.
            DiagnosticsStore.record(context, "car_dvr preview startId=" + startRequestId
                    + " stopId=" + stopRequestId
                    + " payload=" + Arrays.toString(previewPayload));
            callback.onStatus("Подключение верхней камеры…");
            Object result = invoke(dvrService, "request", startRequestId, previewPayload);
            recordEvent("car_dvr start result", result == null ? null : String.valueOf(result));
            parseEvent(result == null ? null : new String[]{String.valueOf(result)});
        } catch (Throwable error) {
            Throwable cause = rootCause(error);
            DiagnosticsStore.record(context, "car_dvr start failed=" + cause);
            callback.onStatus("Верхняя камера недоступна: "
                    + cause.getClass().getSimpleName());
        }
    }

    private void registerCallback() throws Exception {
        Class<?> callbackClass = Class.forName(CALLBACK_INTERFACE);
        Binder callbackBinder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    if (reply != null) reply.writeString(CALLBACK_INTERFACE);
                    return true;
                }
                if (code == 1) {
                    data.enforceInterface(CALLBACK_INTERFACE);
                    parseEvent(data.createStringArray());
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        ClassLoader loader = callbackClass.getClassLoader();
        if (loader == null) loader = context.getClassLoader();
        eventCallback = Proxy.newProxyInstance(loader,
                new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                    if ("asBinder".equals(method.getName())) return callbackBinder;
                    if ("onEvent".equals(method.getName())) {
                        parseEvent(args == null || args.length == 0
                                ? null : (String[]) args[0]);
                        return null;
                    }
                    if ("toString".equals(method.getName())) return "H3CarDvrCallback";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    return null;
                });
        invoke(dvrService, "registerEventCallback", context.getPackageName(), eventCallback);
        DiagnosticsStore.record(context, "car_dvr callback registered");
    }

    private int[] buildPreviewPayload(int dvrId) throws Exception {
        Class<?> preview = Class.forName(PREVIEW_REQUEST);
        Object builder = invokeStatic(preview, "builder");
        invoke(builder, "setDvrId", dvrId);
        Object request = invoke(builder, "build");
        return (int[]) invoke(request, "toArray");
    }

    private void parseEvent(String[] event) {
        DiagnosticsStore.record(context, "car_dvr event=" + Arrays.toString(event));
        if (event == null) return;
        for (String item : event) {
            if (item == null) continue;
            int start = item.indexOf("rtsp://");
            if (start >= 0) {
                String address = item.substring(start).trim();
                callback.onRtspAddress(address);
                return;
            }
        }
    }

    private void stopInternal() {
        try {
            if (dvrService != null && stopRequestId != null && previewPayload != null) {
                Object result = invoke(dvrService, "request", stopRequestId, previewPayload);
                recordEvent("car_dvr stop result", result == null ? null : String.valueOf(result));
            }
            if (dvrService != null && eventCallback != null) {
                invoke(dvrService, "unRegisterEventCallback", eventCallback);
            }
        } catch (Throwable error) {
            DiagnosticsStore.record(context, "car_dvr stop failed=" + rootCause(error));
        } finally {
            dvrService = null;
            eventCallback = null;
            unbindCarService();
        }
    }

    private void recordEvent(String label, String value) {
        DiagnosticsStore.record(context, label + "=" + value);
    }

    private IBinder bindCarService() throws Exception {
        unbindCarService();
        CountDownLatch connected = new CountDownLatch(1);
        carConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                carBinder = service;
                DiagnosticsStore.record(context,
                        "CarService connected component=" + name);
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                carBinder = null;
                DiagnosticsStore.record(context,
                        "CarService disconnected component=" + name);
            }
        };

        Intent implicit = new Intent("android.car.ICar").setPackage("com.android.car");
        boolean accepted = context.bindService(
                implicit, carConnection, Context.BIND_AUTO_CREATE);
        DiagnosticsStore.record(context,
                "CarService action bind accepted=" + accepted);
        if (!accepted) {
            Intent explicit = new Intent().setComponent(
                    new ComponentName("com.android.car", "com.android.car.CarService"));
            accepted = context.bindService(
                    explicit, carConnection, Context.BIND_AUTO_CREATE);
            DiagnosticsStore.record(context,
                    "CarService explicit bind accepted=" + accepted);
        }
        carServiceBound = accepted;
        if (!accepted) throw new IllegalStateException("CarService отклонил подключение");
        if (!connected.await(4, TimeUnit.SECONDS) || carBinder == null) {
            unbindCarService();
            throw new IllegalStateException("CarService не ответил за 4 секунды");
        }
        return carBinder;
    }

    private void unbindCarService() {
        if (carServiceBound && carConnection != null) {
            try {
                context.unbindService(carConnection);
            } catch (Throwable error) {
                DiagnosticsStore.record(context,
                        "CarService unbind failed=" + rootCause(error));
            }
        }
        carServiceBound = false;
        carConnection = null;
        carBinder = null;
    }

    private String staticValue(String className, String fieldName) throws Exception {
        Class<?> type = Class.forName(className);
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                Object value = field.get(null);
                DiagnosticsStore.record(context,
                        "car_dvr constant " + field.getName() + "=" + value);
                if (fieldName.equals(field.getName())) return String.valueOf(value);
            }
        }
        throw new NoSuchFieldException(className + "." + fieldName);
    }

    private static Object invokeStatic(Class<?> type, String name, Object... args) throws Exception {
        return invokeOn(type, null, name, args);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        return invokeOn(target.getClass(), target, name, args);
    }

    private static Object invokeOn(Class<?> type, Object target, String name, Object... args)
            throws Exception {
        for (Method method : type.getMethods()) {
            if (!name.equals(method.getName()) || method.getParameterTypes().length != args.length) {
                continue;
            }
            if (compatible(method.getParameterTypes(), args)) {
                method.setAccessible(true);
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "/" + args.length);
    }

    private static boolean compatible(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) continue;
            Class<?> expected = box(types[i]);
            if (!expected.isAssignableFrom(args[i].getClass())) return false;
        }
        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable result = error;
        while (result.getCause() != null) result = result.getCause();
        return result;
    }
}
