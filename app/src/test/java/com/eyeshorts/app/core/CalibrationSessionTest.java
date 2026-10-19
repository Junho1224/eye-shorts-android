package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class CalibrationSessionTest {
    private static GazeSample sample(long time, double value, double headY) {
        return new GazeSample(time, value, 0, headY, 0.24, 0, true, 1);
    }
    private void feed(CalibrationSession session, long start, double value, double headY) {
        for (long t=start; t<=start+3000; t+=100) session.update(sample(t, value, headY));
    }
    @Test public void stableDistinctGazesProduceACalibration() {
        CalibrationSession session = new CalibrationSession();
        feed(session, 0, 0, 0.6);
        assertEquals(CalibrationSession.Stage.TOP, session.stage());
        feed(session, 3100, -0.05, 0.6);
        assertEquals(CalibrationSession.Stage.COMPLETE, session.stage());
        assertTrue(session.result().valid());
    }
    @Test public void indistinguishableGazesAreRejected() {
        CalibrationSession session = new CalibrationSession();
        feed(session, 0, 0, 0.6); feed(session, 3100, 0.003, 0.6);
        assertEquals(CalibrationSession.Stage.FAILED, session.stage());
        assertNull(session.result());
    }
    @Test public void aHeadMovementCannotMasqueradeAsAnEyeMovement() {
        CalibrationSession session = new CalibrationSession();
        feed(session, 0, 0, 0.6); feed(session, 3100, -0.05, 0.8);
        assertEquals(CalibrationSession.Stage.FAILED, session.stage());
    }
    @Test public void absentFaceRestartsTheCurrentStep() {
        CalibrationSession session = new CalibrationSession();
        for (long t=0; t<2400; t+=100) session.update(sample(t, 0, 0.6));
        session.update(new GazeSample(2400, 0, 0, 0.6, 0.24, 0, false, 0));
        for (long t=2500; t<=4000; t+=100) session.update(sample(t, 0, 0.6));
        assertEquals(CalibrationSession.Stage.CENTER, session.stage());
    }
    @Test public void aPauseInTheCameraCannotCompleteCalibration() {
        CalibrationSession session = new CalibrationSession();
        for (long t=0; t<2000; t+=100) session.update(sample(t, 0, 0.6));
        session.update(sample(10000, 0, 0.6));
        assertEquals(CalibrationSession.Stage.CENTER, session.stage());
        assertEquals(0, session.progress(), 0.001);
    }
    @Test public void noisyMeasurementsAreRejected() {
        CalibrationSession session = new CalibrationSession();
        for (long t=0; t<=3000; t+=100) session.update(sample(t, ((t/100)%11-5)*0.025, 0.6));
        assertEquals(CalibrationSession.Stage.FAILED, session.stage());
    }
    @Test public void corruptPersistedValuesAreRejected() {
        assertFalse(new Calibration(0, -0.05, Double.NaN, 0.6, 0.24, 0).valid());
        assertFalse(new Calibration(0, -0.05, 0, 0.6, 0, 0).valid());
        assertFalse(new Calibration(0, -0.05, 0, 0.6, 0.24, Double.NaN).valid());
    }
    @Test public void motionModeCalibratesBottomThenTop() {
        CalibrationSession session = new CalibrationSession(InputMode.SWIPE_UP);
        assertEquals(CalibrationSession.Stage.BOTTOM, session.stage());
        feed(session, 0, 0.04, 0.6);
        assertEquals(CalibrationSession.Stage.TOP, session.stage());
        feed(session, 3100, -0.04, 0.6);
        assertEquals(CalibrationSession.Stage.COMPLETE, session.stage());
    }
    @Test public void motionModeStillRejectsIndistinguishableEndpoints() {
        CalibrationSession session = new CalibrationSession(InputMode.SWIPE_UP);
        feed(session, 0, 0, 0.6); feed(session, 3100, 0.003, 0.6);
        assertEquals(CalibrationSession.Stage.FAILED, session.stage());
        assertTrue(session.error().contains("보정 미완료"));
    }
    @Test public void overlappingNoisyGazesHaveADistinctError() {
        CalibrationSession session = new CalibrationSession();
        feed(session, 0, 0, 0.6);
        for (long t=3100; t<=6100; t+=100) session.update(sample(t, -0.018+((t/100)%3-1)*0.005, 0.6));
        assertEquals(CalibrationSession.Stage.FAILED, session.stage());
        assertTrue(session.error().contains("겹쳐요"));
    }
    @Test public void rightCalibrationUsesHorizontalSignalWithAnUnchangedVerticalSignal() {
        CalibrationSession session=new CalibrationSession(InputMode.RIGHT_RETURN);
        for(long t=0;t<=3000;t+=100) session.update(new GazeSample(t,0,0,0.6,0.24,0,true,1,0,0.24,0.24,24));
        assertEquals(CalibrationSession.Stage.RIGHT,session.stage());
        for(long t=3100;t<=6100;t+=100) session.update(new GazeSample(t,0,0,0.6,0.24,0,true,1,-0.08,0.24,0.24,24));
        assertEquals(CalibrationSession.Stage.COMPLETE,session.stage());
        assertEquals(-0.08,session.result().target(),0.001);
    }
    @Test(expected=IllegalArgumentException.class) public void winkDoesNotUseAGazeCalibrationSession() {
        new CalibrationSession(InputMode.WINK);
    }
}
