package app.havalh3.telemetry;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int REQUEST_CAMERA = 203;
    private LinearLayout sensorList;
    private TextView status;
    private boolean receiverRegistered;
    private boolean enableCameraAfterPermission;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderSensors();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContent());
        startTelemetry(TelemetryService.ACTION_START);
        renderSensors();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(updateReceiver, new IntentFilter(TelemetryService.ACTION_UPDATE));
        receiverRegistered = true;
        renderSensors();
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) unregisterReceiver(updateReceiver);
        receiverRegistered = false;
        super.onStop();
    }

    private View createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(14), dp(24), dp(14));
        root.setBackgroundColor(Color.rgb(16, 18, 22));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("H3 Телеметрия", 27, Color.WHITE, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch autoStart = new Switch(this);
        autoStart.setText("Автозагрузка");
        autoStart.setTextColor(Color.WHITE);
        autoStart.setTextSize(16);
        autoStart.setChecked(OverlaySettings.isAutoStartEnabled(this));
        autoStart.setOnCheckedChangeListener((button, checked) ->
                OverlaySettings.setAutoStartEnabled(this, checked));
        header.addView(autoStart);

        addHeaderButton(header, "Показать", TelemetryService.ACTION_SHOW_CLUSTER);
        addHeaderButton(header, "Скрыть", TelemetryService.ACTION_HIDE_CLUSTER);
        Button diagnostics = new Button(this);
        diagnostics.setText("Диагностика");
        diagnostics.setTextSize(14);
        diagnostics.setOnClickListener(v ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));
        LinearLayout.LayoutParams diagnosticsParams = new LinearLayout.LayoutParams(-2, dp(52));
        diagnosticsParams.leftMargin = dp(5);
        header.addView(diagnostics, diagnosticsParams);
        root.addView(header);

        status = text("", 14, Color.rgb(174, 181, 191), Typeface.NORMAL);
        root.addView(status);

        TextView hint = text(
                "Нажмите на датчик, чтобы настроить показ, положение и шрифт",
                15,
                Color.rgb(214, 218, 224),
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(8);
        hintParams.bottomMargin = dp(8);
        root.addView(hint, hintParams);

        ScrollView scroll = new ScrollView(this);
        sensorList = new LinearLayout(this);
        sensorList.setOrientation(LinearLayout.VERTICAL);
        sensorList.setPadding(0, 0, 0, dp(12));
        scroll.addView(sensorList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private void renderSensors() {
        if (status == null || sensorList == null) return;
        status.setText((TelemetryStore.isConnected() ? "● " : "○ ") + TelemetryStore.status()
                + "  •  приборка 1920×720  •  displayId=2");
        status.setTextColor(TelemetryStore.isConnected()
                ? Color.rgb(89, 214, 130)
                : Color.rgb(255, 170, 70));

        sensorList.removeAllViews();
        addCameraRow();
        for (String signal : TelemetrySignals.ALL) addSensorRow(signal);
    }

    private void addCameraRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(7), dp(16), dp(7));
        row.setBackgroundColor(Color.rgb(38, 31, 24));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> showCameraDialog());

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("Камеры на приборной панели", 17, Color.WHITE, Typeface.BOLD));
        labels.addView(text((CameraSettings.isFeatureEnabled(this) ? "Включено" : "Выключено")
                        + "  •  " + CameraSettings.sourceLabel(this),
                13, Color.rgb(200, 173, 135), Typeface.NORMAL));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView state = text(CameraState.isVisible() ? "показывается" : "настроить",
                16, Color.rgb(255, 157, 27), Typeface.BOLD);
        state.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(state, new LinearLayout.LayoutParams(dp(260), -1));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.bottomMargin = dp(7);
        sensorList.addView(row, params);
    }

    private void showCameraDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(6), dp(22), dp(12));

        Switch enabled = new Switch(this);
        enabled.setText("Показывать камеры на приборной панели");
        enabled.setTextColor(Color.WHITE);
        enabled.setTextSize(17);
        enabled.setChecked(CameraSettings.isFeatureEnabled(this));
        panel.addView(enabled, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView source = text("Источник: " + CameraSettings.sourceLabel(this),
                16, Color.LTGRAY, Typeface.BOLD);
        panel.addView(source);

        TextView hint = text(
                "При включении открывается передняя нижняя камера. "
                        + "Джойстик вверх выбирает верхнюю камеру, вниз — заднюю.",
                14, Color.rgb(180, 186, 196), Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(6);
        hintParams.bottomMargin = dp(8);
        panel.addView(hint, hintParams);

        Button front = new Button(this);
        front.setText("Передняя нижняя камера");
        front.setOnClickListener(v -> selectCameraSource(
                CameraSettings.SOURCE_FRONT_BUMPER, source));
        panel.addView(front, new LinearLayout.LayoutParams(-1, dp(52)));

        Button windshield = new Button(this);
        windshield.setText("Верхняя камера на стекле (поиск канала)");
        windshield.setOnClickListener(v -> selectCameraSource(
                CameraSettings.SOURCE_WINDSHIELD, source));
        panel.addView(windshield, new LinearLayout.LayoutParams(-1, dp(52)));

        Button rear = new Button(this);
        rear.setText("Задняя камера");
        rear.setOnClickListener(v -> selectCameraSource(CameraSettings.SOURCE_REAR, source));
        panel.addView(rear, new LinearLayout.LayoutParams(-1, dp(52)));

        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (checked && checkSelfPermission(Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                enableCameraAfterPermission = true;
                button.setChecked(false);
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
                return;
            }
            setCameraFeatureEnabled(checked);
        });

        new AlertDialog.Builder(this)
                .setTitle("Камеры")
                .setView(panel)
                .setPositiveButton("Показать сейчас", (dialog, which) -> startCameraTest())
                .setNeutralButton("Скрыть", (dialog, which) -> stopCameraTest())
                .setNegativeButton("Готово", null)
                .show();
    }

    private void selectCameraSource(String selectedSource, TextView label) {
        CameraSettings.setSource(this, selectedSource);
        label.setText("Источник: " + CameraSettings.sourceLabel(this));
        if (CameraState.isVisible()) {
            sendBroadcast(new Intent(TelemetryService.ACTION_CAMERA_CHANGED)
                    .setPackage(getPackageName()));
        }
        DiagnosticsStore.record(this, "manual camera source=" + selectedSource);
        renderSensors();
    }

    private void setCameraFeatureEnabled(boolean enabled) {
        CameraSettings.setFeatureEnabled(this, enabled);
        if (!enabled) CameraState.setVisible(false);
        startTelemetry(TelemetryService.ACTION_CAMERA_SETTINGS_CHANGED);
        sendBroadcast(new Intent(TelemetryService.ACTION_CAMERA_CHANGED)
                .setPackage(getPackageName()));
        renderSensors();
    }

    private void startCameraTest() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            enableCameraAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        if (!CameraSettings.isFeatureEnabled(this)) {
            setCameraFeatureEnabled(true);
        }
        CameraState.setVisible(true);
        startTelemetry(TelemetryService.ACTION_SHOW_CLUSTER);
        sendBroadcast(new Intent(TelemetryService.ACTION_CAMERA_CHANGED)
                .setPackage(getPackageName()));
        renderSensors();
    }

    private void stopCameraTest() {
        CameraState.setVisible(false);
        sendBroadcast(new Intent(TelemetryService.ACTION_CAMERA_CHANGED)
                .setPackage(getPackageName()));
        renderSensors();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                          int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (enableCameraAfterPermission) setCameraFeatureEnabled(true);
            enableCameraAfterPermission = false;
            startCameraTest();
        } else if (requestCode == REQUEST_CAMERA) {
            enableCameraAfterPermission = false;
        }
    }

    private void addSensorRow(String signal) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(7), dp(16), dp(7));
        row.setBackgroundColor(Color.rgb(27, 30, 36));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> showSensorDialog(signal));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(TelemetrySignals.label(signal), 17, Color.WHITE, Typeface.BOLD);
        TextView settings = text(sensorSummary(signal), 13,
                Color.rgb(161, 170, 182), Typeface.NORMAL);
        labels.addView(name);
        labels.addView(settings);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        String value = TelemetryStore.displayText(signal);
        TextView number = text(value == null || value.isEmpty() ? "нет данных" : value,
                19, Color.rgb(255, 157, 27), Typeface.BOLD);
        number.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(number, new LinearLayout.LayoutParams(dp(260), -1));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.bottomMargin = dp(3);
        sensorList.addView(row, params);
    }

    private String sensorSummary(String signal) {
        if (!OverlaySettings.isEnabled(this, signal)) return "Не показывается на приборке";
        return "На приборке  •  X " + OverlaySettings.getX(this, signal)
                + "  Y " + OverlaySettings.getY(this, signal)
                + "  •  шрифт " + OverlaySettings.getFontSize(this, signal) + " px";
    }

    private void showSensorDialog(String signal) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(6), dp(22), dp(12));
        scroll.addView(panel);

        String currentValue = TelemetryStore.displayText(signal);
        TextView value = text("Сейчас: " + (currentValue.isEmpty() ? "нет данных" : currentValue),
                18, Color.rgb(255, 157, 27), Typeface.BOLD);
        panel.addView(value);

        Switch enabled = new Switch(this);
        enabled.setText("Показывать на приборной панели");
        enabled.setTextColor(Color.WHITE);
        enabled.setTextSize(17);
        enabled.setChecked(OverlaySettings.isEnabled(this, signal));
        LinearLayout.LayoutParams enabledParams = new LinearLayout.LayoutParams(-1, dp(54));
        enabledParams.topMargin = dp(5);
        panel.addView(enabled, enabledParams);

        TextView coordinates = text("", 17, Color.LTGRAY, Typeface.BOLD);
        panel.addView(coordinates);

        int[] step = {10};
        TextView stepLabel = text("Шаг перемещения: 10 px", 15, Color.LTGRAY, Typeface.NORMAL);
        panel.addView(stepLabel);

        LinearLayout steps = new LinearLayout(this);
        for (int candidate : new int[]{1, 5, 10, 20}) {
            addDialogButton(steps, String.valueOf(candidate), () -> {
                step[0] = candidate;
                stepLabel.setText("Шаг перемещения: " + candidate + " px");
            });
        }
        panel.addView(steps);

        LinearLayout arrows = new LinearLayout(this);
        panel.addView(arrows);

        TextView fontSize = text("", 17, Color.LTGRAY, Typeface.BOLD);
        panel.addView(fontSize);
        LinearLayout fontButtons = new LinearLayout(this);
        panel.addView(fontButtons);

        Runnable refresh = () -> {
            coordinates.setText("Положение: X " + OverlaySettings.getX(this, signal)
                    + "   Y " + OverlaySettings.getY(this, signal));
            fontSize.setText("Размер шрифта: "
                    + OverlaySettings.getFontSize(this, signal) + " px");
        };

        addDialogButton(arrows, "←", () -> moveSensor(signal, -step[0], 0, refresh));
        addDialogButton(arrows, "→", () -> moveSensor(signal, step[0], 0, refresh));
        addDialogButton(arrows, "↑", () -> moveSensor(signal, 0, -step[0], refresh));
        addDialogButton(arrows, "↓", () -> moveSensor(signal, 0, step[0], refresh));

        addDialogButton(fontButtons, "Шрифт −", () -> {
            OverlaySettings.setFontSize(this, signal,
                    OverlaySettings.getFontSize(this, signal) - 1);
            refresh.run();
            settingsChanged();
        });
        addDialogButton(fontButtons, "Шрифт +", () -> {
            OverlaySettings.setFontSize(this, signal,
                    OverlaySettings.getFontSize(this, signal) + 1);
            refresh.run();
            settingsChanged();
        });

        Button reset = new Button(this);
        reset.setText("Сбросить настройки датчика");
        reset.setOnClickListener(v -> {
            OverlaySettings.resetElement(this, signal);
            enabled.setChecked(OverlaySettings.isEnabled(this, signal));
            refresh.run();
            settingsChanged();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, dp(52));
        resetParams.topMargin = dp(8);
        panel.addView(reset, resetParams);

        enabled.setOnCheckedChangeListener((button, checked) -> {
            OverlaySettings.setEnabled(this, signal, checked);
            settingsChanged();
        });
        refresh.run();

        new AlertDialog.Builder(this)
                .setTitle(TelemetrySignals.label(signal))
                .setView(scroll)
                .setNegativeButton("Готово", null)
                .show();
    }

    private void moveSensor(String signal, int dx, int dy, Runnable refresh) {
        OverlaySettings.setPosition(
                this,
                signal,
                OverlaySettings.getX(this, signal) + dx,
                OverlaySettings.getY(this, signal) + dy
        );
        refresh.run();
        settingsChanged();
    }

    private void settingsChanged() {
        sendBroadcast(new Intent(TelemetryService.ACTION_SETTINGS_CHANGED)
                .setPackage(getPackageName()));
        renderSensors();
    }

    private void addDialogButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1f);
        params.leftMargin = dp(3);
        parent.addView(button, params);
    }

    private void addHeaderButton(LinearLayout parent, String label, String action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setOnClickListener(v -> startTelemetry(action));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(52));
        params.leftMargin = dp(5);
        parent.addView(button, params);
    }

    private TextView text(String value, float sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private void startTelemetry(String action) {
        startForegroundService(new Intent(this, TelemetryService.class).setAction(action));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        DiagnosticsStore.recordKeyEvent(this, "MainActivity", event);
        return super.dispatchKeyEvent(event);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
