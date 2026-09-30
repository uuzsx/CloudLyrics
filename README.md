# 云歌词 · Cloud Lyrics 1.3.1

![CloudLyrics Logo](src/main/resources/cloudlyrics-logo.png)

**作者：幼幼紫**

让音乐陪你挖矿、建造与冒险。

将 Windows 网易云音乐的歌词，跟随播放进度逐句显示在 Minecraft 自己的聊天框。

**无需开启网易云桌面歌词，也无需停留在网易云的歌词页。** 关闭这两个界面后，模组仍会独立获取当前歌曲的歌词并跟随切歌。

**[下载最新 JAR](https://github.com/uuzsx/CloudLyrics/releases/latest)** · [更新记录](CHANGELOG.md)

支持 Minecraft Java **1.21.1、1.21.2、26.1.1、26.1.2、26.2、26.3** 的 NeoForge 客户端，以及 Windows 网易云音乐桌面客户端。

## 发布与安装

**玩家只安装与游戏版本对应的一个 JAR。** 不需要配套 CMD、PS1、额外启动器或网易云插件；模组也不会解压运行这类脚本。

| Minecraft 版本 | NeoForge 最低版本 | Java | 下载文件 |
| --- | --- | --- | --- |
| 1.21.1 | 21.1.1 | 21 | [cloudlyrics-1.21.1-neoforge-1.3.1.jar](https://github.com/uuzsx/CloudLyrics/releases/download/v1.3.1/cloudlyrics-1.21.1-neoforge-1.3.1.jar) |
| 1.21.2 | 21.2.0-beta | 21 | [cloudlyrics-1.21.2-neoforge-1.3.1.jar](https://github.com/uuzsx/CloudLyrics/releases/download/v1.3.1/cloudlyrics-1.21.2-neoforge-1.3.1.jar) |
| 26.1.1 | 26.1.1.0-beta | 25 | [cloudlyrics-26.1.1-neoforge-1.3.1.jar](https://github.com/uuzsx/CloudLyrics/releases/download/v1.3.1/cloudlyrics-26.1.1-neoforge-1.3.1.jar) |
| 26.1.2 | 26.1.2.71 | 25 | [cloudlyrics-26.1.2-neoforge-1.3.1.jar](https://github.com/uuzsx/CloudLyrics/releases/download/v1.3.1/cloudlyrics-26.1.2-neoforge-1.3.1.jar) |
| 26.2 | 26.2.0.57 | 25 | [cloudlyrics-26.2-neoforge-1.3.1.jar](https://github.com/uuzsx/CloudLyrics/releases/download/v1.3.1/cloudlyrics-26.2-neoforge-1.3.1.jar) |
| 26.3 | 26.3.0.0-beta | 25 | [cloudlyrics-26.3-neoforge-1.3.1.jar](https://github.com/uuzsx/CloudLyrics/releases/download/v1.3.1/cloudlyrics-26.3-neoforge-1.3.1.jar) |

最低版本均为对应分支的构建验证基线，允许该 Minecraft 分支内更高的 NeoForge 构建。1.21.2、26.1.1、26.3 的验证基线本身带有 `beta` 标记。26.1.2 的最低要求进一步降至 71，因此也包含此前支持的 109、112。

这些是六个独立适配包，**不要同时安装，也不要把一个版本的 JAR 放进另一版本的游戏**。仅安装在客户端；不支持 Forge 或 Fabric。

玩家把 JAR 放进相应游戏实例的 `mods` 文件夹并重启游戏。升级时请移出旧版本 JAR，避免重复加载。旧版启动脚本可以不用了。

## 开始使用

1. 进入世界，输入 **`/cloudlyrics launch`**。
2. 模组自动查找已安装的网易云，并以支持歌词连接的方式启动它。
3. 如果提示网易云已在普通模式下运行，请从网易云的托盘菜单选择「退出」，再输入一次该指令。只关闭主窗口可能仍在后台运行。
4. 在网易云播放歌曲，聊天框会以 `♪ 歌词` 的形式逐句显示。连接和重新连接由模组自动处理。
5. 可以关闭网易云的桌面歌词和主窗口歌词页，只保留游戏内歌词。网易云播放器需继续运行和播放音乐。

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

- 使用网易云自身的高精度进度，按当前歌曲 ID 调用播放器自己的歌词接口，不按歌名猜歌词，不下载音频。
- 歌词独立缓存在模组读取器中，不依赖桌面歌词、歌词页或它们的界面缓存。获取失败会自动重试；快速切歌时迟到的响应不会覆盖新歌。
- 暂停时停止输出；切歌、倒带、单曲循环时重新定位。
- 同一时间点只输出一次；后面的副歌即使文字相同仍会正常出现。
- 跳转或重新连接只显示当前句，不把错过的歌词全部刷出来。
- 歌词只在自己的客户端显示，不向服务器广播。支持原文逐句显示，不额外显示翻译。
- 纯音乐、无歌词或无时间轴歌词不会输出伪造的字幕。
- 沿用原版聊天框的显示与淡出设置。`/cloudlyrics test` 也不可见时，请检查聊天透明度、可见性和过滤设置。
- 不支持的本地资源或没有可用歌词的歌曲保持静默；歌词接口暂时失败时，可用 `/cloudlyrics status` 查看状态。

当前适配并验证了 Windows 网易云 **3.1.38.205386**。本功能依赖网易云的内部播放接口，其他版本或后续更新可能需要适配。暂不支持 macOS、Linux、手机版或网页版网易云。

## 连接机制

启动功能直接使用 Java 启动本机已有的 `cloudmusic.exe`，只给这次进程添加参数。播放器的连接端口绑定 `127.0.0.1:9223`。没有修改系统启动项、网易云快捷方式或防火墙。

模组通过播放器现有接口请求歌词并读取播放状态，不读取账号凭据。模组的 Java 连接只访问本机；歌词请求由网易云客户端自己的网络接口完成，不将歌词上传至其他服务。普通方式重新启动网易云会关闭这项调试接口。

## 验证情况

- 1.3.1 为作者、简介和 Logo 更新；已重新打包六个版本，并核对每个 JAR 的元数据和原始 Logo 文件。游戏逻辑未改动，客户端运行验证沿用下述 1.3.0 结果。

- 1.3.0 的六个目标均使用上表中的 NeoForge 基线独立编译打包成功。
- 六个版本均启动了独立开发客户端，完成模组加载、界面资源初始化并正常退出。没有在每个版本中重新进入世界验证歌词画面，也未逐一验证第三方整合包。
- 每个目标通过 16 项歌词时间轴测试和 21 项启动路径/参数/分支测试，共享网易云读取器通过 22 项测试。
- 六个 JAR 均检查了实际 Minecraft/NeoForge 依赖范围、Java 字节码版本和共享歌词资源，确认没有混入其他版本的适配器或外部启动脚本。
- 已实测桌面歌词和主窗口歌词页均关闭时，从空的模组缓存加载歌词、切换歌曲以及播放进度持续前进。网易云界面缓存停留在旧歌时，模组仍取得新歌歌词。
- 已通过实际 Java 连接与逐句输出引擎验证播放进度、歌词读取和关闭/重连。
- 已验证运行中进程识别、通过注册信息查找非默认安装位置、已有连接的复用。
- 独立歌词读取逻辑沿用 1.2.0；上面的关闭歌词界面、切歌与同步实测来自 1.2.0 验证。聊天框与指令提示的接口按游戏版本分别适配。
- 未运行播放器的启动分支已通过替身测试，尚未进行完整冷启动实测，也未验证其他网易云版本、电脑或正式整合包的兼容性。

26.2/26.3 测试日志包含本机 OSHI 无法读取 Windows 英文性能计数器的提示，客户端仍完成初始化并正常退出；未发现云歌词加载或类链接错误。

## 源码与构建

开发者可克隆本仓库或下载 GitHub 提供的源码压缩包；普通玩家只需要 [Release 中的 JAR](https://github.com/uuzsx/CloudLyrics/releases/latest)。

```text
gradlew.bat build "-PmcVersion=1.21.1"
node tests/reader.test.cjs
```

`mcVersion` 可选 `1.21.1`、`1.21.2`、`26.1.1`、`26.1.2`、`26.2`、`26.3`，默认 `26.1.2`。构建产物在 `build/<游戏版本>/libs/`，开发客户端在独立的 `runs/<游戏版本>/` 目录中运行。

Gradle 推荐由 JDK 25 启动，编译 1.21.x 还需本机安装 JDK 21。`build` 会执行 Java 逻辑与启动流程测试；JavaScript 测试需 Node.js。全部构建后，运行 `python tests/artifacts.test.py` 检查六个发布包（需要 Python 3.11+）。Windows 开发者可执行 `scripts/build-all.ps1` 完成全量构建与检查；该脚本不随玩家 JAR 发布。

版本矩阵集中在 `gradle/targets.json`。`versions/` 中的六个 Gradle 子项目各自保存独立的任务与构建状态，`-PmcVersion` 只选中一个子项目；使用 `gradlew.bat build "-PallVersions=true"` 可在一次构建中生成全部版本。

`src/main` 保存共享业务逻辑；`src/platform/legacy`、`modern`、`hud` 分别适配 1.21.x、26.1.x、26.2/26.3 的聊天接口，每个子项目只编译选中的适配器。

可选本机验证：`gradlew.bat launcherProbe`（要求网易云已经按连接方式运行，不重启播放器），`gradlew.bat liveProbe`（实际播放与重连验证），`gradlew.bat runClient "-PmcVersion=26.3"`（选定版本的 NeoForge 开发客户端）。源码中的 Gradle Wrapper 是开发构建工具，不是玩家安装要求。

参考：[NeoForge 官方 MDK](https://github.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle)、[CloudMusic Desktop MCP](https://github.com/Seraph310/cloudmusic-desktop-mcp)、[Windows App Paths 注册信息](https://learn.microsoft.com/en-us/windows/win32/shell/app-registration)、[Java ProcessBuilder](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ProcessBuilder.html)。
