# 云歌词 · Cloud Lyrics 1.1.0（单 JAR 版）

将 Windows 网易云音乐的歌词，跟随播放进度逐句显示在 Minecraft 自己的聊天框。

**[下载最新 JAR](https://github.com/uuzsx/CloudLyrics/releases/latest)** · [更新记录](CHANGELOG.md)

适用：Minecraft Java 26.1.2 · NeoForge 26.1.2.112+（26.1.2 系列）· Windows 网易云音乐桌面客户端。

## 发布与安装

**发布时只上传 `cloudlyrics-26.1.2-neoforge-1.1.0.jar` 即可。** 不需要配套 CMD、PS1、额外启动器或网易云插件；模组也不会解压运行这类脚本。

需要 Minecraft Java **26.1.2**、NeoForge **26.1.2.112 或更高的 26.1.2 构建**、Java 25，以及 Windows 网易云音乐桌面客户端。只安装在游戏客户端。

玩家把 JAR 放进相应游戏实例的 `mods` 文件夹并重启游戏。如果原来装过 1.0.0，请移出旧 JAR，避免重复加载。旧版启动脚本可以不用了。

## 开始使用

1. 进入世界，输入 **`/cloudlyrics launch`**。
2. 模组自动查找已安装的网易云，并以支持歌词连接的方式启动它。
3. 如果提示网易云已在普通模式下运行，请从网易云的托盘菜单选择「退出」，再输入一次该指令。只关闭主窗口可能仍在后台运行。
4. 在网易云播放歌曲，聊天框会以 `♪ 歌词` 的形式逐句显示。连接和重新连接由模组自动处理。

若网易云已通过正确方式启动，指令直接复用它，不重复开播放器。进入世界后未连接时，会显示可点击的启动指令提示。

网易云下次完全退出后，仍可用游戏内指令启动；不需要寻找或运行外部文件。模组不会在游戏启动时自行打开、关闭或重启播放器。

## 非默认安装位置

模组会依次使用保存的路径、正在运行的网易云路径、Windows 安装注册信息和常见安装目录。无需假定网易云安装在 C 盘，也不会扫描整个硬盘。

若自动识别失败，用以下指令保存本机实际路径（支持空格和中文，可加双引号）：

```text
/cloudlyrics path "D:\我的软件\CloudMusic\cloudmusic.exe"
/cloudlyrics launch
```

恢复自动识别：`/cloudlyrics path auto`。查看保存的位置：`/cloudlyrics path`。

## 指令

| 指令 | 用途 |
| --- | --- |
| `/cloudlyrics launch` | 查找并启动网易云歌词连接，同时开启歌词显示 |
| `/cloudlyrics` 或 `/cloudlyrics status` | 查看连接状态 |
| `/cloudlyrics on` | 开启歌词显示与读取 |
| `/cloudlyrics off` | 关闭歌词显示与读取，网易云继续播放 |
| `/cloudlyrics offset 300` | 提前 300 毫秒显示 |
| `/cloudlyrics offset -300` | 延后 300 毫秒显示 |
| `/cloudlyrics offset 0` | 恢复默认微调 |
| `/cloudlyrics path <完整路径>` | 设置本机网易云程序位置 |
| `/cloudlyrics path auto` | 恢复自动查找 |
| `/cloudlyrics test` | 输出一条本地聊天框测试消息 |

配置自动保存到游戏实例的 `config/cloudlyrics.properties`。这个文件是模组生成的本机设置，不需要随 JAR 发布，也不应打包分享自己的程序路径。

## 歌词行为与兼容性

- 使用网易云自身的高精度进度和已加载歌词，不按歌名猜歌词，不下载音频。
- 暂停时停止输出；切歌、倒带、单曲循环时重新定位。
- 同一时间点只输出一次；后面的副歌即使文字相同仍会正常出现。
- 跳转或重新连接只显示当前句，不把错过的歌词全部刷出来。
- 歌词只在自己的客户端显示，不向服务器广播。支持原文逐句显示，不额外显示翻译。
- 纯音乐、无歌词或无时间轴歌词不会输出伪造的字幕。
- 沿用原版聊天框的显示与淡出设置。`/cloudlyrics test` 也不可见时，请检查聊天透明度、可见性和过滤设置。
- 歌词加载不出来时，可在网易云打开一次当前歌曲的歌词页。

当前适配并验证了 Windows 网易云 **3.1.38.205386**。本功能依赖网易云的内部播放接口，其他版本或后续更新可能需要适配。暂不支持 macOS、Linux、手机版或网页版网易云。

## 连接机制

启动功能直接使用 Java 启动本机已有的 `cloudmusic.exe`，只给这次进程添加参数。播放器的连接端口绑定 `127.0.0.1:9223`。没有修改系统启动项、网易云快捷方式或防火墙。

模组只读取歌词与播放状态，不读取账号凭据。模组自身只连接本机，不将歌词上传至外部服务。普通方式重新启动网易云会关闭这项调试接口。

## 验证情况

- 1.1.0 编译打包成功。
- 16 项歌词时间轴测试、21 项启动路径/参数/分支测试、10 项网易云读取适配器测试通过。
- 已验证运行中进程识别、通过注册信息查找非默认安装位置、已有连接的复用。
- 1.0.0 已在独立 NeoForge 测试世界中确认实际输出歌词并跟随切歌。1.1.0 保留该歌词读取与显示实现，新增 JAR 内启动功能。
- 1.1.0 的未运行播放器启动分支已通过替身测试，尚未进行完整冷启动实测，也未验证其他电脑或正式整合包的兼容性。

## 源码与构建

开发者可克隆本仓库或下载 GitHub 提供的源码压缩包；普通玩家只需要 [Release 中的 JAR](https://github.com/uuzsx/CloudLyrics/releases/latest)。

```text
gradlew.bat build
node tests/reader.test.cjs
```

安装 JDK 25 后执行上述命令。`build` 会执行 Java 逻辑与启动流程测试；JavaScript 测试需 Node.js。

可选本机验证：`gradlew.bat launcherProbe`（要求网易云已经按连接方式运行，不重启播放器），`gradlew.bat liveProbe`（实际播放与重连验证），`gradlew.bat runClient`（NeoForge 开发客户端）。源码中的 Gradle Wrapper 是开发构建工具，不是玩家安装要求。

参考：[NeoForge 官方 MDK](https://github.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle)、[CloudMusic Desktop MCP](https://github.com/Seraph310/cloudmusic-desktop-mcp)、[Windows App Paths 注册信息](https://learn.microsoft.com/en-us/windows/win32/shell/app-registration)、[Java ProcessBuilder](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ProcessBuilder.html)。
