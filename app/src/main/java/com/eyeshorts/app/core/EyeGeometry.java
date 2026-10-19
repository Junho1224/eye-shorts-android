package com.eyeshorts.app.core;

/** Pixel coordinates after rotation. Eye corners anchor gaze independently of eyelid motion. */
public final class EyeGeometry {
    public record Eye(double vertical, double openness, double centerX, double centerY, double width, double horizontal) {
        public boolean openEnough() { return Double.isFinite(vertical) && openness > 0.13 && width > 12; }
    }
    private EyeGeometry() {}

    public static Eye measure(double ax, double ay, double bx, double by,
                              double topX, double topY, double bottomX, double bottomY,
                              double irisX, double irisY) {
        double dx = bx - ax, dy = by - ay;
        if (dx < 0) { dx = -dx; dy = -dy; }
        double width = Math.hypot(dx, dy), cx = (ax + bx) / 2, cy = (ay + by) / 2;
        if (width < 1) return new Eye(Double.NaN, 0, cx, cy, width, Double.NaN);
        double vx = -dy / width, vy = dx / width;
        return new Eye(((irisX - cx) * vx + (irisY - cy) * vy) / width,
                Math.max(0,((bottomX - topX) * vx + (bottomY - topY) * vy) / width), cx, cy, width,
                ((irisX - cx) * dx + (irisY - cy) * dy) / (width * width));
    }
}
