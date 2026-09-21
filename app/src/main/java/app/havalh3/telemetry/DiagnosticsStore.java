package app.havalh3.telemetry;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DiagnosticsStore {
    static final String ACTION_CHANGED = "app.havalh3.telemetry.DIAGNOSTICS_CHANGED";
    private static final String TAG = "H3Diag";
    private static final String PREFS = "diagnostics";
    private static final String KEY_EVENTS = "key_events";
    private static final int MAX_LINES = 60;

    static void recordKeyEvent(Context context, String origin, KeyEvent event) {
        InputDevice device = InputDevice.getDevice(event.getDeviceId());
        String deviceName = device == null ? "unknown" : device.getName();
        String line = timestamp()
                + " origin=" + origin
                + " action=" + actionName(event.getAction())
                + " keyCode=" + event.getKeyCode()
                + "(" + KeyEvent.keyCodeToString(event.getKeyCode()) + ")"
                + " scanCode=" + event.getScanCode()
                + " repeat=" + event.getRepeatCount()
                + " long=" + event.isLongPress()
                + " deviceId=" + event.getDeviceId()
                + " source=0x" + Integer.toHexString(event.getSource())
                + " displayId=" + displayId(event)
                + " device=" + deviceName;
        append(context, line);
    }

    static void record(Context context, String message) {
        append(context, timestamp() + " " + message);
    }

    static String recentEvents(Context context) {
        return prefs(context).getString(KEY_EVENTS, "События ещё не зарегистрированы.");
    }

    static void clear(Context context) {
        prefs(context).edit().remove(KEY_EVENTS).apply();
        notifyChanged(context);
    }

    private static synchronized void append(Context context, String line) {
        Log.i(TAG, line);
        SharedPreferences preferences = prefs(context);
        String old = preferences.getString(KEY_EVENTS, "");
        String combined = line + (old.isEmpty() ? "" : "\n" + old);
        String[] lines = combined.split("\n");
        StringBuilder limited = new StringBuilder();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            if (i > 0) limited.append('\n');
            limited.append(lines[i]);
        }
        preferences.edit().putString(KEY_EVENTS, limited.toString()).apply();
        notifyChanged(context);
    }

    private static SharedPreferences prefs(Context context) {
        Context storage = context.createDeviceProtectedStorageContext();
        return storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void notifyChanged(Context context) {
        context.sendBroadcast(new android.content.Intent(ACTION_CHANGED)
                .setPackage(context.getPackageName()));
    }

    private static String timestamp() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static String actionName(int action) {
        if (action == KeyEvent.ACTION_DOWN) return "DOWN";
        if (action == KeyEvent.ACTION_UP) return "UP";
        if (action == KeyEvent.ACTION_MULTIPLE) return "MULTIPLE";
        return String.valueOf(action);
    }

    private static String displayId(KeyEvent event) {
        try {
            Object value = event.getClass().getMethod("getDisplayId").invoke(event);
            return String.valueOf(value);
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }

    private DiagnosticsStore() {
    }
}
