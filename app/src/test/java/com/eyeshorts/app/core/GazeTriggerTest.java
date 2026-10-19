package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class GazeTriggerTest {
    private static final Calibration CAL = new Calibration(0, -0.05, 0, 0.6, 0.24, 0.002);
    private static GazeSample sample(long t, double position) {
        return new GazeSample(t, -0.05 * position, 0, 0.6, 0.24, 0, true, 1);
    }
    private static final class Driver {
        final GazeTrigger trigger = new GazeTrigger(CAL, 900);
        long time;
        int fires;
        void look(double position, int millis) { look(position, millis, true); }
        void look(double position, int millis, boolean allowed) {
            for (int elapsed = 0; elapsed < millis; elapsed += 100) {
                GazeTrigger.Update update=trigger.update(sample(time, position), allowed);
                if (update.fire()) { fires++; assertEquals(VideoCommand.NEXT,update.command()); }
                time += 100;
            }
        }
        void invalid(GazeSample sample) { trigger.update(sample, true); time += 100; }
    }

    @Test public void sustainedIntentAfterNeutralFiresOnce() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 1800); assertEquals(1, d.fires);
    }
    @Test public void staringAtTargetOnStartupDoesNotFire() {
        Driver d = new Driver(); d.look(1, 8000); assertEquals(0, d.fires);
    }
    @Test public void holdingTargetCannotScrollContinuously() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 15000); assertEquals(1, d.fires);
    }
    @Test public void returningToCenterAllowsAnotherIntent() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 2000); d.look(0, 3000); d.look(1, 2000); assertEquals(2, d.fires);
    }
    @Test public void aBriefGlanceDoesNotFire() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 600); d.look(0, 2000); assertEquals(0, d.fires);
    }
    @Test public void separateGlancesDoNotAccumulate() {
        Driver d = new Driver(); d.look(0, 800);
        for (int i=0; i<8; i++) { d.look(1, 600); d.look(0, 500); }
        assertEquals(0, d.fires);
    }
    @Test public void eyesClosedDuringDwellDisarmsUntilNeutral() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 700);
        d.invalid(new GazeSample(d.time, -0.05, 0, 0.6, 0.24, 0, false, 1));
        d.look(1, 3000); assertEquals(0, d.fires);
    }
    @Test public void losingFaceCannotCompleteDwell() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 700);
        d.invalid(new GazeSample(d.time, -0.05, 0, 0.6, 0.24, 0, true, 0));
        d.look(1, 3000); assertEquals(0, d.fires);
    }
    @Test public void twoPeopleInFrameDisarmTracking() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 700);
        d.invalid(new GazeSample(d.time, -0.05, 0, 0.6, 0.24, 0, true, 2));
        d.look(1, 3000); assertEquals(0, d.fires);
    }
    @Test public void leavingShortsAndReturningRequiresNeutral() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 700); d.look(1, 300, false);
        d.look(1, 3000); assertEquals(0, d.fires);
    }
    @Test public void noActionsInOtherAppsEvenAfterLongDwell() {
        Driver d = new Driver(); d.look(0, 800, false); d.look(1, 10000, false); assertEquals(0, d.fires);
    }
    @Test public void staleCameraGapCannotCompleteAnOldDwell() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 700); d.time += 1000;
        d.look(1, 3000); assertEquals(0, d.fires);
    }
    @Test public void movingTheHeadInvalidatesDwell() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 700);
        d.invalid(new GazeSample(d.time, -0.05, 0, 0.9, 0.24, 0, true, 1));
        d.look(1, 3000); assertEquals(0, d.fires);
    }
    @Test public void changingViewingDistanceInvalidatesDwell() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 700);
        d.invalid(new GazeSample(d.time, -0.05, 0, 0.6, 0.4, 0, true, 1));
        d.look(1, 3000); assertEquals(0, d.fires);
    }
    @Test public void invalidNumbersCannotTrigger() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 700);
        d.invalid(new GazeSample(d.time, Double.NaN, 0, 0.6, 0.24, 0, true, 1));
        d.look(1, 3000); assertEquals(0, d.fires);
    }
    @Test public void duplicateFrameDisarmsAnIncompleteIntent() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 700);
        d.trigger.update(sample(d.time-100, 1), true);
        d.look(1, 3000); assertEquals(0, d.fires);
    }
    @Test public void secondIntentRequiresCooldownAndNeutral() {
        Driver d = new Driver(); d.look(0, 800); d.look(1, 1700);
        assertEquals(1, d.fires);
        d.look(0, 500); d.look(1, 2200); assertEquals(1, d.fires);
        d.look(0, 1800); d.look(1, 1800); assertEquals(2, d.fires);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsAnUncalibratedDetector() {
        new GazeTrigger(new Calibration(0, 0, 0, 0.6, 0.24, 0), 900);
    }
}
