package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class MouthGeometryTest {
    @Test public void touchingInnerLipsAreClosed() {
        assertEquals(0,MouthGeometry.openness(0,0,100,0,50,10,50,10),0);
    }
    @Test public void openingIsNormalizedByMouthWidth() {
        assertEquals(0.3,MouthGeometry.openness(0,0,100,0,50,10,50,40),1e-9);
        assertEquals(0.3,MouthGeometry.openness(0,0,200,0,100,20,100,80),1e-9);
    }
    @Test public void faceRollDoesNotChangeTheOpening() {
        assertEquals(0.3,MouthGeometry.openness(0,0,0,100,-10,50,-40,50),1e-9);
    }
    @Test public void translationAndSidewaysLipMovementDoNotInflateTheGap() {
        assertEquals(0.3,MouthGeometry.openness(500,500,600,500,530,540,570,570),1e-9);
    }
    @Test public void aTinyOrCollapsedMouthIsNotAMeasurement() {
        assertTrue(Double.isNaN(MouthGeometry.openness(0,0,0,0,0,0,0,30)));
        assertTrue(Double.isNaN(MouthGeometry.openness(0,0,10,0,5,0,5,30)));
    }
    @Test public void nonFiniteLandmarksAreNotAMeasurement() {
        assertTrue(Double.isNaN(MouthGeometry.openness(0,0,100,0,50,Double.NaN,50,30)));
        assertTrue(Double.isNaN(MouthGeometry.openness(0,0,100,0,50,0,Double.POSITIVE_INFINITY,30)));
    }
    @Test public void horizontalGazeProjectionPreservesTheMouthMeasurement() {
        GazeSample value=new GazeSample(0,0.1,0,0.6,0.24,0,true,1,0.2,0.24,0.24,24,0.35);
        assertEquals(0.35,value.horizontalAxis().mouthOpenness(),0);
        assertTrue(Double.isNaN(new GazeSample(0,0,0,0.6,0.24,0,true,1).mouthOpenness()));
    }
}
