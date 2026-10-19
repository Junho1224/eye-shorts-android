package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class EyeGeometryTest {
    private static final double[] OPEN = {0,0,100,0,50,-12,50,12,50,-3};
    private EyeGeometry.Eye measure(double[] p) {
        return EyeGeometry.measure(p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],p[8],p[9]);
    }
    @Test public void eyelidFollowingIrisDoesNotCancelGazeSignal() {
        var still = measure(OPEN);
        var movedLids = EyeGeometry.measure(0,0,100,0,50,-15,50,9,50,-3);
        assertEquals(-0.03, still.vertical(), 1e-9);
        assertEquals(still.vertical(), movedLids.vertical(), 1e-9);
    }
    @Test public void translatingTheFaceDoesNotChangeGaze() {
        double[] moved = OPEN.clone();
        for (int i=0; i<moved.length; i+=2) { moved[i]+=245; moved[i+1]+=103; }
        assertEquals(measure(OPEN).vertical(), measure(moved).vertical(), 1e-9);
    }
    @Test public void scalingTheFaceDoesNotChangeNormalizedGaze() {
        double[] moved = OPEN.clone();
        for (int i=0; i<moved.length; i++) moved[i]*=1.5;
        assertEquals(measure(OPEN).vertical(), measure(moved).vertical(), 1e-9);
    }
    @Test public void moderateRollDoesNotChangeNormalizedGaze() {
        double[] moved = OPEN.clone(); double angle = 0.15;
        for (int i=0; i<moved.length; i+=2) {
            moved[i] = OPEN[i]*Math.cos(angle)-OPEN[i+1]*Math.sin(angle);
            moved[i+1] = OPEN[i]*Math.sin(angle)+OPEN[i+1]*Math.cos(angle);
        }
        assertEquals(measure(OPEN).vertical(), measure(moved).vertical(), 1e-9);
    }
    @Test public void aClosedLidIsRejectedEvenWithAVisibleIrisEstimate() {
        assertFalse(EyeGeometry.measure(0,0,100,0,50,-2,50,2,50,-3).openEnough());
    }
    @Test public void aTooSmallEyeIsRejected() {
        assertFalse(EyeGeometry.measure(0,0,10,0,5,-2,5,2,5,0).openEnough());
    }
    @Test public void degenerateCornersCannotProduceAValidMeasurement() {
        assertFalse(EyeGeometry.measure(0,0,0,0,0,-2,0,2,0,0).openEnough());
    }
    @Test public void horizontalIrisMotionIsIndependentOfVerticalMotion() {
        var right=EyeGeometry.measure(0,0,100,0,50,-12,50,12,57,0);
        var up=EyeGeometry.measure(0,0,100,0,50,-12,50,12,50,-7);
        assertTrue(right.horizontal()>0); assertEquals(0,right.vertical(),1e-9);
        assertEquals(0,up.horizontal(),1e-9); assertTrue(up.vertical()<0);
    }
    @Test public void tinyClosedLidInversionsRemainAClosedEyeMeasurement() {
        var closed=EyeGeometry.measure(0,0,100,0,50,0.1,50,-0.1,50,0);
        assertEquals(0,closed.openness(),1e-9); assertFalse(closed.openEnough());
    }
}
