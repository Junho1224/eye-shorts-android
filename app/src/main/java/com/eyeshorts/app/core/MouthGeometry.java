package com.eyeshorts.app.core;

/** Inner lip gap projected perpendicular to the mouth corners, normalized by mouth width. */
public final class MouthGeometry {
    private MouthGeometry() {}
    public static double openness(double ax, double ay, double bx, double by,
                                  double upperX, double upperY, double lowerX, double lowerY) {
        if (!Double.isFinite(ax) || !Double.isFinite(ay) || !Double.isFinite(bx) || !Double.isFinite(by)
                || !Double.isFinite(upperX) || !Double.isFinite(upperY)
                || !Double.isFinite(lowerX) || !Double.isFinite(lowerY)) return Double.NaN;
        double dx=bx-ax, dy=by-ay, width=Math.hypot(dx,dy);
        if (width<=12) return Double.NaN;
        double gap=Math.abs(-(lowerX-upperX)*dy/width+(lowerY-upperY)*dx/width);
        return gap/width;
    }
}
