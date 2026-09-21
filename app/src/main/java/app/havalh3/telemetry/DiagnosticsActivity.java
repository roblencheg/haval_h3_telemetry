package app.havalh3.telemetry;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class DiagnosticsActivity extends Activity {
    private TextView reportView;
    private boolean receiverRegistered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderReport();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContent());
        renderReport();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(receiver, new IntentFilter(DiagnosticsStore.ACTION_CHANGED));
        receiverRegistered = true;
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) unregisterReceiver(receiver);
        receiverRegistered = false;
        super.onStop();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        DiagnosticsStore.recordKeyEvent(this, "DiagnosticsActivity", event);
        return super.dispatchKeyEvent(event);
    }

    private View createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.setBackgroundColor(Color.rgb(16, 18, 22));

        TextView title = new TextView(this);
        title.setText("Диагностика кнопки и камеры");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Оставьте этот экран открытым и нажмите дополнительную кнопку левого джойстика коротко и долго. Приложение только записывает события и не перехватывает штатное действие.");
        hint.setTextColor(Color.LTGRAY);
        hint.setTextSize(15);
        root.addView(hint);

        LinearLayout actions = new LinearLayout(this);
        addButton(actions, "Назад", this::finish);
        addButton(actions, "Обновить", this::renderReport);
        addButton(actions, "Копировать", this::copyReport);
        addButton(actions, "Поделиться", this::shareReport);
        addButton(actions, "Очистить события", () -> DiagnosticsStore.clear(this));
        root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        reportView = new TextView(this);
        reportView.setTextColor(Color.rgb(220, 224, 230));
        reportView.setTextSize(13);
        reportView.setTypeface(Typeface.MONOSPACE);
        reportView.setTextIsSelectable(true);
        scroll.addView(reportView);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private void renderReport() {
        if (reportView != null) reportView.setText(buildReport());
    }

    private String buildReport() {
        StringBuilder out = new StringBuilder();
        out.append("H3 TELEMETRY DIAGNOSTICS\n");
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            out.append("app=").append(getPackageName())
                    .append(" version=").append(info.versionName)
                    .append(" code=").append(info.getLongVersionCode()).append('\n');
        } catch (Throwable error) {
            out.append("app=").append(getPackageName()).append('\n');
        }
        out.append("device=").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(" product=").append(Build.PRODUCT).append('\n');
        out.append("android=").append(Build.VERSION.RELEASE)
                .append(" sdk=").append(Build.VERSION.SDK_INT).append("\n\n");

        appendDisplays(out);
        appendInputDevices(out);
        appendCameraApi(out);
        appendVideoNodes(out);
        appendServices(out);

        out.append("\nRECENT KEY EVENTS\n")
                .append(DiagnosticsStore.recentEvents(this)).append('\n');
        return out.toString();
    }

    private void appendDisplays(StringBuilder out) {
        out.append("DISPLAYS\n");
        try {
            DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            for (Display display : manager.getDisplays()) {
                android.graphics.Point size = new android.graphics.Point();
                display.getRealSize(size);
                out.append("id=").append(display.getDisplayId())
                        .append(" name=").append(display.getName())
                        .append(" size=").append(size.x).append('x').append(size.y)
                        .append(" state=").append(display.getState())
                        .append(" flags=0x").append(Integer.toHexString(display.getFlags()))
                        .append('\n');
            }
        } catch (Throwable error) {
            out.append("ERROR ").append(error).append('\n');
        }
        out.append('\n');
    }

    private void appendInputDevices(StringBuilder out) {
        out.append("INPUT DEVICES\n");
        try {
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice device = InputDevice.getDevice(id);
                if (device == null) continue;
                out.append("id=").append(id)
                        .append(" name=").append(device.getName())
                        .append(" sources=0x").append(Integer.toHexString(device.getSources()))
                        .append(" keyboardType=").append(device.getKeyboardType()).append('\n');
            }
        } catch (Throwable error) {
            out.append("ERROR ").append(error).append('\n');
        }
        out.append('\n');
    }

    private void appendCameraApi(StringBuilder out) {
        out.append("ANDROID CAMERA API\n");
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String[] ids = manager.getCameraIdList();
            out.append("ids=").append(Arrays.toString(ids)).append('\n');
            for (String id : ids) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                out.append("id=").append(id)
                        .append(" facing=").append(c.get(CameraCharacteristics.LENS_FACING))
                        .append(" level=").append(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
                StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null) {
                    Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                    out.append(" yuv=").append(compactSizes(sizes));
                }
                out.append('\n');
            }
        } catch (Throwable error) {
            out.append("ERROR ").append(error.getClass().getSimpleName())
                    .append(": ").append(error.getMessage()).append('\n');
        }
        out.append('\n');
    }

    private void appendVideoNodes(StringBuilder out) {
        out.append("VIDEO NODES\n");
        for (String path : new String[]{"/dev/video51", "/dev/video52", "/dev/video53"}) {
            File file = new File(path);
            out.append(path)
                    .append(" exists=").append(file.exists())
                    .append(" read=").append(file.canRead())
                    .append(" write=").append(file.canWrite()).append('\n');
        }
        out.append('\n');
    }

    private void appendServices(StringBuilder out) {
        out.append("BINDER SERVICES\n");
        String[] names = {"gwm_adapter", "media.camera", "media.camera.proxy",
                "parking", "gwm_parking", "multidisplay"};
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method checkService = serviceManager.getDeclaredMethod("checkService", String.class);
            for (String name : names) {
                try {
                    Object binder = checkService.invoke(null, name);
                    out.append(name).append('=').append(binder != null ? "FOUND" : "missing").append('\n');
                } catch (Throwable error) {
                    out.append(name).append("=ERROR ")
                            .append(error.getClass().getSimpleName()).append('\n');
                }
            }
        } catch (Throwable error) {
            out.append("ServiceManager unavailable: ")
                    .append(error.getClass().getSimpleName()).append('\n');
        }
    }

    private String compactSizes(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return "[]";
        StringBuilder value = new StringBuilder("[");
        int count = Math.min(sizes.length, 8);
        for (int i = 0; i < count; i++) {
            if (i > 0) value.append(',');
            value.append(sizes[i].getWidth()).append('x').append(sizes[i].getHeight());
        }
        if (sizes.length > count) value.append(",…");
        return value.append(']').toString();
    }

    private void copyReport() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("H3 diagnostics", buildReport()));
    }

    private void shareReport() {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "H3 Telemetry diagnostics")
                .putExtra(Intent.EXTRA_TEXT, buildReport());
        startActivity(Intent.createChooser(send, "Отправить отчёт"));
    }

    private void addButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1f);
        params.setMargins(dp(2), dp(6), dp(2), dp(6));
        parent.addView(button, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
