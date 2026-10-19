package com.eyeshorts.app.core;

/** Continuous dwell, neutral rearming and a cooldown. All input uses a monotonic clock. */
public final class GazeTrigger implements CommandDetector {
    public record Update(boolean fire, double progress, boolean armed, String hint, VideoCommand command) {
        public Update(boolean fire, double progress, boolean armed, String hint) {
            this(fire, progress, armed, hint, VideoCommand.NEXT);
        }
    }
    private final Calibration calibration;
    private final long dwellMs;
    private long previous = -1, centerSince = -1, targetSince = -1, lastFire = -100_000;
    private boolean armed;
    private Double filtered;

    public GazeTrigger(Calibration calibration, long dwellMs) {
        if (!calibration.valid()) throw new IllegalArgumentException("Invalid calibration");
        this.calibration = calibration;
        this.dwellMs = Math.max(600, Math.min(1800, dwellMs));
    }

    public void reset() {
        previous = centerSince = targetSince = -1;
        armed = false;
        filtered = null;
    }

    public Update update(GazeSample sample, boolean allowed) {
        long now = sample.timeMs();
        if (!allowed || !calibration.matchesPose(sample)) {
            reset();
            return new Update(false, 0, false, !allowed ? "쇼츠·릴스 화면에서 대기 중" : "눈을 뜨고 보정할 때의 자세로 바라봐주세요");
        }
        if (previous >= 0 && now <= previous) {
            reset();
            return new Update(false, 0, false, "다시 중앙을 바라봐주세요");
        }
        if (previous >= 0 && now - previous > 350) reset();
        previous = now;
        double position = calibration.position(sample);
        // A sudden outlier must not accumulate dwell through the filter.
        if (!Double.isFinite(position) || Math.abs(position) > 3) {
            reset();
            return new Update(false, 0, false, "다시 중앙을 바라봐주세요");
        }
        filtered = filtered == null ? position : filtered * 0.55 + position * 0.45;
        boolean neutral = Math.abs(position) < 0.35 && Math.abs(filtered) < 0.35;
        boolean target = position >= 0.72 && filtered >= 0.72;
        if (!armed) {
            targetSince = -1;
            if (neutral && now - lastFire >= 1800) {
                if (centerSince < 0) centerSince = now;
                if (now - centerSince >= 450) armed = true;
            } else centerSince = -1;
            return new Update(false, 0, armed, armed ? "위쪽을 바라보면 넘겨요" : "중앙을 잠깐 바라봐주세요");
        }
        if (!target) {
            targetSince = -1;
            return new Update(false, 0, true, "위쪽을 바라보면 넘겨요");
        }
        if (targetSince < 0) targetSince = now;
        double progress = Math.min(1, (double) (now - targetSince) / dwellMs);
        if (progress >= 1) {
            lastFire = now;
            reset();
            return new Update(true, 1, false, "인식 완료 · 중앙을 다시 바라봐주세요");
        }
        return new Update(false, progress, true, "조금만 더 바라봐주세요");
    }
}
