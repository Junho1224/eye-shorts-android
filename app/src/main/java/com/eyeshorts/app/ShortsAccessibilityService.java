package com.eyeshorts.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.eyeshorts.app.core.TargetPolicy;
import com.eyeshorts.app.core.VideoCommand;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.function.Consumer;

/** Reads only package names, IDs, visibility and bounds; never reads text or screenshots. */
public final class ShortsAccessibilityService extends AccessibilityService {
    enum Target {
        DISCONNECTED, LOCKED, OTHER_APP, YOUTUBE_OTHER, INSTAGRAM_OTHER, SHORTS, REELS;
        boolean playback() { return this == SHORTS || this == REELS; }
    }
    private static WeakReference<ShortsAccessibilityService> connected = new WeakReference<>(null);
    private long cachedAt, lastGesture = -100_000;
    private Target cached = Target.OTHER_APP;
    private boolean inFlight;
    private Rect playerBounds = new Rect();

    static boolean available() { return connected.get() != null; }
    static Target target() {
        ShortsAccessibilityService service = connected.get();
        if (service == null) return Target.DISCONNECTED;
        if (SystemClock.elapsedRealtime() - service.cachedAt > 200) {
            service.cached = service.inspect();
            service.cachedAt = SystemClock.elapsedRealtime();
        }
        return service.cached;
    }
    static void execute(VideoCommand command, Consumer<Boolean> callback) {
        ShortsAccessibilityService service = connected.get();
        if (service == null || command == null) callback.accept(false); else service.dispatchCommand(command, callback);
    }
    @Override protected void onServiceConnected() {
        connected = new WeakReference<>(this);
        var info = getServiceInfo();
        if (info != null) {
            info.packageNames = new String[]{TargetPolicy.YOUTUBE, TargetPolicy.INSTAGRAM};
            setServiceInfo(info);
        }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { cachedAt = 0; }
    @Override public void onInterrupt() { stopService(new Intent(this, TrackingService.class)); }
    @Override public boolean onUnbind(Intent intent) {
        if (connected.get() == this) connected.clear();
        stopService(new Intent(this, TrackingService.class));
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        if (connected.get() == this) connected.clear();
        stopService(new Intent(this, TrackingService.class));
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private Target inspect() {
        if (!getSystemService(PowerManager.class).isInteractive()
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) return Target.LOCKED;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return Target.OTHER_APP;
        String activePackage = root.getPackageName() == null ? "" : root.getPackageName().toString();
        if (!TargetPolicy.supportedApp(activePackage)) {
            root.recycle();
            return Target.OTHER_APP;
        }
        boolean youtube = TargetPolicy.YOUTUBE.equals(activePackage);
        Target otherScreen = youtube ? Target.YOUTUBE_OTHER : Target.INSTAGRAM_OTHER;
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);
        if (bounds.width() >= bounds.height()) { root.recycle(); return otherScreen; }
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null) {
            boolean typing = focused.isEditable();
            focused.recycle();
            if (typing) { root.recycle(); return otherScreen; }
        }
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        boolean found = false;
        int scanned = 0;
        try {
            while (!queue.isEmpty() && scanned++ < 600) {
                AccessibilityNodeInfo node = queue.remove();
                try {
                    Rect rect = new Rect();
                    node.getBoundsInScreen(rect);
                    String pkg = node.getPackageName() == null ? "" : node.getPackageName().toString();
                    if (activePackage.equals(pkg) && node.isVisibleToUser() && rect.intersect(bounds)
                            && TargetPolicy.playerContainer(pkg, node.getViewIdResourceName(),
                            rect.width(), rect.height(), bounds.width(), bounds.height())) {
                        found = true;
                        playerBounds = new Rect(youtube ? bounds : rect);
                        break;
                    }
                    for (int i = 0; i < node.getChildCount() && queue.size() < 600; i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) queue.add(child);
                    }
                } finally { node.recycle(); }
            }
        } finally { while (!queue.isEmpty()) queue.remove().recycle(); }
        return found ? youtube ? Target.SHORTS : Target.REELS : otherScreen;
    }

    private void dispatchCommand(VideoCommand command, Consumer<Boolean> callback) {
        long now = SystemClock.elapsedRealtime();
        // Always recheck the foreground window immediately before dispatch.
        if (inFlight || now - lastGesture < 1800 || !inspect().playback()) { callback.accept(false); return; }
        Rect bounds = new Rect(playerBounds);
        float x = bounds.left + bounds.width() * (command.isTap() ? 0.50f : 0.48f);
        Path path = new Path();
        path.moveTo(x, bounds.top + bounds.height() * (float)command.startY());
        if (!command.isTap()) path.lineTo(x, bounds.top + bounds.height() * (float)command.endY());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, command.durationMs())).build();
        inFlight = true;
        lastGesture = now;
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) { inFlight = false; callback.accept(true); }
            @Override public void onCancelled(GestureDescription description) { inFlight = false; callback.accept(false); }
        }, null);
        if (!accepted) { inFlight = false; callback.accept(false); }
    }
}
