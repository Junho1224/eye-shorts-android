package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class MouthTriggerTest {
    private static GazeSample sample(long t,double mouth) {
        return new GazeSample(t,0,0,0.6,0.24,0,true,1,0,0.24,0.24,24,mouth);
    }
    private static final class Driver {
        final MouthTrigger detector=new MouthTrigger(); long t; int fires;
        void send(GazeSample value,boolean allowed) {
            GazeTrigger.Update update=detector.update(value,allowed);
            if (update.fire()) { fires++; assertEquals(VideoCommand.PLAY_PAUSE,update.command()); }
        }
        void look(double mouth,int millis) {
            for(int i=0;i<millis;i+=80) { send(sample(t,mouth),true); t+=80; }
        }
    }
    @Test public void aDeliberateOpeningFiresBeforeClosing() {
        Driver d=new Driver(); d.look(0.4,400); assertEquals(1,d.fires);
    }
    @Test public void oneBriefOpeningDoesNotFire() {
        Driver d=new Driver(); d.look(0.4,80); d.look(0.05,800); assertEquals(0,d.fires);
    }
    @Test public void aLongHeldMouthFiresOnlyOnce() {
        Driver d=new Driver(); d.look(0.4,6000); assertEquals(1,d.fires);
    }
    @Test public void closingAndOpeningAgainCanFireAgain() {
        Driver d=new Driver(); d.look(0.4,400); d.look(0.05,80); d.look(0.4,400); assertEquals(2,d.fires);
    }
    @Test public void separateShortOpeningsDoNotAccumulate() {
        Driver d=new Driver();
        for(int i=0;i<5;i++) { d.look(0.4,160); d.look(0.05,160); }
        assertEquals(0,d.fires);
    }
    @Test public void partialClosingDoesNotRearm() {
        Driver d=new Driver(); d.look(0.4,400); d.look(0.2,2400); d.look(0.4,400); assertEquals(1,d.fires);
    }
    @Test public void anIntermediateOpeningInterruptsTheHold() {
        Driver d=new Driver(); d.look(0.4,160); d.look(0.2,80); d.look(0.4,160); assertEquals(0,d.fires);
    }
    @Test public void aCameraGapCannotCompleteTheOldHold() {
        Driver d=new Driver(); d.look(0.4,160); d.t+=1000; d.look(0.4,240); assertEquals(0,d.fires);
        d.look(0.4,160); assertEquals(1,d.fires);
    }
    @Test public void duplicateOrOlderFramesCannotCompleteTheHold() {
        Driver d=new Driver(); d.look(0.4,240);
        d.send(sample(160,0.4),true); d.send(sample(80,0.4),true); assertEquals(0,d.fires);
        d.look(0.4,80); assertEquals(0,d.fires); d.look(0.4,80); assertEquals(1,d.fires);
    }
    @Test public void noCommandsAreSentOutsideTheTarget() {
        Driver d=new Driver();
        for(int i=0;i<8;i++) { d.send(sample(d.t,0.4),false); d.t+=80; }
        assertEquals(0,d.fires); d.look(0.4,400); assertEquals(1,d.fires);
    }
    @Test public void targetLossCannotRearmAHeldMouth() {
        Driver d=new Driver(); d.look(0.4,400); d.send(sample(2400,0.05),false);
        d.t=2480; d.look(0.4,400); assertEquals(1,d.fires);
    }
    @Test public void faceLossCannotRearmAHeldMouth() {
        Driver d=new Driver(); d.look(0.4,400);
        d.send(new GazeSample(2400,0,0,0,0,0,false,0),true);
        d.t=2480; d.look(0.4,400); assertEquals(1,d.fires);
    }
    @Test public void missingNegativeOrInfiniteMeasurementsDoNotFire() {
        Driver d=new Driver(); d.look(Double.NaN,800); d.look(-0.2,800); d.look(Double.POSITIVE_INFINITY,800);
        assertEquals(0,d.fires); assertFalse(d.detector.engaged());
    }
    @Test public void unusableAndMultipleFacesDoNotFire() {
        Driver d=new Driver();
        for(int i=0;i<6;i++) {
            d.send(new GazeSample(d.t,0,0.3,0.6,0.24,0,true,1,0,0.24,0.24,24,0.4),true); d.t+=80;
            d.send(new GazeSample(d.t,0,0,0.6,0.24,0,true,2,0,0.24,0.24,24,0.4),true); d.t+=80;
        }
        assertEquals(0,d.fires);
    }
    @Test public void theOpenAndCloseThresholdsIncludeTheirBoundaries() {
        Driver d=new Driver(); d.look(0.28,400); assertEquals(1,d.fires);
        d.look(0.1201,800); d.look(0.28,400); assertEquals(1,d.fires);
        d.look(0.12,80); d.look(0.28,400); assertEquals(2,d.fires);
    }
    @Test public void anEngagedMouthReleasesOnlyWhenClosed() {
        Driver d=new Driver(); d.look(0.4,80); assertTrue(d.detector.engaged());
        d.look(0.2,80); assertTrue(d.detector.engaged());
        d.look(0.05,80); assertFalse(d.detector.engaged());
    }
}
