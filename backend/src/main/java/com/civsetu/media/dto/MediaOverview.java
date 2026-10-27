package com.civsetu.media.dto;

/**
 * What a list row needs to know about an issue's report files: the first public photo to show as
 * a cover (null if none), how many photos there are, and whether a voice note was attached.
 */
public record MediaOverview(String coverImageUrl, int imageCount, boolean hasVoice) {

    public static final MediaOverview NONE = new MediaOverview(null, 0, false);
}
