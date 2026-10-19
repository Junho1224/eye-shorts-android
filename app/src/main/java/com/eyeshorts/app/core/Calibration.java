package com.eyeshorts.app.core;

public record Calibration(double center, double target, double headX, double headY,
                          double faceScale, double noise) {
    public boolean valid() {
        return Double.isFinite(center) && Double.isFinite(target)
                && Double.isFinite(headX) && Double.isFinite(headY)
                && Double.isFinite(faceScale) && faceScale > 0
                && Double.isFinite(noise) && noise >= 0 && noise <= 0.025
                && Math.abs(target - center) >= Math.max(0.012, noise * 3.5);
    }

    public double position(GazeSample sample) {
        return (sample.vertical() - center) / (target - center);
    }

    public boolean matchesPose(GazeSample sample) {
        return sample.usable() && Math.abs(sample.headX() - headX) < 0.10
                && Math.abs(sample.headY() - headY) < 0.12
                && sample.faceScale() / faceScale > 0.75
                && sample.faceScale() / faceScale < 1.30;
    }
}
