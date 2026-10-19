package com.eyeshorts.app.core;

/** Counts detections, not ground-truth accuracy. Time advances only while the target is visible. */
public final class EvaluationSession {
    public enum Kind { TRIAL20, PASSIVE5 }
    public record Snapshot(Kind kind, int detections, long elapsedMs, int frames, int validFrames, boolean finished) {
        public int facePercent() { return frames == 0 ? 0 : (int)(100L*validFrames/frames); }
        public String summary() {
            String prefix = kind == Kind.TRIAL20 ? "20회 시도(사용자 확인) · 감지 " : "5분 관찰 · 명령 감지 ";
            return prefix + detections + "회 · 얼굴 확인 " + facePercent() + "%"
                    + (facePercent()<80 ? " · 얼굴 인식이 부족해 재측정 권장" : "");
        }
    }
    private final Kind kind;
    private long previous=-1, elapsed;
    private int detections, frames, validFrames;
    private boolean previousAllowed, finished;
    public EvaluationSession(Kind kind) { this.kind=kind; }
    public void update(long now, boolean allowed, boolean faceValid, boolean fire) {
        if (finished) return;
        if (previous>=0 && now<=previous) { previousAllowed=false; return; }
        if (previous>=0 && now>previous && now-previous<=350 && previousAllowed && allowed) elapsed+=now-previous;
        previous=now; previousAllowed=allowed;
        if (allowed) { frames++; if (faceValid) validFrames++; if (fire) detections++; }
        if (kind==Kind.PASSIVE5 && elapsed>=300_000) { elapsed=300_000; finished=true; }
    }
    public void finishTrial() { if (kind==Kind.TRIAL20) finished=true; }
    public Snapshot snapshot() { return new Snapshot(kind,detections,elapsed,frames,validFrames,finished); }
}
