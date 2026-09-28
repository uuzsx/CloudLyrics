package dev.cloudlyrics;

/** Tests the shipped Java connection against the running player without printing song lyrics. */
public final class LiveProbe {
    public static void main(String[] args) throws Exception {
        try (var connection = new CloudMusicConnection()) {
            var emitter = new LineEmitter(); int seen = 0; long first = -1, last = -1;
            for(int i = 0; i < 60; i++) {
                Thread.sleep(200);
                var frame = connection.latest();
                if ("ready".equals(frame.status())) {
                    if (first < 0) first = frame.positionMs();
                    last = frame.positionMs();
                    if (emitter.accept(frame,0) != null) seen++;
                }
            }
            if (first < 0 || last <= first) throw new AssertionError("No advancing live lyrics: " + connection.detail());
            connection.setEnabled(false);
            Thread.sleep(400);
            if (!"disabled".equals(connection.latest().status())) throw new AssertionError("Disable failed");
            connection.setEnabled(true);
            for (int i=0; i<25 && !"ready".equals(connection.latest().status()); i++) Thread.sleep(200);
            if (!"ready".equals(connection.latest().status())) throw new AssertionError("Reconnect failed");
            System.out.println("LIVE PASS: position advanced " + (last-first) + " ms; emitted " + seen + " distinct current cues; " + connection.detail());
        }
    }
}
