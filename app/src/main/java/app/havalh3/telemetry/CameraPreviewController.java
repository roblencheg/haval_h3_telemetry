package app.havalh3.telemetry;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.Collections;

final class CameraPreviewController {
    interface StatusCallback {
        void onStatus(String message);
    }

    private static final String TAG = "H3Camera";
    private final Activity activity;
    private final TextureView textureView;
    private final StatusCallback statusCallback;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private String requestedCameraId;
    private boolean requested;
    private boolean receivedFrame;
    private final Runnable frameTimeout = () -> {
        if (requested && !receivedFrame) {
            fail("Поток открыт, но видеокадры не поступают");
        }
    };

    CameraPreviewController(Activity activity, TextureView textureView,
                            StatusCallback statusCallback) {
        this.activity = activity;
        this.textureView = textureView;
        this.statusCallback = statusCallback;
        this.textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (requested) openRequestedCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                configureTransform(width, height, 1920, 1080);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                closeCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                if (!receivedFrame) {
                    receivedFrame = true;
                    textureView.removeCallbacks(frameTimeout);
                    DiagnosticsStore.record(activity,
                            "first camera frame received id=" + requestedCameraId);
                    status("");
                }
            }
        });
    }

    void start(String cameraId) {
        requested = true;
        receivedFrame = false;
        requestedCameraId = cameraId;
        status("Подключение камеры " + cameraId + "…");
        startThread();
        if (textureView.isAvailable()) openRequestedCamera();
    }

    void stop() {
        requested = false;
        textureView.removeCallbacks(frameTimeout);
        closeCamera();
        stopThread();
    }

    private void openRequestedCamera() {
        if (!requested || cameraDevice != null || requestedCameraId == null) return;
        if (activity.checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            fail("Нет разрешения CAMERA");
            return;
        }
        try {
            CameraManager manager = (CameraManager) activity.getSystemService(Activity.CAMERA_SERVICE);
            Size size = chooseSize(manager, requestedCameraId);
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(size.getWidth(), size.getHeight());
            configureTransform(textureView.getWidth(), textureView.getHeight(),
                    size.getWidth(), size.getHeight());
            manager.openCamera(requestedCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    createPreviewSession(size);
                    DiagnosticsStore.record(activity,
                            "camera opened id=" + requestedCameraId + " size=" + size);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                    fail("Камера отключилась: " + requestedCameraId);
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                    fail("Ошибка камеры " + requestedCameraId + ": " + error);
                }
            }, cameraHandler);
        } catch (Throwable error) {
            fail("Не удалось открыть камеру " + requestedCameraId + ": " + error);
        }
    }

    private Size chooseSize(CameraManager manager, String cameraId) throws Exception {
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map == null ? null : map.getOutputSizes(SurfaceTexture.class);
        if (sizes != null) {
            for (Size size : sizes) {
                if (size.getWidth() == 1920 && size.getHeight() == 1080) return size;
            }
            for (Size size : sizes) {
                if (size.getWidth() == 1280 && size.getHeight() == 640) return size;
            }
            if (sizes.length > 0) return sizes[0];
        }
        return new Size(1920, 1080);
    }

    private void createPreviewSession(Size size) {
        if (cameraDevice == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(size.getWidth(), size.getHeight());
            Surface surface = new Surface(texture);
            CaptureRequest.Builder request =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(surface);
            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null) return;
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(request.build(), null, cameraHandler);
                                status("Поток открыт; ожидание видеокадра…");
                                textureView.removeCallbacks(frameTimeout);
                                textureView.postDelayed(frameTimeout, 2_500L);
                            } catch (Throwable error) {
                                fail("Не удалось запустить поток: " + error);
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            fail("Камера не настроила видеопоток");
                        }
                    }, cameraHandler);
        } catch (Throwable error) {
            fail("Ошибка создания видеопотока: " + error);
        }
    }

    private void configureTransform(int viewWidth, int viewHeight,
                                    int bufferWidth, int bufferHeight) {
        if (viewWidth <= 0 || viewHeight <= 0 || bufferWidth <= 0 || bufferHeight <= 0) return;
        float baseX = (float) viewWidth / bufferWidth;
        float baseY = (float) viewHeight / bufferHeight;
        float uniform = Math.max(baseX, baseY);
        Matrix matrix = new Matrix();
        matrix.setScale(uniform / baseX, uniform / baseY,
                viewWidth / 2f, viewHeight / 2f);
        textureView.setTransform(matrix);
    }

    private void closeCamera() {
        if (captureSession != null) {
            try {
                captureSession.close();
            } catch (Throwable ignored) {
            }
            captureSession = null;
        }
        if (cameraDevice != null) {
            try {
                cameraDevice.close();
            } catch (Throwable ignored) {
            }
            cameraDevice = null;
        }
    }

    private void startThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("H3CameraPreview");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopThread() {
        if (cameraThread == null) return;
        cameraThread.quitSafely();
        cameraThread = null;
        cameraHandler = null;
    }

    private void fail(String message) {
        Log.e(TAG, message);
        DiagnosticsStore.record(activity, message);
        status(message);
    }

    private void status(String message) {
        activity.runOnUiThread(() -> statusCallback.onStatus(message));
    }
}
