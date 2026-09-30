package dev.cloudlyrics;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

@Mod(value = "cloudlyrics", dist = Dist.CLIENT)
public final class CloudLyrics {
    private final Path config = FMLPaths.CONFIGDIR.get().resolve("cloudlyrics.properties");
    private final CloudMusicConnection connection;
    private final LineEmitter emitter = new LineEmitter();
    private boolean enabled = true;
    private int offsetMs;
    private boolean joined;
    private boolean connectionHint;
    private long joinedAt;
    private String lastSong = "";
    private String playerPath = "";
    private boolean launching;

    public CloudLyrics() {
        load();
        connection = new CloudMusicConnection();
        connection.setEnabled(enabled);
        Runtime.getRuntime().addShutdownHook(new Thread(connection::close, "CloudLyrics-Shutdown"));
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::commands);
    }

    private void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (joined) { emitter.reset(); lastSong = ""; }
            joined = false;
            return;
        }
        if (!joined) {
            joined = true; joinedAt = System.nanoTime(); connectionHint = false;
            info("云歌词已加载，/cloudlyrics 查看状态，/cloudlyrics off 关闭。" );
        }
        if (!enabled) return;
        LyricFrame frame = connection.latest();
        if ("disconnected".equals(frame.status())) {
            if (!connectionHint && System.nanoTime() - joinedAt > 8_000_000_000L) {
                launchHint();
                connectionHint = true;
            }
            return;
        }
        if (!frame.songId().isBlank() && !frame.songId().equals(lastSong)) {
            lastSong = frame.songId();
            info("正在听：" + LineEmitter.sanitize(frame.title()) + " — " + LineEmitter.sanitize(frame.artist()));
        }
        String lyric = emitter.accept(frame, offsetMs);
        if (lyric != null) {
            ClientPlatform.showChat(Component.literal("♪ ")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(lyric).withStyle(ChatFormatting.WHITE)));
        }
    }

    private void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cloudlyrics")
            .executes(c -> status())
            .then(Commands.literal("status").executes(c -> status()))
            .then(Commands.literal("on").executes(c -> toggle(true)))
            .then(Commands.literal("off").executes(c -> toggle(false)))
            .then(Commands.literal("launch").executes(c -> launchPlayer()))
            .then(Commands.literal("path")
                .executes(c -> { info(playerPath.isBlank() ? "网易云路径：自动检测。" : "网易云路径：" + playerPath); return 1; })
                .then(Commands.argument("executable", StringArgumentType.greedyString()).executes(c -> {
                    String input = StringArgumentType.getString(c, "executable");
                    if (input.equalsIgnoreCase("auto")) { playerPath = ""; save(); info("已恢复自动查找网易云。" ); return 1; }
                    var path = CloudMusicLauncher.validatePath(input);
                    if (path.isEmpty()) { info("请填写实际存在的 cloudmusic.exe 完整路径，支持空格和中文。" ); return 0; }
                    playerPath = path.get().toString(); save(); info("已保存网易云路径。输入 /cloudlyrics launch 启动连接。" ); return 1;
                })))
            .then(Commands.literal("offset")
                .then(Commands.argument("milliseconds", IntegerArgumentType.integer(-10000, 10000))
                    .executes(c -> {
                        offsetMs = IntegerArgumentType.getInteger(c, "milliseconds");
                        emitter.reset(); save();
                        info("同步微调：" + offsetMs + " ms（正数提前，负数延后）。"); return 1;
                    })))
            .then(Commands.literal("test").executes(c -> {
                info("聊天框显示正常。这是一条本地测试消息。♪"); return 1;
            })));
    }

    private int status() {
        info((enabled ? "已开启" : "已关闭") + " · " + connection.detail() + " · 微调 " + offsetMs + " ms");
        info("指令：/cloudlyrics launch | on | off | status | offset <毫秒> | path <完整路径或auto> | test");
        return 1;
    }
    private int launchPlayer() {
        if (launching) { info("正在查找和启动网易云，请稍候。" ); return 0; }
        launching = true;
        info("正在查找网易云安装位置……" );
        String configured = playerPath;
        CompletableFuture.supplyAsync(() -> {
            try { return new CloudMusicLauncher().launch(configured); }
            catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        }).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
            launching = false;
            if (error != null) { info("启动失败，请检查网易云安装路径与系统权限：/cloudlyrics path <完整路径>。" ); return; }
            if (result.executable() != null) { playerPath = result.executable().toString(); save(); }
            switch (result.outcome()) {
                case READY, STARTED -> {
                    enabled = true; connection.setEnabled(true); emitter.reset(); save();
                    info(result.outcome() == CloudMusicLauncher.Outcome.READY
                        ? "网易云连接已就绪，播放歌曲即可显示歌词。"
                        : "已启动网易云并开启歌词连接，播放歌曲即可。连接会自动建立。" );
                }
                case ALREADY_RUNNING -> info("网易云正在普通模式下运行。请从其托盘菜单选择「退出」，然后再次输入 /cloudlyrics launch。" );
                case NOT_FOUND -> info("没有找到网易云。先安装 Windows 电脑版，或用 /cloudlyrics path <cloudmusic.exe完整路径> 指定位置。" );
                case PORT_IN_USE -> info("歌词连接端口正被占用或网易云正在启动。稍候再试；若仍失败，请退出其他使用 9223 端口的程序。" );
                case UNSUPPORTED -> info("当前版本需要 Windows 和网易云音乐桌面客户端。" );
            }
        }));
        return 1;
    }
    private static void launchHint() {
        ClientPlatform.showChat(Component.literal("[云歌词] ")
            .withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal("输入 /cloudlyrics launch 启动网易云并连接歌词。若网易云已开着，请先从托盘退出。 ")
                .withStyle(ChatFormatting.GRAY))
            .append(Component.literal("[点击填入指令]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withUnderlined(true).withClickEvent(ClientPlatform.suggestCommand("/cloudlyrics launch")))));
    }
    private int toggle(boolean value) {
        enabled = value; connection.setEnabled(value); emitter.reset(); save();
        info(value ? "云歌词已开启。" : "云歌词已关闭。"); return 1;
    }
    private static void info(String text) {
        ClientPlatform.showChat(
            Component.literal("[云歌词] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(text).withStyle(ChatFormatting.GRAY)));
    }
    private void load() {
        if (!Files.exists(config)) return;
        try (var reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
            var properties = new Properties(); properties.load(reader);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            playerPath = properties.getProperty("playerPath", "");
            offsetMs = Math.clamp(Integer.parseInt(properties.getProperty("offsetMs", "0")), -10000, 10000);
        } catch (Exception ignored) { enabled = true; offsetMs = 0; }
    }
    private void save() {
        try {
            Files.createDirectories(config.getParent());
            var properties = new Properties();
            properties.setProperty("enabled", Boolean.toString(enabled));
            properties.setProperty("offsetMs", Integer.toString(offsetMs));
            properties.setProperty("playerPath", playerPath);
            try (var writer = Files.newBufferedWriter(config, StandardCharsets.UTF_8)) {
                properties.store(writer, "Cloud Lyrics: positive offsetMs displays lyrics earlier");
            }
        } catch (Exception e) { info("配置无法保存，本次会话仍然有效。"); }
    }
}
