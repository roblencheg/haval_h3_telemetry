package app.havalh3.telemetry;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;

final class DvrPreviewController implements TextureView.SurfaceTextureListener {
    interface StatusCallback {
        void onStatus(String message);
    }

    private final Context context;
    private final TextureView textureView;
    private final StatusCallback statusCallback;
    private final CarDvrClient dvrClient;
    private MediaPlayer player;
    private String pendingAddress;
    private boolean requested;

    DvrPreviewController(Context context, TextureView textureView,
                         StatusCallback statusCallback) {
        this.context = context.getApplicationContext();
        this.textureView = textureView;
        this.statusCallback = statusCallback;
        this.textureView.setSurfaceTextureListener(this);
        dvrClient = new CarDvrClient(context, new CarDvrClient.Callback() {
            @Override
            public void onStatus(String message) {
                textureView.post(() -> status(message));
            }

            @Override
            public void onRtspAddress(String address) {
                textureView.post(() -> play(address));
            }
        });
    }

    void start() {
        if (requested) return;
        requested = true;
        status("Подключение верхней камеры…");
        dvrClient.startFrontPreview();
    }

    void stop() {
        requested = false;
        pendingAddress = null;
        releasePlayer();
        dvrClient.stop();
    }

    void release() {
        requested = false;
        pendingAddress = null;
        releasePlayer();
        dvrClient.release();
    }

    private void play(String address) {
        if (!requested || address == null || address.isEmpty()) return;
        pendingAddress = address;
        if (!textureView.isAvailable()) return;
        releasePlayer();
        try {
            Surface surface = new Surface(textureView.getSurfaceTexture());
            player = new MediaPlayer();
            player.setSurface(surface);
            surface.release();
            player.setDataSource(address);
            player.setOnPreparedListener(mediaPlayer -> {
                mediaPlayer.start();
                status("");
                DiagnosticsStore.record(context, "car_dvr RTSP playback started=" + address);
            });
            player.setOnErrorListener((mediaPlayer, what, extra) -> {
                status("Ошибка потока верхней камеры: " + what + "/" + extra);
                DiagnosticsStore.record(context,
                        "car_dvr RTSP error what=" + what + " extra=" + extra);
                return true;
            });
            player.prepareAsync();
            DiagnosticsStore.record(context, "car_dvr RTSP prepare=" + address);
        } catch (Throwable error) {
            DiagnosticsStore.record(context, "car_dvr RTSP prepare failed=" + error);
            status("Не удалось открыть поток верхней камеры");
        }
    }

    private void releasePlayer() {
        if (player == null) return;
        try {
            player.stop();
        } catch (Throwable ignored) {
        }
        player.release();
        player = null;
    }

    private void status(String message) {
        statusCallback.onStatus(message);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (pendingAddress != null) play(pendingAddress);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        releasePlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }
}
