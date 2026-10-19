package com.eyeshorts.app.core;

/** A calibrated right glance followed by a return to center; a held glance never fires. */
public final class RightReturnTrigger implements CommandDetector {
    private final Calibration calibration;
    private long previous=-1, centerSince=-1, rightSince=-1, returnSince=-1, lastFire=-100_000;
    private boolean ready, returning;
    public RightReturnTrigger(Calibration calibration) {
        if (!calibration.valid()) throw new IllegalArgumentException("Invalid calibration");
        this.calibration = calibration;
    }
    @Override public void reset() { previous=centerSince=rightSince=returnSince=-1; ready=returning=false; }
    private GazeTrigger.Update hint(double progress, String text) { return new GazeTrigger.Update(false, progress, ready, text); }
    @Override public GazeTrigger.Update update(GazeSample source, boolean allowed) {
        GazeSample s = source.horizontalAxis(); long now=s.timeMs();
        if (!allowed || !calibration.matchesPose(s)) { reset(); return hint(0, !allowed ? "쇼츠·릴스 화면에서 대기 중" : "눈을 뜨고 보정할 때의 자세로 바라봐주세요"); }
        if (previous>=0 && now<=previous) { reset(); return hint(0, "중앙을 다시 바라봐주세요"); }
        if (previous>=0 && now-previous>350) reset();
        previous=now;
        double p=calibration.position(s);
        if (!Double.isFinite(p) || p < -1 || p > 2) { reset(); return hint(0, "중앙을 다시 바라봐주세요"); }
        boolean center=Math.abs(p)<0.30;
        if (!ready) {
            if (center && now-lastFire>=1800) {
                if (centerSince<0) centerSince=now;
                if (now-centerSince>=300) ready=true;
            } else centerSince=-1;
            return hint(0, ready ? "오른쪽을 잠깐 바라봐주세요" : "중앙을 잠깐 바라봐주세요");
        }
        if (!returning) {
            if (p>=0.72) {
                if (rightSince<0) rightSince=now;
                if (now-rightSince>=200) returning=true;
            } else rightSince=-1;
            return hint(returning ? 0.6 : 0.2, returning ? "이제 중앙으로 돌아오세요" : "오른쪽을 잠깐 바라봐주세요");
        }
        if (now-rightSince>1600 || p < -0.5) { reset(); return hint(0, "중앙을 다시 바라봐주세요"); }
        if (center) {
            if (returnSince<0) returnSince=now;
            if (now-returnSince>=120) {
                lastFire=now; reset();
                return new GazeTrigger.Update(true,1,false,"오른쪽 → 중앙 인식 완료");
            }
        } else returnSince=-1;
        return hint(0.7,"중앙으로 돌아오세요");
    }
}
