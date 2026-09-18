package app.havalh3.telemetry;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

final class OverlaySettings {
    static final String FUEL = TelemetrySignals.FUEL_PERCENT;
    static final String COOLANT = TelemetrySignals.COOLANT_TEMP;
    static final String BATTERY = TelemetrySignals.BATTERY_VOLTAGE;
    static final String[] ELEMENTS = TelemetrySignals.ALL;

    static final int DISPLAY_WIDTH = 1920;
    static final int DISPLAY_HEIGHT = 720;
    static final int ELEMENT_HEIGHT = 62;

    private static final String PREFS = "overlay_settings";
    private static final String BOOT_PREFS = "boot_settings";

    static boolean isAutoStartEnabled(Context context) {
        SharedPreferences bootPreferences = bootPrefs(context);
        if (bootPreferences.contains("auto_start")) {
            return bootPreferences.getBoolean("auto_start", true);
        }

        boolean enabled = true;
        try {
            enabled = prefs(context).getBoolean("auto_start", true);
        } catch (IllegalStateException ignored) {
            // Credential-protected preferences are unavailable during locked boot.
        }
        bootPreferences.edit().putBoolean("auto_start", enabled).apply();
        return enabled;
    }

    static void setAutoStartEnabled(Context context, boolean enabled) {
        bootPrefs(context).edit().putBoolean("auto_start", enabled).apply();
        prefs(context).edit().putBoolean("auto_start", enabled).apply();
    }

    static boolean isEnabled(Context context, String element) {
        return prefs(context).getBoolean(storageKey(element) + "_enabled", defaultEnabled(element));
    }

    static void setEnabled(Context context, String element, boolean enabled) {
        prefs(context).edit().putBoolean(storageKey(element) + "_enabled", enabled).apply();
    }

    static int getX(Context context, String element) {
        return prefs(context).getInt(storageKey(element) + "_x", defaultX(element));
    }

    static int getY(Context context, String element) {
        return prefs(context).getInt(storageKey(element) + "_y", defaultY(element));
    }

    static void setPosition(Context context, String element, int x, int y) {
        int safeX = clamp(x, 0, DISPLAY_WIDTH - width(element));
        int safeY = clamp(y, 0, DISPLAY_HEIGHT - ELEMENT_HEIGHT);
        prefs(context).edit()
                .putInt(storageKey(element) + "_x", safeX)
                .putInt(storageKey(element) + "_y", safeY)
                .apply();
    }

    static int getFontSize(Context context, String element) {
        return prefs(context).getInt(storageKey(element) + "_font_size", defaultFontSize(element));
    }

    static void setFontSize(Context context, String element, int sizePx) {
        prefs(context).edit()
                .putInt(storageKey(element) + "_font_size", clamp(sizePx, 18, 48))
                .apply();
    }

    static int width(String element) {
        if (FUEL.equals(element)) return 220;
        if (COOLANT.equals(element)) return 230;
        if (BATTERY.equals(element)) return 200;
        return 320;
    }

    static void resetPositions(Context context) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (String element : ELEMENTS) {
            editor.remove(storageKey(element) + "_x");
            editor.remove(storageKey(element) + "_y");
        }
        editor.apply();
    }

    static void resetElement(Context context, String element) {
        String key = storageKey(element);
        prefs(context).edit()
                .remove(key + "_enabled")
                .remove(key + "_x")
                .remove(key + "_y")
                .remove(key + "_font_size")
                .apply();
    }

    private static int defaultX(String element) {
        if (FUEL.equals(element)) return 160;
        if (COOLANT.equals(element)) return 1570;
        if (BATTERY.equals(element)) return 1720;
        return 800;
    }

    private static int defaultY(String element) {
        return FUEL.equals(element) || COOLANT.equals(element) || BATTERY.equals(element)
                ? 642 : 360;
    }

    private static int defaultFontSize(String element) {
        return FUEL.equals(element) ? 30 : 28;
    }

    private static boolean defaultEnabled(String element) {
        return FUEL.equals(element) || COOLANT.equals(element) || BATTERY.equals(element);
    }

    private static String storageKey(String element) {
        if (FUEL.equals(element)) return "fuel";
        if (COOLANT.equals(element)) return "coolant";
        if (BATTERY.equals(element)) return "battery";
        return element;
    }

    private static SharedPreferences prefs(Context context) {
        Context storage = context.createDeviceProtectedStorageContext();
        SharedPreferences devicePreferences =
                storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!devicePreferences.getBoolean("storage_migrated", false)) {
            UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
            boolean unlocked = users == null || users.isUserUnlocked();
            if (unlocked) {
                try {
                    storage.moveSharedPreferencesFrom(context, PREFS);
                } catch (Throwable ignored) {
                    // Keep defaults if the old credential-protected file is unavailable.
                }
                storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean("storage_migrated", true).apply();
            }
        }
        return storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SharedPreferences bootPrefs(Context context) {
        Context storage = context.createDeviceProtectedStorageContext();
        return storage.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private OverlaySettings() {
    }
}
