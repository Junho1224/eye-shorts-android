package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoCommandTest {
    @Test public void aPhysicalRightWinkProducesAnUpwardSwipe() {
        GazeSample rightClosed=new GazeSample(0,0,0,0.6,0.24,0,false,1,0,0.24,0.12,24);
        VideoCommand command=new WinkTrigger().update(rightClosed,true).command();
        assertTrue(command.startY()>command.endY());
    }
    @Test public void aPhysicalLeftWinkProducesADownwardSwipe() {
        GazeSample leftClosed=new GazeSample(0,0,0,0.6,0.24,0,false,1,0,0.12,0.24,24);
        VideoCommand command=new WinkTrigger().update(leftClosed,true).command();
        assertTrue(command.startY()<command.endY());
    }
    @Test public void allGesturePathsStayInsideThePlayer() {
        for(VideoCommand command:VideoCommand.values()) {
            assertTrue(command.startY()>0 && command.startY()<1);
            assertTrue(command.endY()>0 && command.endY()<1);
        }
        assertEquals(VideoCommand.NEXT.startY(),VideoCommand.PREVIOUS.endY(),0);
        assertEquals(VideoCommand.NEXT.endY(),VideoCommand.PREVIOUS.startY(),0);
    }
    @Test public void playbackIsASingleShortCenterTapWhileNavigationKeepsItsSwipe() {
        assertTrue(VideoCommand.PLAY_PAUSE.isTap());
        assertEquals(0.5,VideoCommand.PLAY_PAUSE.startY(),0);
        assertEquals(VideoCommand.PLAY_PAUSE.startY(),VideoCommand.PLAY_PAUSE.endY(),0);
        assertEquals(80,VideoCommand.PLAY_PAUSE.durationMs());
        assertFalse(VideoCommand.NEXT.isTap()); assertFalse(VideoCommand.PREVIOUS.isTap());
        assertEquals(300,VideoCommand.NEXT.durationMs()); assertEquals(300,VideoCommand.PREVIOUS.durationMs());
    }
}
