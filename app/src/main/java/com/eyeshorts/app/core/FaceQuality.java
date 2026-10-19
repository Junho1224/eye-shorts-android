package com.eyeshorts.app.core;

public enum FaceQuality {
    NO_FACE("얼굴을 찾는 중 · 카메라를 바라봐주세요"), MULTIPLE("한 사람만 화면에 나오게 해주세요"),
    TOO_SMALL("눈이 작게 보여요 · 휴대폰을 조금 가까이 해주세요"), POSE("얼굴 위치 조정 필요 · 정면을 바라봐주세요"),
    CLOSED("양쪽 눈 감음"), ONE_CLOSED("한쪽 눈 감음"), READY("얼굴 인식 중 · 양쪽 눈 뜸");
    public static final double OPEN_THRESHOLD = 0.13;
    private final String label;
    FaceQuality(String label) { this.label = label; }
    public String label() { return label; }
    public static FaceQuality from(GazeSample s) {
        if (s.faces() == 0) return NO_FACE;
        if (s.faces() > 1) return MULTIPLE;
        if (!Double.isFinite(s.eyeWidth()) || s.eyeWidth() <= 12) return TOO_SMALL;
        if (!s.faceUsable()) return POSE;
        boolean left = s.leftOpenness() > OPEN_THRESHOLD, right = s.rightOpenness() > OPEN_THRESHOLD;
        return left && right ? READY : !left && !right ? CLOSED : ONE_CLOSED;
    }
}
