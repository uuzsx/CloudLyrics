package dev.cloudlyrics;

import java.util.List;

public record LyricFrame(String songId, String playbackId, String title, String artist,
                         boolean playing, long positionMs, String status, List<Cue> lyrics) {
    public record Cue(long timeMs, String text) {}
    public static LyricFrame empty(String status) {
        return new LyricFrame("", "", "", "", false, 0, status, List.of());
    }
}
