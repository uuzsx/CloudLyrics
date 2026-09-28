package dev.cloudlyrics;

/** Read-only verification on the host. It intentionally never starts/stops the real player. */
public final class LauncherProbe {
    public static void main(String[] args) throws Exception {
        var environment = new CloudMusicLauncher.WindowsEnvironment();
        if (!environment.supported()) throw new AssertionError("Windows is required for this probe");
        var running = environment.runningExecutables();
        var registered = environment.locate("", java.util.List.of());
        if (running.isEmpty() || registered.isEmpty()) throw new AssertionError("Real player discovery failed");
        if (!running.contains(registered.get())) throw new AssertionError("Registry/running-process paths disagree");
        if (environment.endpoint() != CloudMusicLauncher.Endpoint.READY) throw new AssertionError("Player endpoint not ready");
        if (new CloudMusicLauncher().launch("").outcome() != CloudMusicLauncher.Outcome.READY)
            throw new AssertionError("Existing connection should be reused");
        System.out.println("LIVE LAUNCHER PASS: process discovery + registry discovery + existing endpoint reuse; no player restart");
    }
}
