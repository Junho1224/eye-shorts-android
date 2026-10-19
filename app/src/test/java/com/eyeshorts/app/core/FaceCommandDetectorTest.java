package com.eyeshorts.app.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class FaceCommandDetectorTest {
    private static GazeSample sample(long t,double left,double right,double mouth) {
        return new GazeSample(t,0,0,0.6,0.24,0,left>0.13&&right>0.13,1,0,left,right,24,mouth);
    }
    private static final class Driver {
        final FaceCommandDetector detector;
        final List<VideoCommand> commands=new ArrayList<>(); long t;
        Driver(boolean enabled) { detector=new FaceCommandDetector(new WinkTrigger(),enabled); }
        Driver(CommandDetector primary) { detector=new FaceCommandDetector(primary,false); }
        void look(double left,double right,double mouth,int millis,boolean allowed) {
            for(int i=0;i<millis;i+=80) {
                GazeTrigger.Update update=detector.update(sample(t,left,right,mouth),allowed);
                if(update.fire()) commands.add(update.command());
                t+=80;
            }
        }
        void look(double left,double right,double mouth,int millis) { look(left,right,mouth,millis,true); }
        void mouth(int millis) { look(0.24,0.24,0.4,millis); }
        void neutral(int millis) { look(0.24,0.24,0.05,millis); }
    }
    @Test public void anEnabledMouthDoesNotDelayAnOrdinaryWink() {
        Driver d=new Driver(true); d.look(0.24,0.12,0.05,80);
        assertEquals(List.of(VideoCommand.NEXT),d.commands);
    }
    @Test public void missingMouthDataDoesNotBreakWinks() {
        Driver d=new Driver(true); d.look(0.12,0.24,Double.NaN,80);
        assertEquals(List.of(VideoCommand.PREVIOUS),d.commands);
    }
    @Test public void disablingMouthLeavesOnlyTheEyeCommands() {
        Driver d=new Driver(false); d.mouth(800); assertTrue(d.commands.isEmpty());
        d.look(0.24,0.12,0.4,80); assertEquals(List.of(VideoCommand.NEXT),d.commands);
    }
    @Test public void anOpeningProducesAPlaybackCommand() {
        Driver d=new Driver(true); d.mouth(400);
        assertEquals(List.of(VideoCommand.PLAY_PAUSE),d.commands);
    }
    @Test public void anOpeningAndWinkTogetherProduceOnlyTheMouthCommand() {
        Driver d=new Driver(true); d.look(0.24,0.12,0.4,4000);
        assertEquals(List.of(VideoCommand.PLAY_PAUSE),d.commands);
    }
    @Test public void anEyeHeldClosedAfterMouthUseCannotBecomeADelayedSwipe() {
        Driver d=new Driver(true); d.look(0.24,0.12,0.4,400);
        d.look(0.24,0.12,0.05,4000); assertEquals(List.of(VideoCommand.PLAY_PAUSE),d.commands);
        d.neutral(80); d.look(0.24,0.12,0.05,80);
        assertEquals(List.of(VideoCommand.PLAY_PAUSE,VideoCommand.NEXT),d.commands);
    }
    @Test public void aMouthDuringTheWinkCooldownIsConsumedWithoutDelayedPlayback() {
        Driver d=new Driver(true); d.look(0.24,0.12,0.05,80); d.neutral(80);
        d.mouth(3200); assertEquals(List.of(VideoCommand.NEXT),d.commands);
        d.neutral(80); d.mouth(400);
        assertEquals(List.of(VideoCommand.NEXT,VideoCommand.PLAY_PAUSE),d.commands);
    }
    @Test public void aWinkDuringTheMouthCooldownDoesNotBecomeADelayedSwipe() {
        Driver d=new Driver(true); d.mouth(400); d.neutral(80);
        d.look(0.24,0.12,0.05,3200); assertEquals(List.of(VideoCommand.PLAY_PAUSE),d.commands);
        d.neutral(80); d.look(0.24,0.12,0.05,80);
        assertEquals(List.of(VideoCommand.PLAY_PAUSE,VideoCommand.NEXT),d.commands);
    }
    @Test public void neitherCommandIsSentOutsideTheTarget() {
        Driver d=new Driver(true); d.look(0.24,0.12,0.4,4000,false);
        assertTrue(d.commands.isEmpty()); d.mouth(400);
        assertEquals(List.of(VideoCommand.PLAY_PAUSE),d.commands);
    }
    @Test public void mouthOnlyPracticeIgnoresWinksAndUsesTheSameCooldown() {
        Driver d=new Driver(new MouthTrigger()); d.look(0.24,0.12,0.05,80);
        assertTrue(d.commands.isEmpty()); d.mouth(400); d.neutral(80); d.mouth(3200);
        assertEquals(List.of(VideoCommand.PLAY_PAUSE),d.commands);
        d.neutral(80); d.mouth(400); assertEquals(2,d.commands.size());
    }
    @Test public void resettingTheRouterDoesNotBypassTheSharedCooldown() {
        Driver d=new Driver(true); d.look(0.24,0.12,0.05,80); d.detector.reset();
        d.mouth(400); assertEquals(List.of(VideoCommand.NEXT),d.commands);
    }
}
