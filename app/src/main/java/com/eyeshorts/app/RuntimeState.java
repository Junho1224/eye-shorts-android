package com.eyeshorts.app;

import androidx.lifecycle.MutableLiveData;
import com.eyeshorts.app.core.EvaluationSession;

final class RuntimeState {
    static boolean screenVisible;
    enum Mode { OFF, CHECK, CALIBRATING, PRACTICE, TRACKING, OBSERVING, PAUSED }
    record Status(Mode mode, boolean cameraReady, String message, double progress,
                  String calibrationStage, int practiceCount, int gestureCount, int nextPracticeCount, int previousPracticeCount,
                  int playbackPracticeCount, boolean mouthPracticeOnly,
                  String eyeStatus, EvaluationSession.Snapshot evaluation) {
        boolean running() { return mode != Mode.OFF; }
    }
    static final MutableLiveData<Status> live = new MutableLiveData<>(new Status(Mode.OFF, false,
            "입력 방식을 선택하고 연습해보세요", 0, "", 0, 0, 0, 0, 0, false,"카메라 꺼짐",null));
    private RuntimeState() {}
}
