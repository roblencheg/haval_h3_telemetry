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

import java.util.HashMap;
import java.util.Map;

public final class ClusterActivity extends Activity {
    private final Map<String, TextView> sensorViews = new HashMap<>();
    private boolean receiverRegistered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelemetryService.ACTION_HIDE_CLUSTER.equals(intent.getAction())) {
                finishAndRemoveTask();
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
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelemetryService.ACTION_UPDATE);
        filter.addAction(TelemetryService.ACTION_HIDE_CLUSTER);
        filter.addAction(TelemetryService.ACTION_SETTINGS_CHANGED);
        registerReceiver(receiver, filter);
        receiverRegistered = true;
        applySettings();
        render();
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) unregisterReceiver(receiver);
        receiverRegistered = false;
        super.onStop();
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
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        for (String signal : TelemetrySignals.ALL) {
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
        for (String signal : TelemetrySignals.ALL) {
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
            view.setVisibility(OverlaySettings.isEnabled(this, signal)
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void render() {
        for (String signal : TelemetrySignals.ALL) {
            TextView view = sensorViews.get(signal);
            if (view == null) continue;
            String value = TelemetryStore.displayText(signal);
            view.setText(value == null || value.isEmpty() ? "—" : value);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        DiagnosticsStore.recordKeyEvent(this, "ClusterActivity", event);
        return super.dispatchKeyEvent(event);
    }
}
