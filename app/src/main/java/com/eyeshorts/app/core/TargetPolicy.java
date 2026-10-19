package com.eyeshorts.app.core;

import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

public final class TargetPolicy {
    public static final String YOUTUBE = "com.google.android.youtube";
    public static final String INSTAGRAM = "com.instagram.android";
    private static final Set<String> YOUTUBE_PLAYER_IDS = new HashSet<>(Arrays.asList(
            "reel_recycler", "reel_player_page_container", "reel_watch_fragment_root",
            "reel_watch_player", "reel_video_frame", "shorts_player"));
    private static final Set<String> INSTAGRAM_PLAYER_IDS = new HashSet<>(Arrays.asList(
            "clips_viewer_view_pager", "root_clips_layout", "clips_viewer_container"));
    private TargetPolicy() {}

    public static boolean supportedApp(String packageName) {
        return YOUTUBE.equals(packageName) || INSTAGRAM.equals(packageName);
    }

    public static boolean playerContainer(String packageName, String resourceId,
                                          int width, int height, int rootWidth, int rootHeight) {
        if (!supportedApp(packageName) || resourceId == null
                || !resourceId.startsWith(packageName + ":id/") || rootWidth <= 0 || rootHeight <= 0) return false;
        String id = resourceId.substring(resourceId.lastIndexOf('/') + 1);
        Set<String> ids = YOUTUBE.equals(packageName) ? YOUTUBE_PLAYER_IDS : INSTAGRAM_PLAYER_IDS;
        return ids.contains(id) && width >= rootWidth * 0.70 && height >= rootHeight * 0.65;
    }
}
