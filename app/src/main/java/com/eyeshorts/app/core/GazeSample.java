package com.eyeshorts.app.core;

/** Eye-local measurements; left/right openness refers to the person's physical eyes. No image is retained. */
public record GazeSample(long timeMs, double vertical, double headX, double headY,
                         double faceScale, double roll, boolean eyesOpen, int faces,
                         double horizontal, double leftOpenness, double rightOpenness, double eyeWidth,
                         double mouthOpenness) {
    public GazeSample(long timeMs, double vertical, double headX, double headY,
                      double faceScale, double roll, boolean eyesOpen, int faces,
                      double horizontal, double leftOpenness, double rightOpenness, double eyeWidth) {
        this(timeMs, vertical, headX, headY, faceScale, roll, eyesOpen, faces,
                horizontal, leftOpenness, rightOpenness, eyeWidth, Double.NaN);
    }
    public GazeSample(long timeMs, double vertical, double headX, double headY,
                      double faceScale, double roll, boolean eyesOpen, int faces) {
        this(timeMs, vertical, headX, headY, faceScale, roll, eyesOpen, faces,
                0, eyesOpen ? 0.24 : 0.02, eyesOpen ? 0.24 : 0.02, 20);
    }
    public boolean faceUsable() {
        return faces == 1 && Double.isFinite(headX) && Math.abs(headX) < 0.25
                && Double.isFinite(headY) && Double.isFinite(faceScale) && faceScale > 0
                && Double.isFinite(roll) && Math.abs(roll) < 0.22
                && Double.isFinite(leftOpenness) && Double.isFinite(rightOpenness)
                && leftOpenness >= 0 && rightOpenness >= 0 && Double.isFinite(eyeWidth) && eyeWidth > 12;
    }
    public boolean usable() {
        return faceUsable() && eyesOpen && Double.isFinite(vertical) && Double.isFinite(horizontal);
    }
    public GazeSample horizontalAxis() {
        return new GazeSample(timeMs, horizontal, headX, headY, faceScale, roll, eyesOpen,
                faces, horizontal, leftOpenness, rightOpenness, eyeWidth, mouthOpenness);
    }
}
