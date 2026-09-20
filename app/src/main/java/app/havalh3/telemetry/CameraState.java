package app.havalh3.telemetry;

final class CameraState {
    private static volatile boolean visible;

    static boolean isVisible() {
        return visible;
    }

    static boolean toggle() {
        visible = !visible;
        return visible;
    }

    static void setVisible(boolean value) {
        visible = value;
    }

    private CameraState() {
    }
}
