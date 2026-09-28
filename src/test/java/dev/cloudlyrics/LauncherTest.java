package dev.cloudlyrics;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static dev.cloudlyrics.CloudMusicLauncher.*;

public final class LauncherTest {
    private static int checks;
    private static void equal(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static final class FakeEnvironment implements CloudMusicLauncher.Environment {
        boolean windows = true, failStart;
        Path path = Path.of("C:/Music Player/中文 & Music/cloudmusic.exe");
        List<Path> running = List.of();
        Endpoint endpoint = Endpoint.CLOSED;
        int starts;
        public boolean supported() { return windows; }
        public List<Path> runningExecutables() { return running; }
        public Optional<Path> locate(String configured, List<Path> running) { return Optional.ofNullable(path); }
        public Endpoint endpoint() { return endpoint; }
        public void start(Path executable) throws IOException { starts++; if (failStart) throw new IOException("denied"); }
    }
    public static void main(String[] args) throws Exception {
        var env = new FakeEnvironment();
        var launcher = new CloudMusicLauncher(env);
        equal(Outcome.STARTED, launcher.launch("").outcome());
        equal(1, env.starts);
        equal(List.of(env.path.toString(), "--remote-debugging-address=127.0.0.1", "--remote-debugging-port=9223"), launchArguments(env.path));
        env.starts = 0; env.running = List.of(env.path);
        equal(Outcome.ALREADY_RUNNING, launcher.launch("").outcome());
        equal(0, env.starts);
        env.endpoint = Endpoint.READY;
        equal(Outcome.READY, launcher.launch("").outcome());
        equal(0, env.starts);
        env.running = List.of(); env.endpoint = Endpoint.OCCUPIED;
        equal(Outcome.PORT_IN_USE, launcher.launch("").outcome());
        equal(0, env.starts);
        env.path = null; env.endpoint = Endpoint.CLOSED;
        equal(Outcome.NOT_FOUND, launcher.launch("").outcome());
        env.windows = false;
        equal(Outcome.UNSUPPORTED, launcher.launch("").outcome());
        equal(0, env.starts);
        equal(Optional.of("E:\\音乐 软件\\cloudmusic.exe"), parseRegistryPath("    (Default)    REG_SZ    E:\\音乐 软件\\cloudmusic.exe\r\n"));
        equal(Optional.of("E:\\Music\\cloudmusic.exe"), parseRegistryPath("    (默认)    REG_SZ    \"E:\\Music\\cloudmusic.exe\"--webcmd=\"%1\"\r\n"));
        equal(Optional.of("%ProgramFiles%\\NetEase\\CloudMusic\\cloudmusic.exe"), parseRegistryPath("    (Default)    REG_EXPAND_SZ    %ProgramFiles%\\NetEase\\CloudMusic\\cloudmusic.exe\r\n"));
        equal(Optional.empty(), parseRegistryPath("    (Default)    REG_SZ    C:\\notepad.exe\r\n"));
        equal(Optional.empty(), validatePath("relative/cloudmusic.exe"));
        equal(Optional.empty(), validatePath("C:\\missing-cloudlyrics-test\\cloudmusic.exe"));
        Path directory = Files.createTempDirectory(Path.of("build"), "launcher-test-");
        Path executable = Files.createFile(directory.resolve("cloudmusic.exe")).toRealPath();
        equal(Optional.of(executable), validatePath("\"" + executable + "\""));
        equal(Optional.empty(), validatePath(executable + " --extra-argument"));
        // Remove only the two exact temporary paths created above.
        Files.delete(executable); Files.delete(directory);
        env = new FakeEnvironment(); env.failStart = true;
        try { new CloudMusicLauncher(env).launch(""); throw new AssertionError("Expected launch failure"); }
        catch (IOException expected) { checks++; }
        System.out.println("PASS: " + checks + " launcher discovery/argument/state checks");
    }
}
