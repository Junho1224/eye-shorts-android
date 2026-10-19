package com.eyeshorts.app.core;

/** A deliberate bottom-to-top transition. Calibration reference is bottom, target is top. */
public final class EyeSwipeTrigger implements CommandDetector {
    private enum Stage { NEUTRAL, BOTTOM, READY, RISING, CONFIRM }
    private final Calibration calibration;
    private Stage stage = Stage.NEUTRAL;
    private long previous = -1, centerSince = -1, bottomSince = -1;
    private long bottomLast = -1, risingAt = -1, topSince = -1, lastFire = -100_000;
    private double previousPosition = 0.5;

    public EyeSwipeTrigger(Calibration calibration) {
        if (!calibration.valid()) throw new IllegalArgumentException("Invalid calibration");
        this.calibration = calibration;
    }
    public void reset() {
        stage = Stage.NEUTRAL;
        previous = centerSince = bottomSince = bottomLast = risingAt = topSince = -1;
    }
    private GazeTrigger.Update hint(double progress, String hint) {
        return new GazeTrigger.Update(false, progress, stage != Stage.NEUTRAL, hint);
    }
    public GazeTrigger.Update update(GazeSample sample, boolean allowed) {
        long now = sample.timeMs();
        if (!allowed || !calibration.matchesPose(sample)) {
            reset();
            return hint(0, !allowed ? "쇼츠·릴스 화면에서 대기 중" : "눈을 뜨고 보정할 때의 자세로 바라봐주세요");
        }
        if (previous >= 0 && now <= previous) { reset(); return hint(0, "다시 중앙을 바라봐주세요"); }
        if (previous >= 0 && now - previous > 350) reset();
        double position = calibration.position(sample);
        if (!Double.isFinite(position) || position < -1 || position > 2) {
            reset(); return hint(0, "다시 중앙을 바라봐주세요");
        }
        previous = now;
        if (stage == Stage.NEUTRAL) {
            if (position >= 0.30 && position <= 0.70 && now - lastFire >= 1800) {
                if (centerSince < 0) centerSince = now;
                if (now - centerSince >= 250) stage = Stage.BOTTOM;
            } else centerSince = -1;
            previousPosition = position;
            return hint(0, stage == Stage.BOTTOM ? "아래쪽 점을 잠깐 바라봐주세요" : "중앙을 잠깐 바라봐주세요");
        }
        if (stage == Stage.BOTTOM) {
            if (position <= 0.22) {
                if (bottomSince < 0) bottomSince = now;
                bottomLast = now;
                if (now - bottomSince >= 180) stage = Stage.READY;
            } else bottomSince = -1;
            previousPosition = position;
            return hint(stage == Stage.READY ? 0.45 : 0.15,
                    stage == Stage.READY ? "이제 눈을 위로 빠르게 움직이세요" : "아래쪽 점을 잠깐 바라봐주세요");
        }
        if (stage == Stage.READY) {
            if (position <= 0.30) { bottomLast = now; previousPosition = position; return hint(0.45, "이제 눈을 위로 빠르게 움직이세요"); }
            risingAt = bottomLast;
            stage = Stage.RISING;
        }
        if (now - risingAt > 900 || position < previousPosition - 0.28) {
            reset(); return hint(0, "중앙을 본 뒤, 아래에서 위로 조금 더 빠르게 움직여주세요");
        }
        previousPosition = position;
        if (stage == Stage.RISING) {
            if (position >= 0.78 && now - risingAt >= 50) { stage = Stage.CONFIRM; topSince = now; }
            return hint(0.65, "위쪽을 보고 있어요");
        }
        if (position < 0.70) { reset(); return hint(0, "다시 중앙을 바라봐주세요"); }
        if (now - topSince >= 60) {
            lastFire = now;
            reset();
            return new GazeTrigger.Update(true, 1, false, "인식 완료 · 중앙을 다시 바라봐주세요");
        }
        return hint(0.85, "위쪽을 보고 있어요");
    }
}
