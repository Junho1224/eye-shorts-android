package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class WinkTriggerTest {
    private static GazeSample sample(long t, double left, double right) {
        return new GazeSample(t,0,0,0.6,0.24,0,left>0.13 && right>0.13,1,0,left,right,24);
    }
    private static final class Driver {
        final WinkTrigger detector = new WinkTrigger();
        long t; int fires;
        VideoCommand lastCommand;
        void send(GazeSample value, boolean allowed) {
            GazeTrigger.Update update=detector.update(value,allowed);
            if (update.fire()) { fires++; lastCommand=update.command(); }
        }
        void look(double left, double right, int millis) {
            for (int i=0;i<millis;i+=80) { send(sample(t,left,right),true); t+=80; }
        }
        void open(int millis) { look(0.24,0.24,millis); }
    }

    @Test public void theFirstLeftClosureFiresBeforeAnyReopeningOrWarmup() {
        Driver d=new Driver(); d.look(0.12,0.24,80); assertEquals(1,d.fires);
        assertEquals(VideoCommand.PREVIOUS,d.lastCommand);
    }
    @Test public void theFirstRightClosureAlsoFiresImmediately() {
        Driver d=new Driver(); d.look(0.24,0.12,80); assertEquals(1,d.fires);
        assertEquals(VideoCommand.NEXT,d.lastCommand);
    }
    @Test public void theExactDisplayedClosedThresholdFires() {
        GazeSample value=sample(0,0.13,0.24);
        assertEquals(FaceQuality.ONE_CLOSED,FaceQuality.from(value));
        GazeTrigger.Update update=new WinkTrigger().update(value,true);
        assertTrue(update.fire()); assertEquals(VideoCommand.PREVIOUS,update.command());
    }
    @Test public void aShallowClosureDoesNotNeedAnAdditionalDifferenceBetweenEyes() {
        Driver d=new Driver(); d.look(0.14,0.14,800);
        d.look(0.12,0.14,80); assertEquals(1,d.fires);
    }
    @Test public void twoOpenEyesDoNotFire() {
        Driver d=new Driver(); d.look(0.1301,0.14,3200); assertEquals(0,d.fires);
    }
    @Test public void closingBothEyesDoesNotFire() {
        Driver d=new Driver(); d.look(0.03,0.03,3200); d.open(800); assertEquals(0,d.fires);
    }
    @Test public void aLongHeldClosureFiresOnlyOnceEvenAfterCooldown() {
        Driver d=new Driver(); d.look(0.03,0.24,6400); assertEquals(1,d.fires);
    }
    @Test public void reopeningAllowsAnotherClosureAfterCooldown() {
        Driver d=new Driver(); d.look(0.12,0.24,80); d.open(1920);
        d.look(0.24,0.12,80); assertEquals(2,d.fires);
        assertEquals(VideoCommand.NEXT,d.lastCommand);
    }
    @Test public void switchingClosedSidesWithoutOpeningBothDoesNotRepeat() {
        Driver d=new Driver(); d.look(0.12,0.24,2400);
        d.look(0.24,0.12,2400); assertEquals(1,d.fires);
        assertEquals(VideoCommand.PREVIOUS,d.lastCommand);
    }
    @Test public void closingBothEyesDoesNotRearmAHeldClosure() {
        Driver d=new Driver(); d.look(0.12,0.24,80); d.look(0.03,0.03,2400);
        d.look(0.12,0.24,80); assertEquals(1,d.fires);
    }
    @Test public void aFastSecondClosureIsNotSentLaterWhileItIsStillHeld() {
        Driver d=new Driver(); d.look(0.12,0.24,80); d.open(80);
        d.look(0.12,0.24,2400); assertEquals(1,d.fires);
        d.open(80); d.look(0.12,0.24,80); assertEquals(2,d.fires);
    }
    @Test public void noCommandsAreSentOutsideTheTarget() {
        Driver d=new Driver(); d.send(sample(0,0.12,0.24),false); assertEquals(0,d.fires);
        d.send(sample(80,0.12,0.24),true); assertEquals(1,d.fires);
    }
    @Test public void leavingAndReturningToTheTargetDoesNotRepeatAHeldClosure() {
        Driver d=new Driver(); d.look(0.12,0.24,80);
        d.send(sample(2400,0.24,0.24),false);
        d.send(sample(2480,0.12,0.24),true); assertEquals(1,d.fires);
    }
    @Test public void aLostOrMultipleFaceCannotFire() {
        Driver d=new Driver();
        d.send(new GazeSample(0,0,0,0.6,0.24,0,false,0,0,0.12,0.24,24),true);
        d.send(new GazeSample(80,0,0,0.6,0.24,0,false,2,0,0.12,0.24,24),true);
        assertEquals(0,d.fires);
    }
    @Test public void anUnusableFaceCannotFireEvenWithUnequalEyelids() {
        Driver d=new Driver();
        d.send(new GazeSample(0,0,0.3,0.6,0.24,0,false,1,0,0.12,0.24,24),true);
        d.send(new GazeSample(80,0,0,0.6,0.24,0,false,1,0,0.12,0.24,10),true);
        assertEquals(0,d.fires);
    }
    @Test public void faceLossDoesNotRearmAHeldClosure() {
        Driver d=new Driver(); d.look(0.12,0.24,80);
        d.send(new GazeSample(2400,0,0,0,0,0,false,0),true);
        d.send(sample(2480,0.12,0.24),true); assertEquals(1,d.fires);
    }
    @Test public void aCameraGapDoesNotRepeatAHeldClosure() {
        Driver d=new Driver(); d.look(0.12,0.24,80); d.t+=3000;
        d.look(0.12,0.24,80); assertEquals(1,d.fires);
    }
    @Test public void duplicateAndOlderOpenFramesCannotRearmTheDetector() {
        Driver d=new Driver(); d.send(sample(100,0.12,0.24),true);
        d.send(sample(100,0.24,0.24),true); d.send(sample(0,0.24,0.24),true);
        d.send(sample(2400,0.12,0.24),true); assertEquals(1,d.fires);
    }
    @Test public void resetDoesNotBypassTheSwipeCooldown() {
        Driver d=new Driver(); d.look(0.12,0.24,80); d.detector.reset();
        d.look(0.12,0.24,80); assertEquals(1,d.fires);
    }
    @Test public void theRightEyeAtTheDisplayedThresholdMovesForward() {
        GazeTrigger.Update update=new WinkTrigger().update(sample(0,0.24,0.13),true);
        assertTrue(update.fire()); assertEquals(VideoCommand.NEXT,update.command());
    }
    @Test public void alternatingEyesProducesNextPreviousNextAfterReopening() {
        Driver d=new Driver(); d.look(0.24,0.12,80); assertEquals(VideoCommand.NEXT,d.lastCommand);
        d.open(1920); d.look(0.12,0.24,80); assertEquals(VideoCommand.PREVIOUS,d.lastCommand);
        d.open(1920); d.look(0.24,0.12,80); assertEquals(VideoCommand.NEXT,d.lastCommand);
        assertEquals(3,d.fires);
    }
    @Test public void nonWinkMeasurementsHaveNoEyeDirection() {
        assertNull(WinkTrigger.directionFor(sample(0,0.24,0.24)));
        assertNull(WinkTrigger.directionFor(sample(0,0.03,0.03)));
        assertNull(WinkTrigger.directionFor(new GazeSample(0,0,0,0,0,0,false,0)));
    }
}
