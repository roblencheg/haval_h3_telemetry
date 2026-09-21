package app.havalh3.telemetry;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.TextureView;

import java.util.HashMap;
import java.util.Map;

public final class ClusterActivity extends Activity {
    private final Map<String, TextView> sensorViews = new HashMap<>();
    private FrameLayout root;
    private TextureView cameraView;
    private TextureView dvrCameraView;
    private TextView cameraStatus;
    private CameraPreviewController cameraPreview;
    private DvrPreviewController dvrPreview;
    private boolean receiverRegistered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelemetryService.ACTION_HIDE_CLUSTER.equals(intent.getAction())) {
                finishAndRemoveTask();
            } else if (TelemetryService.ACTION_CAMERA_CHANGED.equals(intent.getAction())) {
                applyCameraState();
            } else {
                applySettings();
                render();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureTransparentWindow();
        setContentView(createOverlay());
        cameraPreview = new CameraPreviewController(this, cameraView, message -> {
            cameraStatus.setText(message);
            cameraStatus.setVisibility(message == null || message.isEmpty()
                    ? View.GONE : View.VISIBLE);
        });
        dvrPreview = new DvrPreviewController(this, dvrCameraView, message -> {
            cameraStatus.setText(message);
            cameraStatus.setVisibility(message == null || message.isEmpty()
                    ? View.GONE : View.VISIBLE);
        });
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelemetryService.ACTION_UPDATE);
        filter.addAction(TelemetryService.ACTION_HIDE_CLUSTER);
        filter.addAction(TelemetryService.ACTION_SETTINGS_CHANGED);
        filter.addAction(TelemetryService.ACTION_CAMERA_CHANGED);
        registerReceiver(receiver, filter);
        receiverRegistered = true;
        applySettings();
        applyCameraState();
        render();
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) unregisterReceiver(receiver);
        receiverRegistered = false;
        if (cameraPreview != null) cameraPreview.stop();
        if (dvrPreview != null) dvrPreview.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (dvrPreview != null) dvrPreview.release();
        super.onDestroy();
    }

    private void configureTransparentWindow() {
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    private View createOverlay() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        cameraView = new TextureView(this);
        cameraView.setVisibility(View.GONE);
        root.addView(cameraView, new FrameLayout.LayoutParams(-1, -1));
        dvrCameraView = new TextureView(this);
        dvrCameraView.setVisibility(View.GONE);
        root.addView(dvrCameraView, new FrameLayout.LayoutParams(-1, -1));
        cameraStatus = clusterValue();
        cameraStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        cameraStatus.setGravity(Gravity.CENTER);
        cameraStatus.setTextColor(Color.WHITE);
        cameraStatus.setBackgroundColor(Color.argb(150, 0, 0, 0));
        cameraStatus.setVisibility(View.GONE);
        root.addView(cameraStatus, new FrameLayout.LayoutParams(-1, -1));
        for (String signal : TelemetrySignals.DISPLAY_ELEMENTS) {
            TextView view = clusterValue();
            sensorViews.put(signal, view);
            root.addView(view);
        }
        applySettings();
        return root;
    }

    private TextView clusterValue() {
        TextView view = new TextView(this);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.create("sans", Typeface.BOLD));
        view.setShadowLayer(4f, 1f, 1f, Color.BLACK);
        view.setIncludeFontPadding(false);
        return view;
    }

    private void applySettings() {
        boolean cameraVisible = CameraState.isVisible();
        for (String signal : TelemetrySignals.DISPLAY_ELEMENTS) {
            TextView view = sensorViews.get(signal);
            if (view == null) continue;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    OverlaySettings.width(signal),
                    OverlaySettings.ELEMENT_HEIGHT,
                    Gravity.START | Gravity.TOP
            );
            params.leftMargin = OverlaySettings.getX(this, signal);
            params.topMargin = OverlaySettings.getY(this, signal);
            view.setLayoutParams(params);
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    OverlaySettings.getFontSize(this, signal));
            view.setVisibility(!cameraVisible && OverlaySettings.isEnabled(this, signal)
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void applyCameraState() {
        if (root == null || cameraView == null || dvrCameraView == null
                || cameraPreview == null || dvrPreview == null) return;
        boolean visible = CameraState.isVisible();
        boolean windshield = CameraSettings.SOURCE_WINDSHIELD.equals(
                CameraSettings.getSource(this));
        root.setBackgroundColor(visible ? Color.BLACK : Color.TRANSPARENT);
        cameraView.setVisibility(visible && !windshield ? View.VISIBLE : View.GONE);
        dvrCameraView.setVisibility(visible && windshield ? View.VISIBLE : View.GONE);
        cameraStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) cameraStatus.setText("Подключение камеры…");
        if (visible) {
            if (windshield) {
                cameraPreview.stop();
                dvrPreview.start();
            } else {
                dvrPreview.stop();
                String cameraId = CameraSettings.cameraIdForSource(this);
                cameraPreview.start(cameraId);
            }
        } else {
            cameraPreview.stop();
            dvrPreview.stop();
        }
        applySettings();
    }

    private void render() {
        for (String signal : TelemetrySignals.DISPLAY_ELEMENTS) {
            TextView view = sensorViews.get(signal);
            if (view == null) continue;
            String value = TelemetryStore.displayText(signal);
            view.setText(value == null || value.isEmpty() ? "—" : value);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        DiagnosticsStore.recordKeyEvent(this, "ClusterActivity", event);
        if (CameraState.isVisible() && CameraSettings.isFeatureEnabled(this)
                && event.getAction() == KeyEvent.ACTION_UP) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
                selectCameraSource(CameraSettings.SOURCE_WINDSHIELD, "joystick up");
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                selectCameraSource(CameraSettings.SOURCE_REAR, "joystick down");
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void selectCameraSource(String source, String origin) {
        CameraSettings.setSource(this, source);
        DiagnosticsStore.record(this, origin + "; source=" + source);
        applyCameraState();
    }
}
