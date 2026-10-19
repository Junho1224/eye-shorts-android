package com.eyeshorts.app.core;

/** Gives an engaged mouth priority and shares the gesture cooldown with the eye detector. */
public final class FaceCommandDetector implements CommandDetector {
    private static final long COOLDOWN_MS=1800;
    private final CommandDetector eyes;
    private final MouthTrigger mouth=new MouthTrigger();
    private final boolean mouthEnabled;
    private long lastFire=-100_000;
    private boolean waitingForOpenEyes;

    public FaceCommandDetector(CommandDetector eyes,boolean mouthEnabled) {
        this.eyes=eyes; this.mouthEnabled=mouthEnabled;
    }
    @Override public void reset() { eyes.reset(); mouth.reset(); waitingForOpenEyes=false; }
    @Override public GazeTrigger.Update update(GazeSample sample,boolean allowed) {
        if (mouthEnabled) {
            GazeTrigger.Update opening=mouth.update(sample,allowed);
            if (mouth.engaged()) {
                waitingForOpenEyes=true;
                eyes.update(sample,false);
                return limit(opening,sample.timeMs());
            }
        }
        if (waitingForOpenEyes) {
            if (allowed && FaceQuality.from(sample)==FaceQuality.READY) waitingForOpenEyes=false;
            else {
                eyes.update(sample,false);
                return new GazeTrigger.Update(false,0,false,"입을 닫고 양쪽 눈을 떠주세요");
            }
        }
        return limit(eyes.update(sample,allowed),sample.timeMs());
    }
    private GazeTrigger.Update limit(GazeTrigger.Update update,long now) {
        if (!update.fire()) return update;
        if (now-lastFire<COOLDOWN_MS)
            return new GazeTrigger.Update(false,0,false,"연속 입력 대기 중 · 잠시 뒤 다시 시도해주세요",update.command());
        lastFire=now;
        return update;
    }
}
