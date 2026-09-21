package app.havalh3.telemetry;

import java.util.Collections;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class TelemetryStore {
    static final float H3_TANK_CAPACITY_LITERS = 55f;

    private static final ConcurrentHashMap<String, String> RAW = new ConcurrentHashMap<>();
    private static volatile boolean connected;
    private static volatile String status = "Ожидание подключения";

    static void put(String key, String value) {
        if (key != null && value != null) {
            RAW.put(key, value);
        }
    }

    static Map<String, String> snapshot() {
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        for (String key : TelemetrySignals.ALL) {
            if (RAW.containsKey(key)) {
                ordered.put(key, RAW.get(key));
            }
        }
        for (Map.Entry<String, String> entry : RAW.entrySet()) {
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(ordered);
    }

    static Float number(String key) {
        String value = RAW.get(key);
        if (value == null) return null;
        try {
            return Float.parseFloat(value.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Float fuelLiters() {
        Float percent = number(TelemetrySignals.FUEL_PERCENT);
        if (percent == null || percent.isNaN() || percent.isInfinite()) return null;
        float safePercent = Math.max(0f, Math.min(100f, percent));
        return safePercent * H3_TANK_CAPACITY_LITERS / 100f;
    }

    static String fuelText() {
        Float value = fuelLiters();
        return value == null ? "" : String.format(Locale.US, "%.0f L", value);
    }

    static String coolantText() {
        Float value = number(TelemetrySignals.COOLANT_TEMP);
        return value == null ? "" : String.format(Locale.US, "%.0f °C", value);
    }

    static String batteryText() {
        Float value = number(TelemetrySignals.BATTERY_VOLTAGE);
        return value == null ? "" : String.format(Locale.US, "%.1f V", value);
    }

    static String displayText(String key) {
        if (TelemetrySignals.CURRENT_DATE.equals(key)) {
            return new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());
        }
        if (TelemetrySignals.FUEL_PERCENT.equals(key)) return fuelText();
        if (TelemetrySignals.COOLANT_TEMP.equals(key)) return coolantText();
        if (TelemetrySignals.BATTERY_VOLTAGE.equals(key)) return batteryText();

        String value = RAW.get(key);
        if (value == null || value.trim().isEmpty()) return "";
        if (TelemetrySignals.INSIDE_TEMP.equals(key)) return value + " °C";
        if (TelemetrySignals.EV_BATTERY_PERCENT.equals(key)
                || TelemetrySignals.BATTERY_POWER_LEVEL.equals(key)) return value + " %";
        if (TelemetrySignals.AVG_FUEL.equals(key)
                || TelemetrySignals.JOURNEY_AVG_FUEL.equals(key)) return value + " л/100 км";
        if (TelemetrySignals.ODOMETER.equals(key)) return value + " км";
        return value;
    }

    static boolean isConnected() {
        return connected;
    }

    static void setConnected(boolean value, String message) {
        connected = value;
        status = message;
    }

    static String status() {
        return status;
    }

    private TelemetryStore() {
    }
}
