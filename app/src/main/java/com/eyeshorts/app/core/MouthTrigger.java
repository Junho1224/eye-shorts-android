package com.eyeshorts.app.core;

/** A short deliberate opening sends once; a closed mouth is required before another opening. */
public final class MouthTrigger implements CommandDetector {
    public static final double OPEN_THRESHOLD=0.28, CLOSED_THRESHOLD=0.12;
    private static final long HOLD_MS=250, MAX_GAP_MS=350;
    private long previous=-1, openSince=-1;
    private boolean latched, engaged;

    @Override public void reset() { previous=openSince=-1; latched=engaged=false; }
    public boolean engaged() { return engaged; }
    public static boolean measured(GazeSample sample) {
        return sample.faceUsable() && Double.isFinite(sample.mouthOpenness()) && sample.mouthOpenness()>=0;
    }
    public static String stateLabel(GazeSample sample) {
        if (!measured(sample)) return "입을 찾는 중 · 얼굴을 정면으로 보여주세요";
        if (sample.mouthOpenness()>=OPEN_THRESHOLD) return "입 벌림 감지";
        return sample.mouthOpenness()<=CLOSED_THRESHOLD ? "입 닫힘" : "입을 조금 더 벌려주세요";
    }
    private GazeTrigger.Update hint(double progress,String text) {
        return new GazeTrigger.Update(false,progress,false,text,VideoCommand.PLAY_PAUSE);
    }
    @Override public GazeTrigger.Update update(GazeSample sample,boolean allowed) {
        long now=sample.timeMs();
        if (previous>=0 && now<=previous) return hint(0,"새 카메라 측정을 기다리는 중이에요");
        if (previous>=0 && now-previous>MAX_GAP_MS) openSince=-1;
        previous=now;
        if (!allowed || !measured(sample)) {
            openSince=-1; engaged=false;
            // Losing the face or target cannot rearm a mouth that already fired.
            return hint(0,!allowed ? "쇼츠·릴스 화면에서 대기 중" : stateLabel(sample));
        }
        double opening=sample.mouthOpenness();
        if (opening<=CLOSED_THRESHOLD) {
            latched=engaged=false; openSince=-1;
            return hint(0,"입을 약 0.3초 벌리면 영상 재생/일시정지");
        }
        if (opening<OPEN_THRESHOLD) {
            openSince=-1;
            return hint(0,latched ? "입을 닫았다 다시 벌려주세요" : "입을 조금 더 벌려주세요");
        }
        engaged=true;
        if (latched) return hint(0,"한 번 인식했어요 · 입을 닫았다 다시 벌려주세요");
        if (openSince<0) openSince=now;
        double progress=Math.min(1,(now-openSince)/(double)HOLD_MS);
        if (progress<1) return hint(progress,"입 벌림 감지 · 잠깐 유지해주세요");
        latched=true;
        return new GazeTrigger.Update(true,1,false,"입 인식 성공 · 재생/일시정지",VideoCommand.PLAY_PAUSE);
    }
}
