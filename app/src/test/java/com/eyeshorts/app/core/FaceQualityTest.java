package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class FaceQualityTest {
    private GazeSample sample(int faces,double l,double r,double roll,double width) {
        return new GazeSample(0,0,0,0.6,0.24,roll,l>0.13 && r>0.13,faces,0,l,r,width);
    }
    @Test public void noFaceHasItsOwnGuidance() { assertEquals(FaceQuality.NO_FACE,FaceQuality.from(sample(0,0.24,0.24,0,24))); }
    @Test public void twoFacesAreRejected() { assertEquals(FaceQuality.MULTIPLE,FaceQuality.from(sample(2,0.24,0.24,0,24))); }
    @Test public void smallEyesPromptForCloserPosition() { assertEquals(FaceQuality.TOO_SMALL,FaceQuality.from(sample(1,0.24,0.24,0,10))); }
    @Test public void excessiveRollRequiresPoseCorrection() { assertEquals(FaceQuality.POSE,FaceQuality.from(sample(1,0.24,0.24,0.5,24))); }
    @Test public void bothClosedAndOneClosedAreDifferent() {
        assertEquals(FaceQuality.CLOSED,FaceQuality.from(sample(1,0.03,0.03,0,24)));
        assertEquals(FaceQuality.ONE_CLOSED,FaceQuality.from(sample(1,0.03,0.24,0,24)));
    }
    @Test public void anOpenFrontalFaceIsReady() { assertEquals(FaceQuality.READY,FaceQuality.from(sample(1,0.24,0.24,0,24))); }
}
