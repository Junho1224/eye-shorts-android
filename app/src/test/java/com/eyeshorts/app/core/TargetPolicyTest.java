package com.eyeshorts.app.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class TargetPolicyTest {
    private boolean accepts(String pkg, String id, int height) {
        return TargetPolicy.playerContainer(pkg, id, 1080, height, 1080, 2200);
    }
    @Test public void recognizesAFullScreenShortsPlayer() {
        assertTrue(accepts(TargetPolicy.YOUTUBE, "com.google.android.youtube:id/reel_recycler", 2050));
    }
    @Test public void rejectsAHomeFeedShortsShelf() {
        assertFalse(accepts(TargetPolicy.YOUTUBE, "com.google.android.youtube:id/reel_item_thumbnail", 400));
    }
    @Test public void rejectsNormalYouTubeVideoPlayers() {
        assertFalse(accepts(TargetPolicy.YOUTUBE, "com.google.android.youtube:id/player_view", 2050));
    }
    @Test public void smallShortsPreviewsAreInsufficient() {
        assertFalse(accepts(TargetPolicy.YOUTUBE, "com.google.android.youtube:id/reel_video_frame", 500));
    }
    @Test public void anotherAppCannotTriggerEvenWithTheSameResourceName() {
        assertFalse(accepts("com.example.other", "com.google.android.youtube:id/reel_recycler", 2050));
    }
    @Test public void unknownOrMissingIdsFailClosed() {
        assertFalse(accepts(TargetPolicy.YOUTUBE, "com.google.android.youtube:id/new_unknown_player", 2050));
        assertFalse(accepts(TargetPolicy.YOUTUBE, null, 2050));
        assertFalse(accepts(TargetPolicy.YOUTUBE, "com.other:id/reel_recycler", 2050));
    }
    @Test public void recognizesTheKnownFullScreenInstagramReelsContainers() {
        for(String id:new String[]{"clips_viewer_view_pager","root_clips_layout","clips_viewer_container"})
            assertTrue(accepts(TargetPolicy.INSTAGRAM,TargetPolicy.INSTAGRAM+":id/"+id,2050));
    }
    @Test public void instagramHomeFeedDoesNotQualifyAsReels() {
        assertFalse(accepts(TargetPolicy.INSTAGRAM,TargetPolicy.INSTAGRAM+":id/recycler_view",2050));
    }
    @Test public void instagramStoriesDoNotQualifyAsReels() {
        assertFalse(accepts(TargetPolicy.INSTAGRAM,TargetPolicy.INSTAGRAM+":id/reel_viewer_root",2050));
    }
    @Test public void instagramDirectMessagesDoNotQualifyAsReels() {
        assertFalse(accepts(TargetPolicy.INSTAGRAM,TargetPolicy.INSTAGRAM+":id/direct_thread_messages_list",2050));
    }
    @Test public void smallOrNarrowReelsPreviewsAreInsufficient() {
        String id=TargetPolicy.INSTAGRAM+":id/clips_viewer_view_pager";
        assertFalse(accepts(TargetPolicy.INSTAGRAM,id,500));
        assertFalse(TargetPolicy.playerContainer(TargetPolicy.INSTAGRAM,id,300,2050,1080,2200));
    }
    @Test public void theAppsCannotUseEachOthersPlayerNamesOrNamespaces() {
        assertFalse(accepts(TargetPolicy.INSTAGRAM,TargetPolicy.YOUTUBE+":id/reel_recycler",2050));
        assertFalse(accepts(TargetPolicy.INSTAGRAM,TargetPolicy.INSTAGRAM+":id/reel_recycler",2050));
        assertFalse(accepts(TargetPolicy.YOUTUBE,TargetPolicy.YOUTUBE+":id/clips_viewer_view_pager",2050));
    }
    @Test public void onlyTheTwoOfficialAppPackagesAreSupported() {
        assertTrue(TargetPolicy.supportedApp(TargetPolicy.YOUTUBE));
        assertTrue(TargetPolicy.supportedApp(TargetPolicy.INSTAGRAM));
        assertFalse(TargetPolicy.supportedApp("com.instagram.lite"));
        assertFalse(TargetPolicy.supportedApp("com.example.other"));
        assertFalse(TargetPolicy.supportedApp(null));
    }
    @Test public void unknownOrMissingInstagramIdsFailClosed() {
        assertFalse(accepts(TargetPolicy.INSTAGRAM,TargetPolicy.INSTAGRAM+":id/unknown_player",2050));
        assertFalse(accepts(TargetPolicy.INSTAGRAM,null,2050));
    }
    @Test public void invalidWindowDimensionsDoNotQualify() {
        assertFalse(TargetPolicy.playerContainer(TargetPolicy.INSTAGRAM,TargetPolicy.INSTAGRAM+":id/clips_viewer_container",1080,2050,0,2200));
        assertFalse(TargetPolicy.playerContainer(TargetPolicy.YOUTUBE,TargetPolicy.YOUTUBE+":id/reel_recycler",1080,2050,1080,-1));
    }
}
