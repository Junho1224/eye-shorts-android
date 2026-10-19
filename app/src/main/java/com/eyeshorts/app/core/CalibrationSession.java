package com.eyeshorts.app.core;

import java.util.ArrayList;
import java.util.List;

public final class CalibrationSession {
    public enum Stage { CENTER, BOTTOM, TOP, RIGHT, COMPLETE, FAILED }
    private final InputMode mode;
    private Stage stage;
    private long start = -1, previous = -1;
    private final List<GazeSample> samples = new ArrayList<>();
    private List<GazeSample> centerSamples;
    private Calibration result;
    private String error = "";
    private double progress;

    public CalibrationSession() { this(InputMode.DWELL); }
    public CalibrationSession(InputMode mode) {
        if (!mode.needsCalibration()) throw new IllegalArgumentException("Wink needs no gaze calibration");
        this.mode = mode;
        stage = mode == InputMode.SWIPE_UP ? Stage.BOTTOM : Stage.CENTER;
    }

    public Stage stage() { return stage; }
    public Calibration result() { return result; }
    public String error() { return error; }
    public double progress() { return progress; }

    public void update(GazeSample sample) {
        if (stage == Stage.COMPLETE || stage == Stage.FAILED) return;
        if (!sample.usable()) {
            clearStep();
            return;
        }
        if (mode == InputMode.RIGHT_RETURN) sample = sample.horizontalAxis();
        long now = sample.timeMs();
        if (previous >= 0 && (now <= previous || now - previous > 350)) clearStep();
        previous = now;
        if (start < 0) start = now;
        long elapsed = now - start;
        progress = Math.min(1, elapsed / 3000.0);
        if (elapsed >= 800) samples.add(sample);
        if (elapsed < 3000) return;
        if (samples.size() < 14) { clearStep(); return; }
        double noise = spread(samples);
        if (noise > 0.025) { fail("시선이 많이 흔들렸어요. 휴대폰을 고정하고 다시 보정해주세요."); return; }
        if (stage == Stage.CENTER || stage == Stage.BOTTOM) {
            centerSamples = new ArrayList<>(samples);
            stage = mode == InputMode.RIGHT_RETURN ? Stage.RIGHT : Stage.TOP;
            clearStep();
            return;
        }
        double hx = median(centerSamples, 1), hy = median(centerSamples, 2);
        double scale = median(centerSamples, 3);
        if (Math.abs(median(samples, 1) - hx) > 0.06
                || Math.abs(median(samples, 2) - hy) > 0.07
                || Math.abs(median(samples, 3) / scale - 1) > 0.15) {
            fail("머리가 움직였어요. 머리는 그대로 두고 눈만 움직여 다시 보정해주세요."); return;
        }
        result = new Calibration(median(centerSamples, 0), median(samples, 0), hx, hy, scale,
                Math.max(spread(centerSamples), noise));
        if (!result.valid()) {
            boolean tooSmall = Math.abs(result.target() - result.center()) < 0.012;
            result = null;
            fail(tooSmall ? "보정 미완료 · 두 점을 볼 때의 눈동작을 구분하지 못했어요. 휴대폰을 고정하고 두 점을 차례로 바라보며 다시 시도해주세요."
                    : "보정 미완료 · 두 시선의 측정값이 겹쳐요. 휴대폰을 고정하고 눈에 빛이 고르게 들어오는 곳에서 다시 시도해주세요.");
            return;
        }
        stage = Stage.COMPLETE;
    }

    private void fail(String message) { stage = Stage.FAILED; error = message; }
    private void clearStep() { samples.clear(); start = previous = -1; progress = 0; }
    private static double value(GazeSample s, int field) {
        return switch (field) { case 1 -> s.headX(); case 2 -> s.headY(); case 3 -> s.faceScale(); default -> s.vertical(); };
    }
    private static double median(List<GazeSample> list, int field) {
        double[] sorted = list.stream().mapToDouble(s -> value(s, field)).sorted().toArray();
        return sorted[sorted.length / 2];
    }
    private static double spread(List<GazeSample> list) {
        double mid = median(list, 0);
        double[] errors = list.stream().mapToDouble(s -> Math.abs(s.vertical() - mid)).sorted().toArray();
        return errors[errors.length / 2] * 1.4826;
    }
}
