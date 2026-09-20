package app.havalh3.telemetry;

import android.content.Context;
import android.content.SharedPreferences;

final class CameraSettings {
    static final String SOURCE_FRONT_BUMPER = "front_bumper";
    static final String SOURCE_WINDSHIELD = "windshield";
    static final String SOURCE_REAR = "rear";

    private static final String PREFS = "camera_settings";
    private static final String CAMERA_ID = "camera_id";
    private static final String FEATURE_ENABLED = "feature_enabled";
    private static final String SOURCE = "source";

    static boolean isFeatureEnabled(Context context) {
        return prefs(context).getBoolean(FEATURE_ENABLED, false);
    }

    static void setFeatureEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(FEATURE_ENABLED, enabled).apply();
    }

    static String getSource(Context context) {
        return prefs(context).getString(SOURCE, SOURCE_FRONT_BUMPER);
    }

    static void setSource(Context context, String source) {
        prefs(context).edit().putString(SOURCE, source).apply();
    }

    static String sourceLabel(Context context) {
        String source = getSource(context);
        if (SOURCE_REAR.equals(source)) return "Задняя камера";
        if (SOURCE_WINDSHIELD.equals(source)) return "Верхняя камера на лобовом стекле";
        return "Передняя нижняя камера";
    }

    static String cameraIdForSource(Context context) {
        String source = getSource(context);
        if (SOURCE_REAR.equals(source)) return "0";
        if (SOURCE_FRONT_BUMPER.equals(source)) return "1";
        // Android Camera2 ID 2 is a test-pattern generator, not the windshield camera.
        return null;
    }

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
