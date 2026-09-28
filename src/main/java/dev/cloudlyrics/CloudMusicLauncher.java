package dev.cloudlyrics;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Launches the installed player directly, without extracting or invoking shell scripts. */
public final class CloudMusicLauncher {
    public enum Endpoint { READY, CLOSED, OCCUPIED }
    public enum Outcome { READY, STARTED, ALREADY_RUNNING, NOT_FOUND, PORT_IN_USE, UNSUPPORTED }
    public record Result(Outcome outcome, Path executable) {}
    interface Environment {
        boolean supported();
        List<Path> runningExecutables();
        Optional<Path> locate(String configuredPath, List<Path> running);
        Endpoint endpoint();
        void start(Path executable) throws IOException;
    }
    private final Environment environment;
    public CloudMusicLauncher() { this(new WindowsEnvironment()); }
    CloudMusicLauncher(Environment environment) { this.environment = environment; }

    public Result launch(String configuredPath) throws IOException {
        if (!environment.supported()) return new Result(Outcome.UNSUPPORTED, null);
        List<Path> running = environment.runningExecutables();
        Path path = environment.locate(configuredPath, running).orElse(null);
        Endpoint endpoint = environment.endpoint();
        if (endpoint == Endpoint.READY) return new Result(Outcome.READY, path);
        if (!running.isEmpty()) return new Result(Outcome.ALREADY_RUNNING, path);
        if (endpoint == Endpoint.OCCUPIED) return new Result(Outcome.PORT_IN_USE, path);
        if (path == null) return new Result(Outcome.NOT_FOUND, null);
        environment.start(path);
        return new Result(Outcome.STARTED, path);
    }

    public static Optional<Path> validatePath(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String text = input.strip();
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 1)
            text = text.substring(1, text.length() - 1);
        try {
            Path path = Path.of(text);
            if (!path.isAbsolute() || path.getFileName() == null ||
                !path.getFileName().toString().equalsIgnoreCase("cloudmusic.exe") || !Files.isRegularFile(path))
                return Optional.empty();
            return Optional.of(path.toRealPath());
        } catch (Exception e) { return Optional.empty(); }
    }

    static List<String> launchArguments(Path executable) {
        return List.of(executable.toString(), "--remote-debugging-address=127.0.0.1", "--remote-debugging-port=9223");
    }

    static Optional<String> parseRegistryPath(String output) {
        var row = Pattern.compile("(?m)^\\s+[^\\r\\n]*?\\s+REG_(?:EXPAND_)?SZ\\s+([^\\r\\n]+)$").matcher(output);
        while (row.find()) {
            String value = row.group(1).strip();
            var executable = Pattern.compile("(?i)^(?:\"([^\"]*cloudmusic\\.exe)\"|(.+?cloudmusic\\.exe)(?:\\s|$))").matcher(value);
            if (executable.find()) return Optional.of(executable.group(1) != null ? executable.group(1) : executable.group(2));
        }
        return Optional.empty();
    }

    static final class WindowsEnvironment implements Environment {
        public boolean supported() { return System.getProperty("os.name", "").startsWith("Windows"); }
        public List<Path> runningExecutables() {
            try (var processes = ProcessHandle.allProcesses()) {
                return processes.flatMap(p -> p.info().command().stream())
                    .map(CloudMusicLauncher::validatePath).flatMap(Optional::stream).distinct().toList();
            }
        }
        public Optional<Path> locate(String configuredPath, List<Path> running) {
            var custom = validatePath(configuredPath);
            if (custom.isPresent()) return custom;
            if (!running.isEmpty()) return Optional.of(running.getFirst());
            // App Paths supports non-default drives and paths containing spaces or Chinese characters.
            for (String key : List.of(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\cloudmusic.exe",
                "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\cloudmusic.exe",
                "HKLM\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\App Paths\\cloudmusic.exe",
                "HKCU\\Software\\Classes\\orpheus\\shell\\open\\command",
                "HKLM\\Software\\Classes\\orpheus\\shell\\open\\command")) {
                var path = readRegistry(key).flatMap(CloudMusicLauncher::validatePath);
                if (path.isPresent()) return path;
            }
            for (String root : List.of("ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA")) {
                String value = System.getenv(root);
                if (value == null) continue;
                for (String sub : List.of("NetEase/CloudMusic/cloudmusic.exe", "CloudMusic/cloudmusic.exe")) {
                    var path = validatePath(Path.of(value, sub).toString());
                    if (path.isPresent()) return path;
                }
            }
            return Optional.empty();
        }
        private Optional<String> readRegistry(String key) {
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null) return Optional.empty();
            Process process = null;
            try {
                process = new ProcessBuilder(Path.of(systemRoot, "System32", "reg.exe").toString(), "query", key, "/ve")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if (!process.waitFor(2, TimeUnit.SECONDS)) { process.destroyForcibly(); return Optional.empty(); }
                byte[] bytes;
                try (var stream = process.getInputStream()) { bytes = stream.readNBytes(16384); }
                // reg.exe uses the Windows native code page for redirected text.
                String output = new String(bytes, Charset.forName(System.getProperty("native.encoding", "GB18030")));
                return parseRegistryPath(output).map(WindowsEnvironment::expandEnvironment);
            } catch (Exception e) {
                if (process != null && process.isAlive()) process.destroyForcibly();
                return Optional.empty();
            }
        }
        private static String expandEnvironment(String value) {
            var matcher = Pattern.compile("%([^%]+)%").matcher(value);
            return matcher.replaceAll(match -> {
                String replacement = System.getenv(match.group(1));
                return java.util.regex.Matcher.quoteReplacement(replacement == null ? match.group() : replacement);
            });
        }
        public Endpoint endpoint() {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) URI.create("http://127.0.0.1:9223/json/list").toURL().openConnection(Proxy.NO_PROXY);
                connection.setConnectTimeout(750); connection.setReadTimeout(750);
                if (connection.getResponseCode() != 200) return Endpoint.OCCUPIED;
                byte[] bytes;
                try (var stream = connection.getInputStream()) { bytes = stream.readNBytes(65537); }
                if (bytes.length > 65536) return Endpoint.OCCUPIED;
                for (var element : JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    var target = element.getAsJsonObject();
                    if (target.has("type") && target.has("url") && target.has("webSocketDebuggerUrl") &&
                        target.get("type").getAsString().equals("page") &&
                        target.get("url").getAsString().startsWith("orpheus://orpheus/")) {
                        URI ws = URI.create(target.get("webSocketDebuggerUrl").getAsString());
                        if (ws.getScheme().equals("ws") && ws.getHost().equals("127.0.0.1") && ws.getPort() == 9223 &&
                            ws.getPath().startsWith("/devtools/page/")) return Endpoint.READY;
                    }
                }
                return Endpoint.OCCUPIED;
            } catch (ConnectException e) { return Endpoint.CLOSED; }
            catch (Exception e) { return Endpoint.OCCUPIED; }
            finally { if (connection != null) connection.disconnect(); }
        }
        public void start(Path executable) throws IOException {
            Process child = new ProcessBuilder(launchArguments(executable)).directory(executable.getParent().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            child.getOutputStream().close();
        }
    }
}
