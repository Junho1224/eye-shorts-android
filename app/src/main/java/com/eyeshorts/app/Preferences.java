package com.eyeshorts.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.eyeshorts.app.core.Calibration;
import com.eyeshorts.app.core.InputMode;
import com.eyeshorts.app.core.EvaluationSession;

final class Preferences {
    private final SharedPreferences prefs;
    Preferences(Context context) { prefs = context.getSharedPreferences("eyeshorts", Context.MODE_PRIVATE); }
    boolean disclosed() { return prefs.getInt("disclosureVersion", 0) == 2; }
    void acceptDisclosure() { prefs.edit().putInt("disclosureVersion", 2).apply(); }
    boolean mouthEnabled() { return prefs.getBoolean("mouthEnabled",true); }
    void mouthEnabled(boolean enabled) { prefs.edit().putBoolean("mouthEnabled",enabled).apply(); }
    long dwellMs() { return prefs.getLong("dwell", 900); }
    void dwellMs(long duration) { prefs.edit().putLong("dwell", Math.max(600, Math.min(1800, duration))).apply(); }
    InputMode mode() {
        try { return InputMode.valueOf(prefs.getString("inputMode", InputMode.WINK.name())); }
        catch (IllegalArgumentException e) { return InputMode.WINK; }
    }
    void mode(InputMode mode) { prefs.edit().putString("inputMode", mode.name()).apply(); }
    private String key(String name) { return mode().name() + "." + name; }
    void save(Calibration c) {
        prefs.edit().putInt(key("calibrationVersion"), 3)
                .putString(key("center"), Double.toString(c.center())).putString(key("target"), Double.toString(c.target()))
                .putString(key("headX"), Double.toString(c.headX())).putString(key("headY"), Double.toString(c.headY()))
                .putString(key("scale"), Double.toString(c.faceScale())).putString(key("noise"), Double.toString(c.noise())).apply();
    }
    Calibration calibration() {
        if (prefs.getInt(key("calibrationVersion"), 0) != 3) return null;
        try {
            Calibration c = new Calibration(number("center"), number("target"), number("headX"),
                    number("headY"), number("scale"), number("noise"));
            return c.valid() ? c : null;
        } catch (NumberFormatException e) { return null; }
    }
    private double number(String name) { return Double.parseDouble(prefs.getString(key(name), "NaN")); }
    boolean ready() { return !mode().needsCalibration() || calibration()!=null; }
    void saveReport(InputMode mode, EvaluationSession.Snapshot result) {
        if (!result.finished()) return;
        prefs.edit().putString(mode.name()+".report."+result.kind().name(),result.summary()).apply();
    }
    String report(InputMode mode) {
        return prefs.getString(mode.name()+".report.TRIAL20","20회 입력 · 미측정") + "\n"
                + prefs.getString(mode.name()+".report.PASSIVE5","5분 관찰 · 미측정");
    }
}
