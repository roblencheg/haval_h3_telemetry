package app.havalh3.telemetry;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "H3TelemetryBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!isSupportedAction(action)) return;

        boolean enabled = OverlaySettings.isAutoStartEnabled(context);
        Log.i(TAG, "Received " + action + ", autoStart=" + enabled);
        if (!enabled) return;

        Intent service = new Intent(context, TelemetryService.class)
                .setAction(TelemetryService.ACTION_SHOW_CLUSTER);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (Throwable error) {
            Log.e(TAG, "Unable to start telemetry service after " + action, error);
        }
    }

    private static boolean isSupportedAction(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action);
    }
}
