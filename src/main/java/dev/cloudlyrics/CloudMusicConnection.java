package dev.cloudlyrics;

import com.google.gson.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Loopback reader. Lyric requests use the player's API without changing playback or opening UI. */
public final class CloudMusicConnection implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CloudLyrics-Reader"); t.setDaemon(true); return t;
    });
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
        .proxy(new ProxySelector() {
            public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
            public void connectFailed(URI uri, SocketAddress address, IOException error) { }
        }).build();
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentMap<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final String script;
    private volatile WebSocket socket;
    private volatile LyricFrame latest = LyricFrame.empty("disconnected");
    private volatile long receivedNanos;
    private volatile String detail = "等待网易云连接";
    private volatile boolean enabled = true;
    private long retryAfter;
    private volatile boolean closed;

    public CloudMusicConnection() {
        try (var input = getClass().getResourceAsStream("/cloudlyrics/reader.js")) {
            if (input == null) throw new IOException("Missing reader.js");
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalStateException(e); }
        worker.scheduleWithFixedDelay(this::poll, 0, 150, TimeUnit.MILLISECONDS);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        detail = enabled ? "等待网易云连接" : "读取已关闭";
        if (!enabled) { latest = LyricFrame.empty("disabled"); disconnect(); }
    }
    public LyricFrame latest() {
        if (!enabled) return LyricFrame.empty("disabled");
        return System.nanoTime() - receivedNanos > TimeUnit.SECONDS.toNanos(3)
            ? LyricFrame.empty("disconnected") : latest;
    }
    public String detail() { return detail; }

    private void poll() {
        if (closed || !enabled || System.nanoTime() < retryAfter) return;
        try {
            if (socket == null) connect();
            if (!enabled || closed) { disconnect(); return; }
            JsonObject params = new JsonObject();
            params.addProperty("expression", script);
            params.addProperty("returnByValue", true);
            JsonObject response = request("Runtime.evaluate", params);
            JsonObject result = response.getAsJsonObject("result");
            if (response.has("error") || result == null || result.has("exceptionDetails"))
                throw new IOException("网易云接口版本不兼容或尚未就绪");
            JsonElement value = result.getAsJsonObject("result").get("value");
            LyricFrame frame = GSON.fromJson(value, LyricFrame.class);
            if (frame == null || frame.lyrics() == null || frame.playbackId() == null)
                throw new IOException("网易云返回的数据不完整");
            if (!enabled || closed) { disconnect(); return; }
            latest = frame;
            receivedNanos = System.nanoTime();
            detail = switch (frame.status()) {
                case "ready" -> frame.playing() ? "已连接 · 正在播放" : "已连接 · 已暂停";
                case "loading" -> "已连接 · 正在后台加载歌词";
                case "no_synced_lyrics" -> "这首歌没有带时间轴的歌词";
                case "unavailable" -> "歌词暂时获取失败，将自动重试";
                default -> "已连接 · 等待播放歌曲";
            };
        } catch (Exception e) {
            if (!enabled || closed) { disconnect(); return; }
            latest = LyricFrame.empty("disconnected");
            detail = e instanceof ConnectException ? "输入 /cloudlyrics launch 启动网易云连接" : "未连接：可用 /cloudlyrics launch 检查网易云启动方式";
            disconnect();
            retryAfter = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        }
    }

    private void connect() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:9223/json/list"))
            .timeout(Duration.ofSeconds(2)).GET().build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200 || res.body().length() > 1_000_000) throw new IOException("Invalid endpoint");
        for (var element : JsonParser.parseString(res.body()).getAsJsonArray()) {
            var page = element.getAsJsonObject();
            if (!"page".equals(page.get("type").getAsString()) ||
                !page.get("url").getAsString().startsWith("orpheus://orpheus/")) continue;
            URI endpoint = URI.create(page.get("webSocketDebuggerUrl").getAsString());
            if (!"ws".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost()) ||
                endpoint.getPort() != 9223 || !endpoint.getPath().startsWith("/devtools/page/"))
                throw new IOException("Non-local endpoint rejected");
            socket = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(2))
                .buildAsync(endpoint, new Receiver()).get(3, TimeUnit.SECONDS);
            return;
        }
        throw new IOException("Cloud Music page unavailable");
    }

    private JsonObject request(String method, JsonObject params) throws Exception {
        long id = sequence.incrementAndGet();
        var result = new CompletableFuture<JsonObject>();
        pending.put(id, result);
        JsonObject message = new JsonObject();
        message.addProperty("id", id); message.addProperty("method", method); message.add("params", params);
        try {
            socket.sendText(GSON.toJson(message), true).get(2, TimeUnit.SECONDS);
            return result.get(3, TimeUnit.SECONDS);
        } finally { pending.remove(id); }
    }

    private void disconnect() {
        WebSocket old = socket; socket = null;
        if (old != null) old.abort();
        pending.values().forEach(f -> f.completeExceptionally(new IOException("Disconnected")));
        pending.clear();
    }

    private final class Receiver implements WebSocket.Listener {
        private final StringBuilder message = new StringBuilder();
        public void onOpen(WebSocket ws) { ws.request(1); }
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            message.append(data);
            if (message.length() > 2_000_000) { ws.abort(); return null; }
            if (last) {
                try {
                    JsonObject parsed = JsonParser.parseString(message.toString()).getAsJsonObject();
                    if (parsed.has("id")) {
                        var future = pending.get(parsed.get("id").getAsLong());
                        if (future != null) future.complete(parsed);
                    }
                } catch (RuntimeException ignored) { }
                message.setLength(0);
            }
            ws.request(1); return null;
        }
        public void onError(WebSocket ws, Throwable error) {
            pending.values().forEach(f -> f.completeExceptionally(error));
        }
    }

    public void close() { closed = true; worker.shutdownNow(); disconnect(); http.close(); }
}
