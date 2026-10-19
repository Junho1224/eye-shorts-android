package com.eyeshorts.app.core;

/** Send once when the displayed face state becomes ONE_CLOSED. */
public final class WinkTrigger implements CommandDetector {
    private static final long COOLDOWN_MS = 1800;
    private long previous = -1, lastFire = -100_000;
    private boolean latched;

    @Override public void reset() {
        previous = -1; latched = false;
        // Keep the swipe cooldown when a caller resets the detector.
    }
    private GazeTrigger.Update hint(String text) {
        return new GazeTrigger.Update(false, 0, false, text);
    }
    /** Sides are the wearer's physical eyes, independent of the mirrored preview. */
    public static VideoCommand directionFor(GazeSample sample) {
        if (FaceQuality.from(sample) != FaceQuality.ONE_CLOSED) return null;
        return sample.rightOpenness() <= FaceQuality.OPEN_THRESHOLD ? VideoCommand.NEXT : VideoCommand.PREVIOUS;
    }
    @Override public GazeTrigger.Update update(GazeSample sample, boolean allowed) {
        long now = sample.timeMs();
        if (previous >= 0 && now <= previous)
            return hint("새 카메라 측정을 기다리는 중이에요");
        previous = now;
        if (!allowed) return hint("쇼츠·릴스 화면에서 대기 중");

        FaceQuality quality = FaceQuality.from(sample);
        if (quality == FaceQuality.READY) {
            latched = false;
            return hint("오른쪽 눈은 다음 · 왼쪽 눈은 이전");
        }
        // Face loss, both eyes closed, or leaving the target cannot rearm a held closure.
        if (quality != FaceQuality.ONE_CLOSED) return hint(quality.label());
        if (latched) return hint("한 번 인식했어요 · 다시 넘기려면 양쪽 눈을 떠주세요");
        latched = true;
        if (now - lastFire < COOLDOWN_MS)
            return hint("연속 넘김 대기 중 · 잠시 뒤 눈을 떴다 다시 감아주세요");

        lastFire = now;
        VideoCommand command = directionFor(sample);
        return new GazeTrigger.Update(true, 1, false, "한쪽 눈 감음 · " + command.label(), command);
    }
}
