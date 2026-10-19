package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class RightReturnTriggerTest {
    private static final Calibration CAL=new Calibration(0,-0.08,0,0.6,0.24,0.002);
    private static GazeSample sample(long t,double p) { return new GazeSample(t,0,0,0.6,0.24,0,true,1,-0.08*p,0.24,0.24,24); }
    private static final class Driver {
        final RightReturnTrigger detector=new RightReturnTrigger(CAL); long t; int fires;
        void look(double p,int millis) { look(p,millis,true); }
        void look(double p,int millis,boolean allowed) {
            for(int i=0;i<millis;i+=80) {
                GazeTrigger.Update update=detector.update(sample(t,p),allowed);
                if(update.fire()) { fires++; assertEquals(VideoCommand.NEXT,update.command()); }
                t+=80;
            }
        }
        void prepare() { look(0,640); }
        void bad(GazeSample s) { detector.update(s,true); t+=80; }
    }
    @Test public void rightThenCenterFiresOnce() {
        Driver d=new Driver(); d.prepare(); d.look(1,400); assertEquals(0,d.fires); d.look(0,240); assertEquals(1,d.fires);
    }
    @Test public void startingOnTheRightCannotFire() {
        Driver d=new Driver(); d.look(1,400); d.look(0,1000); assertEquals(0,d.fires);
    }
    @Test public void leftThenCenterIsNotRightThenCenter() {
        Driver d=new Driver(); d.prepare(); d.look(-0.8,400); d.look(0,400); assertEquals(0,d.fires);
    }
    @Test public void aBriefRightGlanceIsRejected() {
        Driver d=new Driver(); d.prepare(); d.look(1,80); d.look(0,400); assertEquals(0,d.fires);
    }
    @Test public void oneCenterFrameIsNotEnoughToConfirm() {
        Driver d=new Driver(); d.prepare(); d.look(1,400); d.look(0,80); d.look(1,800); assertEquals(0,d.fires);
    }
    @Test public void aLongRightStareExpiresWithoutAFire() {
        Driver d=new Driver(); d.prepare(); d.look(1,6000); d.look(0,600); assertEquals(0,d.fires);
    }
    @Test public void verticalMovementDoesNotCountAsARightGlance() {
        Driver d=new Driver(); d.prepare();
        for(int i=0;i<10;i++) { if(d.detector.update(new GazeSample(d.t,-0.1,0,0.6,0.24,0,true,1,0,0.24,0.24,24),true).fire()) d.fires++; d.t+=80; }
        d.look(0,400); assertEquals(0,d.fires);
    }
    @Test public void blinkCancelsTheRightGlance() {
        Driver d=new Driver(); d.prepare(); d.look(1,400); d.bad(new GazeSample(d.t,0,0,0.6,0.24,0,false,1)); d.look(0,600); assertEquals(0,d.fires);
    }
    @Test public void headMovementCannotActAsTheCommand() {
        Driver d=new Driver(); d.prepare(); d.look(1,400); d.bad(new GazeSample(d.t,0,0.2,0.6,0.24,0,true,1)); d.look(0,600); assertEquals(0,d.fires);
    }
    @Test public void leavingTheTargetCancelsTheReturn() {
        Driver d=new Driver(); d.prepare(); d.look(1,400); d.look(0,80,false); d.look(0,600); assertEquals(0,d.fires);
    }
    @Test public void aFrameGapCannotCompleteTheReturn() {
        Driver d=new Driver(); d.prepare(); d.look(1,400); d.t+=1000; d.look(0,600); assertEquals(0,d.fires);
    }
    @Test public void repeatNeedsCooldownAndNewRightGlance() {
        Driver d=new Driver(); d.prepare(); d.look(1,400); d.look(0,240); assertEquals(1,d.fires);
        d.look(1,400); d.look(0,600); assertEquals(1,d.fires);
        d.look(0,2400); d.look(1,400); d.look(0,240); assertEquals(2,d.fires);
    }
}
