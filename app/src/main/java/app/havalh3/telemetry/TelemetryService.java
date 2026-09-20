package app.havalh3.telemetry;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

import java.lang.reflect.Method;

public final class TelemetryService extends Service implements GwmAdapterClient.Callback {
    static final String ACTION_START = "app.havalh3.telemetry.START";
    static final String ACTION_SHOW_CLUSTER = "app.havalh3.telemetry.SHOW_CLUSTER";
    static final String ACTION_HIDE_CLUSTER = "app.havalh3.telemetry.HIDE_CLUSTER";
    static final String ACTION_UPDATE = "app.havalh3.telemetry.UPDATE";
    static final String ACTION_SETTINGS_CHANGED = "app.havalh3.telemetry.SETTINGS_CHANGED";
    static final String ACTION_CAMERA_CHANGED = "app.havalh3.telemetry.CAMERA_CHANGED";

    private static final String TAG = "H3TelemetryService";
    private static final String CHANNEL_ID = "h3_telemetry";
    private static final int NOTIFICATION_ID = 203;
    private static final int MAX_CLUSTER_LAUNCH_ATTEMPTS = 120;
    private static final long CLUSTER_RETRY_DELAY_MS = 5_000L;
    private GwmAdapterClient client;
    private Handler mainHandler;
    private int clusterLaunchAttempts;
    private long lastCameraKeyAt;

    private final Runnable clusterLaunchTask = new Runnable() {
        @Override
        public void run() {
            if (launchCluster()) {
                clusterLaunchAttempts = 0;
                return;
            }
            clusterLaunchAttempts++;
            if (clusterLaunchAttempts < MAX_CLUSTER_LAUNCH_ATTEMPTS) {
                mainHandler.postDelayed(this, CLUSTER_RETRY_DELAY_MS);
            } else {
                Log.e(TAG, "Cluster launch stopped after " + clusterLaunchAttempts + " attempts");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Подключение к GWM"));
        mainHandler = new Handler(Looper.getMainLooper());
        client = new GwmAdapterClient(this, this);
        client.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        DiagnosticsStore.record(this, "service start action=" + action
                + " flags=" + flags + " startId=" + startId);
        if (ACTION_SHOW_CLUSTER.equals(action)) {
            requestClusterLaunch();
        } else if (ACTION_HIDE_CLUSTER.equals(action)) {
            cancelClusterLaunch();
            CameraState.setVisible(false);
            sendLocalAction(ACTION_CAMERA_CHANGED);
            sendLocalAction(ACTION_HIDE_CLUSTER);
        } else if (intent == null && OverlaySettings.isAutoStartEnabled(this)) {
            requestClusterLaunch();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        cancelClusterLaunch();
        CameraState.setVisible(false);
        if (client != null) client.stop();
        TelemetryStore.setConnected(false, "Сервис остановлен");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConnectionChanged(boolean connected, String message) {
        TelemetryStore.setConnected(connected, message);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(message));
        broadcastUpdate();
    }

    @Override
    public void onValue(String key, String value) {
        TelemetryStore.put(key, value);
        if (TelemetrySignals.SYSTEM_KEY_EVENT.equals(key)) {
            handleSystemKeyEvent(value);
        }
        broadcastUpdate();
    }

    private void handleSystemKeyEvent(String value) {
        if (value == null || !"[1007,1]".equals(value.replace(" ", ""))) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastCameraKeyAt < 500L) return;
        lastCameraKeyAt = now;
        boolean visible = CameraState.toggle();
        DiagnosticsStore.record(this,
                "steering camera key=1007 shortRelease cameraVisible=" + visible);
        mainHandler.post(() -> {
            if (visible) requestClusterLaunch();
            sendLocalAction(ACTION_CAMERA_CHANGED);
            if (visible) {
                mainHandler.postDelayed(() -> sendLocalAction(ACTION_CAMERA_CHANGED), 750L);
            }
        });
    }

    private void broadcastUpdate() {
        Intent update = new Intent(ACTION_UPDATE).setPackage(getPackageName());
        sendBroadcast(update);
    }

    private void sendLocalAction(String action) {
        sendBroadcast(new Intent(action).setPackage(getPackageName()));
    }

    private void requestClusterLaunch() {
        cancelClusterLaunch();
        clusterLaunchAttempts = 0;
        mainHandler.post(clusterLaunchTask);
    }

    private void cancelClusterLaunch() {
        if (mainHandler != null) mainHandler.removeCallbacks(clusterLaunchTask);
    }

    private boolean launchCluster() {
        try {
            DisplayManager manager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            Display target = manager.getDisplay(2);
            if (target == null) {
                for (Display display : manager.getDisplays()) {
                    if (display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                        target = display;
                        break;
                    }
                }
            }
            if (target == null) throw new IllegalStateException("Дополнительный дисплей не найден");

            Intent activity = new Intent(this, ClusterActivity.class)
                    .addFlags(0x1c010000);
            ActivityOptions options = ActivityOptions.makeBasic();
            try {
                Method method = ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class);
                method.invoke(options, 5);
            } catch (Throwable ignored) {
                Log.i(TAG, "Freeform mode is unavailable; using default windowing mode");
            }
            options.setLaunchBounds(new Rect(0, 0, 1920, 720));
            options.setLaunchDisplayId(target.getDisplayId());
            startActivity(activity, options.toBundle());
            Log.i(TAG, "Cluster activity launched on displayId=" + target.getDisplayId());
            DiagnosticsStore.record(this,
                    "cluster launched displayId=" + target.getDisplayId());
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "Cluster is not ready, attempt=" + (clusterLaunchAttempts + 1), error);
            DiagnosticsStore.record(this, "cluster launch failed attempt="
                    + (clusterLaunchAttempts + 1) + " error=" + error);
            TelemetryStore.setConnected(TelemetryStore.isConnected(),
                    "Ошибка запуска приборки: " + error.getClass().getSimpleName());
            broadcastUpdate();
            return false;
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Телеметрия Haval H3",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setShowBadge(false);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("H3 Телеметрия")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }
}
