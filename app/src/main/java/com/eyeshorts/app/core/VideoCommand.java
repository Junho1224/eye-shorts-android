package com.eyeshorts.app.core;

/** Fractions are relative to the active full-screen player, in screen coordinates. */
public enum VideoCommand {
    NEXT("다음 영상", 0.77, 0.25),
    PREVIOUS("이전 영상", 0.25, 0.77),
    PLAY_PAUSE("영상 재생/일시정지", 0.50, 0.50);

    private final String label;
    private final double startY, endY;
    VideoCommand(String label, double startY, double endY) {
        this.label = label; this.startY = startY; this.endY = endY;
    }
    public String label() { return label; }
    public double startY() { return startY; }
    public double endY() { return endY; }
    public boolean isTap() { return this==PLAY_PAUSE; }
    public long durationMs() { return isTap() ? 80 : 300; }
}
