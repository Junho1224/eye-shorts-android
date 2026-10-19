package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class EvaluationSessionTest {
    @Test public void trialWaitsForUserConfirmationEvenAfterTwentyDetections() {
        EvaluationSession s=new EvaluationSession(EvaluationSession.Kind.TRIAL20);
        for(int i=0;i<25;i++) s.update(i*100,true,true,true);
        assertFalse(s.snapshot().finished()); assertEquals(25,s.snapshot().detections());
        s.finishTrial(); assertTrue(s.snapshot().finished());
    }
    @Test public void passiveTestFinishesAfterFiveMinutesOfActiveFrames() {
        EvaluationSession s=new EvaluationSession(EvaluationSession.Kind.PASSIVE5);
        for(long t=0;t<=300000;t+=100) s.update(t,true,true,t==1000);
        assertTrue(s.snapshot().finished()); assertEquals(1,s.snapshot().detections()); assertEquals(100,s.snapshot().facePercent());
    }
    @Test public void timeInOtherAppsDoesNotCount() {
        EvaluationSession s=new EvaluationSession(EvaluationSession.Kind.PASSIVE5);
        for(long t=0;t<=300000;t+=100) s.update(t,false,true,true);
        assertFalse(s.snapshot().finished()); assertEquals(0,s.snapshot().elapsedMs()); assertEquals(0,s.snapshot().detections());
    }
    @Test public void aFrozenCameraCannotFinishTheTest() {
        EvaluationSession s=new EvaluationSession(EvaluationSession.Kind.PASSIVE5);
        s.update(0,true,true,false); s.update(300000,true,true,false);
        assertFalse(s.snapshot().finished()); assertEquals(0,s.snapshot().elapsedMs());
    }
    @Test public void poorFaceCoverageIsVisibleInTheResult() {
        EvaluationSession s=new EvaluationSession(EvaluationSession.Kind.TRIAL20);
        for(int i=0;i<10;i++) s.update(i*100,true,i==0,false);
        s.finishTrial(); assertEquals(10,s.snapshot().facePercent()); assertTrue(s.snapshot().summary().contains("재측정"));
    }
    @Test public void finishedResultsCannotAccumulateMoreDetections() {
        EvaluationSession s=new EvaluationSession(EvaluationSession.Kind.TRIAL20);
        s.update(0,true,true,true); s.finishTrial(); s.update(100,true,true,true);
        assertEquals(1,s.snapshot().detections());
    }
    @Test public void duplicateFramesDoNotInflateCounts() {
        EvaluationSession s=new EvaluationSession(EvaluationSession.Kind.TRIAL20);
        s.update(100,true,true,true); s.update(100,true,true,true); assertEquals(1,s.snapshot().detections());
    }
}
