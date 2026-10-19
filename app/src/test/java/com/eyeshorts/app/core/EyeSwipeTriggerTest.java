package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class EyeSwipeTriggerTest {
    private static final Calibration CAL = new Calibration(0.04, -0.04, 0, 0.6, 0.24, 0.002);
    private static GazeSample sample(long time, double position) {
        return new GazeSample(time, 0.04 - position * 0.08, 0, 0.6, 0.24, 0, true, 1);
    }
    private static final class Driver {
        final EyeSwipeTrigger trigger = new EyeSwipeTrigger(CAL);
        long time;
        int fires;
        void look(double position, int millis) { look(position, millis, true); }
        void look(double position, int millis, boolean allowed) {
            for (int i=0; i<millis; i+=80) {
                GazeTrigger.Update update=trigger.update(sample(time, position), allowed);
                if (update.fire()) { fires++; assertEquals(VideoCommand.NEXT,update.command()); }
                time += 80;
            }
        }
        void invalid(GazeSample invalid) { trigger.update(invalid, true); time += 80; }
        void prepare() { look(0.5, 480); look(0, 400); }
    }
    @Test public void deliberateBottomToTopMovementFires() {
        Driver d = new Driver(); d.prepare(); d.look(1, 240); assertEquals(1, d.fires);
    }
    @Test public void noFireFromStaringAtTop() {
        Driver d = new Driver(); d.look(1, 5000); assertEquals(0, d.fires);
    }
    @Test public void startupRequiresNeutralBeforeAGesture() {
        Driver d = new Driver(); d.look(0, 1000); d.look(1, 1000); assertEquals(0, d.fires);
    }
    @Test public void lookingFromTopToBottomDoesNotFire() {
        Driver d = new Driver(); d.look(0.5, 480); d.look(1, 400); d.look(0, 400); assertEquals(0, d.fires);
    }
    @Test public void anExtremelyBriefBottomGlanceIsNotArmed() {
        Driver d = new Driver(); d.look(0.5, 480); d.look(0, 80); d.look(1, 800); assertEquals(0, d.fires);
    }
    @Test public void oneNoisyTopFrameIsNotEnough() {
        Driver d = new Driver(); d.prepare(); d.look(1, 80); d.look(0, 800); assertEquals(0, d.fires);
    }
    @Test public void slowDriftDoesNotFire() {
        Driver d = new Driver(); d.prepare();
        for (double p=0.1; p<1.05; p+=0.1) d.look(p, 320);
        assertEquals(0, d.fires);
    }
    @Test public void topHoldAfterASwipeDoesNotRepeat() {
        Driver d = new Driver(); d.prepare(); d.look(1, 10000); assertEquals(1, d.fires);
    }
    @Test public void cooldownAndNeutralPermitASecondSwipe() {
        Driver d = new Driver(); d.prepare(); d.look(1, 240);
        d.look(0, 1200); d.look(1, 240); assertEquals(1, d.fires);
        d.look(0.5, 2800); d.look(0, 400); d.look(1, 240); assertEquals(2, d.fires);
    }
    @Test public void aBlinkDuringTheMovementCancelsIt() {
        Driver d = new Driver(); d.prepare();
        d.invalid(new GazeSample(d.time, 0, 0, 0.6, 0.24, 0, false, 1));
        d.look(1, 800); assertEquals(0, d.fires);
    }
    @Test public void losingTheFaceCancelsTheMovement() {
        Driver d = new Driver(); d.prepare();
        d.invalid(new GazeSample(d.time, 0, 0, 0.6, 0.24, 0, true, 0));
        d.look(1, 800); assertEquals(0, d.fires);
    }
    @Test public void twoFacesCancelTheMovement() {
        Driver d = new Driver(); d.prepare();
        d.invalid(new GazeSample(d.time, 0, 0, 0.6, 0.24, 0, true, 2));
        d.look(1, 800); assertEquals(0, d.fires);
    }
    @Test public void headMovementCannotSubstituteForEyeMovement() {
        Driver d = new Driver(); d.prepare();
        d.invalid(new GazeSample(d.time, -0.04, 0, 0.9, 0.24, 0, true, 1));
        d.look(1, 800); assertEquals(0, d.fires);
    }
    @Test public void leavingShortsCancelsAPendingGesture() {
        Driver d = new Driver(); d.prepare(); d.look(0.5, 80, false); d.look(1, 800); assertEquals(0, d.fires);
    }
    @Test public void cameraGapCannotBridgeAGesture() {
        Driver d = new Driver(); d.prepare(); d.time += 1000; d.look(1, 800); assertEquals(0, d.fires);
    }
    @Test public void duplicateFrameCancelsTheGesture() {
        Driver d = new Driver(); d.prepare(); d.trigger.update(sample(d.time - 80, 1), true);
        d.look(1, 800); assertEquals(0, d.fires);
    }
    @Test public void unknownOrOtherAppNeverReceivesCommands() {
        Driver d = new Driver(); d.look(0.5, 480, false); d.look(0, 400, false); d.look(1, 400, false); assertEquals(0, d.fires);
    }
}
