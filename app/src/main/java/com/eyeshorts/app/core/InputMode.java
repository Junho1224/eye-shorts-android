package com.eyeshorts.app.core;

public enum InputMode {
    WINK("한쪽 눈 윙크", "오른쪽 눈을 감으면 다음 · 왼쪽 눈을 감으면 이전"),
    RIGHT_RETURN("오른쪽 보기 → 중앙", "오른쪽을 잠깐 본 뒤 중앙으로 돌아오세요"),
    SWIPE_UP("아래 → 위로 눈 움직이기", "중앙을 본 뒤 아래쪽에서 위쪽으로 눈을 움직여주세요"),
    DWELL("위쪽을 잠깐 응시하기", "중앙을 본 뒤 위쪽 점을 바라봐주세요");

    private final String title, instruction;
    InputMode(String title, String instruction) { this.title = title; this.instruction = instruction; }
    public String title() { return title; }
    public String instruction() { return instruction; }
    public boolean needsCalibration() { return this != WINK; }
}
