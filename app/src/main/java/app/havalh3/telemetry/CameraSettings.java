package app.havalh3.telemetry;

import android.content.Context;
import android.content.SharedPreferences;

final class CameraSettings {
    private static final String PREFS = "camera_settings";
    private static final String CAMERA_ID = "camera_id";

    static String getCameraId(Context context) {
        return prefs(context).getString(CAMERA_ID, "1");
    }

    static void setCameraId(Context context, String cameraId) {
        prefs(context).edit().putString(CAMERA_ID, cameraId).apply();
    }

    private static SharedPreferences prefs(Context context) {
        Context storage = context.createDeviceProtectedStorageContext();
        return storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private CameraSettings() {
    }
}
