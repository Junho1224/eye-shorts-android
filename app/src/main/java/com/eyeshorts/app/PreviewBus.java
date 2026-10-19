package com.eyeshorts.app;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** At most one queued preview and one displayed preview. No frame is written to storage. */
final class PreviewBus {
    record Frame(Bitmap bitmap, float leftX, float leftY, float rightX, float rightY, boolean face, boolean eyesOpen) {
        void recycle() { if (!bitmap.isRecycled()) bitmap.recycle(); }
    }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicReference<Frame> pending = new AtomicReference<>();
    private static final AtomicBoolean posted = new AtomicBoolean();
    private static WeakReference<CameraPreviewView> consumer = new WeakReference<>(null);
    static volatile boolean enabled;
    private PreviewBus() {}
    static void attach(CameraPreviewView view) {
        if (consumer.get()!=view) { CameraPreviewView old=consumer.get(); if (old!=null) old.clearFrame(); consumer=new WeakReference<>(view); }
        enabled=true;
    }
    static void detach(CameraPreviewView view) {
        if (consumer.get()!=view) return;
        enabled=false; consumer.clear(); view.clearFrame();
        Frame old=pending.getAndSet(null); if (old!=null) old.recycle();
    }
    static void clear() {
        enabled=false;
        Frame old=pending.getAndSet(null); if (old!=null) old.recycle();
        CameraPreviewView view=consumer.get(); if (view!=null) view.clearFrame();
    }
    static void publish(Frame frame) {
        if (!enabled) { frame.recycle(); return; }
        Frame old=pending.getAndSet(frame); if (old!=null) old.recycle();
        if (posted.compareAndSet(false,true)) MAIN.post(PreviewBus::drain);
    }
    private static void drain() {
        posted.set(false);
        Frame frame=pending.getAndSet(null);
        if (frame==null) return;
        CameraPreviewView view=consumer.get();
        if (!enabled || view==null) frame.recycle(); else view.setFrame(frame);
    }
}
