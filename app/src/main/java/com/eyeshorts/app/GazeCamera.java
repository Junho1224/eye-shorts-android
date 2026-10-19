package com.eyeshorts.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.eyeshorts.app.core.GazeSample;
import com.eyeshorts.app.core.EyeGeometry;
import com.eyeshorts.app.core.MouthGeometry;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Camera images exist only in RAM. Inference is serialized on a dedicated worker. */
final class GazeCamera implements AutoCloseable {
    interface Listener {
        void onReady();
        void onSample(GazeSample sample);
        void onError(String message);
    }
    private final Context context;
    private final Listener listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();
    private FaceLandmarker detector;
    private ProcessCameraProvider provider;
    private ImageAnalysis analysis;
    private long lastFrame;
    private int failures;

    GazeCamera(Context context, Listener listener) { this.context = context; this.listener = listener; }

    void start(LifecycleOwner owner) {
        worker.execute(() -> {
            try {
                detector = FaceLandmarker.createFromOptions(context,
                        FaceLandmarker.FaceLandmarkerOptions.builder()
                                .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                                .setRunningMode(RunningMode.VIDEO).setNumFaces(2)
                                .setMinFaceDetectionConfidence(0.65f).setMinFacePresenceConfidence(0.65f)
                                .setMinTrackingConfidence(0.65f).build());
                if (!closed.get()) ContextCompat.getMainExecutor(context).execute(() -> bind(owner));
            } catch (RuntimeException e) { listener.onError("시선 인식 모델을 열지 못했어요. 앱을 다시 시작해주세요."); }
        });
    }

    @SuppressWarnings("deprecation")
    private void bind(LifecycleOwner owner) {
        if (closed.get()) return;
        var future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            if (closed.get()) return;
            try {
                provider = future.get();
                analysis = new ImageAnalysis.Builder().setTargetResolution(new Size(640, 480))
                        .setTargetRotation(Surface.ROTATION_0)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build();
                analysis.setAnalyzer(worker, this::analyze);
                var boundCamera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis);
                boundCamera.getCameraInfo().getCameraState().observe(owner, state -> {
                    if (!closed.get() && state.getError() != null)
                        listener.onError("카메라 연결이 끊겼어요. 다른 카메라 앱을 닫고 다시 시작해주세요.");
                });
                listener.onReady();
            } catch (Exception e) { listener.onError("전면 카메라를 열지 못했어요. 다른 카메라 앱을 닫고 다시 시작해주세요."); }
        }, ContextCompat.getMainExecutor(context));
    }

    private void analyze(ImageProxy frame) {
        long now = SystemClock.elapsedRealtime();
        if (closed.get() || now - lastFrame < 80) { frame.close(); return; }
        lastFrame = now;
        Bitmap original = null, rotated = null;
        MPImage input = null;
        try {
            original = frame.toBitmap();
            int rotation = frame.getImageInfo().getRotationDegrees();
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            rotated = rotation == 0 ? original : Bitmap.createBitmap(original, 0, 0,
                    original.getWidth(), original.getHeight(), matrix, true);
            input = new BitmapImageBuilder(rotated).build();
            FaceLandmarkerResult result = detector.detectForVideo(input, now);
            GazeSample sample=measure(result, now, rotated.getWidth(), rotated.getHeight());
            listener.onSample(sample);
            if (PreviewBus.enabled && !closed.get()) preview(result,rotated,sample);
            failures = 0;
        } catch (RuntimeException e) {
            listener.onSample(new GazeSample(now, 0, 0, 0, 0, 0, false, 0));
            if (++failures >= 5) listener.onError("카메라 분석이 중단됐어요. 추적을 종료한 뒤 다시 시작해주세요.");
        } finally {
            if (input != null) input.close();
            if (rotated != null && rotated != original) rotated.recycle();
            if (original != null) original.recycle();
            frame.close();
        }
    }

    private static GazeSample measure(FaceLandmarkerResult result, long now, int width, int height) {
        var faces = result.faceLandmarks();
        if (faces.size() != 1 || faces.get(0).size() < 478)
            return new GazeSample(now, 0, 0, 0, 0, 0, false, faces.size());
        List<NormalizedLandmark> face = faces.get(0);
        // MediaPipe's 33/133 eye is the person's RIGHT eye; 362/263 is the LEFT eye.
        // Inference uses the unmirrored image. Only the UI preview is mirrored.
        var userRight = eye(face, 33, 133, 159, 145, 468, width, height);
        var userLeft = eye(face, 362, 263, 386, 374, 473, width, height);
        double ex = (userRight.centerX() + userLeft.centerX()) / 2, ey = (userRight.centerY() + userLeft.centerY()) / 2;
        double dx = userLeft.centerX() - userRight.centerX(), dy = userLeft.centerY() - userRight.centerY();
        if (dx < 0) { dx = -dx; dy = -dy; }
        double separation = Math.hypot(dx, dy);
        double ux = dx / separation, uy = dy / separation;
        double nx = face.get(1).x() * width - ex, ny = face.get(1).y() * height - ey;
        return new GazeSample(now, (userRight.vertical() + userLeft.vertical()) / 2,
                (nx * ux + ny * uy) / separation,
                (-nx * uy + ny * ux) / separation,
                separation / width, Math.atan2(dy, dx),
                userRight.openEnough() && userLeft.openEnough(), 1,
                (userRight.horizontal()+userLeft.horizontal())/2, userLeft.openness(), userRight.openness(),
                Math.min(userRight.width(),userLeft.width()), mouth(face,width,height));
    }

    private static double mouth(List<NormalizedLandmark> face,int width,int height) {
        var a=face.get(61); var b=face.get(291); var upper=face.get(13); var lower=face.get(14);
        return MouthGeometry.openness(a.x()*width,a.y()*height,b.x()*width,b.y()*height,
                upper.x()*width,upper.y()*height,lower.x()*width,lower.y()*height);
    }

    private static void preview(FaceLandmarkerResult result, Bitmap source, GazeSample sample) {
        int width=Math.min(360,source.getWidth());
        int height=Math.max(1,Math.round((float)source.getHeight()*width/source.getWidth()));
        Bitmap thumbnail=Bitmap.createScaledBitmap(source,width,height,true);
        if (thumbnail==source) thumbnail=source.copy(Bitmap.Config.ARGB_8888,false);
        boolean face=result.faceLandmarks().size()==1 && result.faceLandmarks().get(0).size()>=478;
        var points=face ? result.faceLandmarks().get(0) : null;
        PreviewBus.publish(new PreviewBus.Frame(thumbnail,face ? points.get(468).x() : 0,
                face ? points.get(468).y() : 0,face ? points.get(473).x() : 0,
                face ? points.get(473).y() : 0,face,sample.eyesOpen()));
    }

    private static EyeGeometry.Eye eye(List<NormalizedLandmark> points, int a, int b, int top, int bottom,
                                int iris, int width, int height) {
        double ax = points.get(a).x() * width, ay = points.get(a).y() * height;
        double bx = points.get(b).x() * width, by = points.get(b).y() * height;
        double tx = points.get(top).x() * width, ty = points.get(top).y() * height;
        double lx = points.get(bottom).x() * width, ly = points.get(bottom).y() * height;
        double ix = points.get(iris).x() * width, iy = points.get(iris).y() * height;
        return EyeGeometry.measure(ax, ay, bx, by, tx, ty, lx, ly, ix, iy);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (analysis != null) analysis.clearAnalyzer();
        if (provider != null && analysis != null) provider.unbind(analysis);
        worker.execute(() -> { if (detector != null) { detector.close(); detector = null; } });
        worker.shutdown();
    }
}
