package dev.cloudlyrics;

import java.util.LinkedHashSet;

/** Chooses only the current cue; never dumps missed lines after a seek or reconnect. */
public final class LineEmitter {
    private String playback = "";
    private long lastPosition = -1;
    private String lastCue = "";

    public void reset() { playback = ""; lastPosition = -1; lastCue = ""; }

    public String accept(LyricFrame frame, long offsetMs) {
        if (!playback.equals(frame.playbackId()) ||
            (lastPosition >= 0 && frame.positionMs() < lastPosition - 750)) {
            playback = frame.playbackId();
            lastCue = "";
        }
        lastPosition = frame.positionMs();
        if (!frame.playing() || !"ready".equals(frame.status())) return null;
        long position = frame.positionMs() + offsetMs;
        long timestamp = Long.MIN_VALUE;
        for (var cue : frame.lyrics()) {
            if (cue.timeMs() <= position && cue.timeMs() > timestamp) timestamp = cue.timeMs();
        }
        if (timestamp == Long.MIN_VALUE) { lastCue = ""; return null; }
        var parts = new LinkedHashSet<String>();
        for (var cue : frame.lyrics()) {
            if (cue.timeMs() == timestamp && !cue.text().isBlank()) parts.add(sanitize(cue.text()));
        }
        String text = String.join(" / ", parts);
        String key = timestamp + ":" + text;
        if (key.equals(lastCue)) return null;
        lastCue = key;
        return text.isBlank() ? null : text;
    }

    static String sanitize(String text) {
        // Literal chat text only: strip formatting codes and invisible/control characters.
        String result = text.replaceAll("§.", "").replaceAll("[\\p{Cc}\\p{Cf}]", " ").strip();
        return result.substring(0, Math.min(result.length(), 500));
    }
}
