# Echo Terminal · 回声终端

一个**文字类终端风格的游戏引擎**。以复古终端为载体，用文字、符号与即时反馈承载互动叙事游戏：场景、选项、关键词输入、线索与结局统计，都在一个打字机般的终端界面中完成。

**亮点**：游戏即文件——上传一个符合规范的 `.json` 游戏文件即可**安装即玩**；任何人都可以依照 `samples/GAMEFILE.md` 格式规范与示例程序编写自己的互动叙事游戏。

## 特性

- 🖥️ **终端式游玩体验**：基于 xterm.js 的复古终端界面，支持编号选项与自然语言关键词输入
- 📦 **安装即玩**：在游戏库首页「上传安装」一个游戏文件即可游玩；同 `id` 覆盖已安装版本，删除外部副本自动回退内置版
- 🎮 **内置故事 + 示例游戏**：开箱即用的完整游戏（科幻、恐怖、推理题材）
- 🧩 **结构化叙事模型**：场景 / 动作 / 条件 / 效果四层模型，支持 flag、物品、信任值、计数器
- 📜 **结局与线索统计**：自动追踪结局达成、线索收集与信任值变化
- ⚙️ **严格校验**：安装时对格式、引用、嵌套深度做全面校验，拼写错误即时报错
- 🌐 **轻量部署**：单进程 Spring Boot 应用，内置全部静态资源，无外部依赖

## 快速开始

环境要求：**JDK 21+**（含 Maven Wrapper，无需预装 Maven）。

```bash
# 启动（首次会自动下载 Maven 依赖）
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

启动后打开浏览器访问：

- **游戏库首页**：http://localhost:8080/
- **游玩入口**：http://localhost:8080/play?story=<游戏id>

端口默认 `8080`，可通过 `server.port` 配置修改。

## 编写自己的游戏

游戏就是一个 **UTF-8 编码的 JSON 文件**（≤ 5MB），核心是描述场景、动作、条件与效果：

```json
{
  "format": 1,
  "id": "my-story",
  "title": "我的故事",
  "genre": "类型 · 标签",
  "version": "1.0",
  "author": "作者名",
  "description": "一句话简介，显示在游戏库卡片上。",
  "startScene": "boot",
  "endings": ["结局一", "结局二"],
  "clues": ["clue_a"],
  "scenes": { "...": "..." }
}
```

- **完整规范**：参见 [`samples/GAMEFILE.md`](samples/GAMEFILE.md)（格式、字段、场景/动作/条件/效果详解、行样式、限制）
- **示例程序**：见 `samples/` 目录，覆盖编号选项、自由输入、信任值、计数器、双物品门槛、首访/重访分支等写法

引擎内置全局指令，所有故事通用、无需定义：`帮助`、`背包`、`重新开始`、`关于`。

### 安装游戏

| 方式 | 操作 |
|---|---|
| 网页 | 游戏库首页「上传安装」，选择 `.json` 文件 |
| API | `POST /api/stories/install`（multipart 字段 `file`） |
| 直接部署 | 把文件放入外部游戏目录（默认运行目录下 `games/`），重启或热扫描加载 |

外部文件与内置故事同 `id` 时，外部版本优先；删除外部文件后回退内置版本。

## 内置与示例游戏

| 游戏 | 类型 | 位置 |
|---|---|---|
| 曙光-7 深空研究站 | 科幻 · 悬疑 | 内置 `src/main/resources/stories/` |
| 雨夜山庄杀人事件 | 破案 · 推理 | 内置 `src/main/resources/stories/` |
| 雾镇卫生院 | 恐怖 · 探险 · 解谜 | `games/` + `samples/` |
| 九龙城寨·无面佛 | 中式恐怖 · 悬疑 | `games/` |
| 霍格沃茨：石像鬼之夜 | 奇幻 · 恐怖 · 探险 | `samples/` |

## 项目结构

```
echo-terminal/
├── pom.xml                          # Maven 配置（Spring Boot 3.3.5, Java 21）
├── mvnw / mvnw.cmd                  # Maven Wrapper
├── games/                           # 外部安装的游戏目录（运行时加载）
├── samples/
│   ├── GAMEFILE.md                  # ★ 游戏文件格式规范（编写必读）
│   └── *.json                       # 示例游戏
└── src/main/
    ├── java/com/example/echo/
    │   ├── EchoApplication.java     # 应用入口
    │   ├── game/                    # 游戏运行时引擎（会话、回复、渲染）
    │   ├── story/                   # 故事模型与解析（场景/动作/条件/效果）
    │   └── web/                     # 页面与 REST API
    └── resources/
        ├── stories/                 # 内置故事
        ├── static/                  # 前端资源（xterm.js、样式、脚本）
        └── templates/               # 游戏库 / 游玩页面（Thymeleaf）
```

## HTTP API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 游戏库首页 |
| GET | `/play?story=<id>` | 游玩页面 |
| GET | `/api/stories` | 游戏列表（含场景数、结局数、是否内置等） |
| POST | `/api/stories/install` | 上传安装游戏（multipart 字段 `file`，仅 `.json`） |
| DELETE | `/api/stories/{id}` | 删除外部安装的游戏（内置不可删） |
| GET | `/api/stories/{id}/download` | 下载游戏文件 |
| POST | `/api/game/start` | 开始一局游戏，body：`{"storyId":"<id>"}` |
| POST | `/api/game/input` | 提交玩家输入，body：`{"text":"..."}` |

## 技术栈

- **后端**：Java 21 · Spring Boot 3.3.5 · Thymeleaf
- **前端**：xterm.js（终端模拟）+ jQuery，原生 CSS
- **数据**：游戏为 JSON 文件，无数据库；会话状态存于 HttpSession

## 许可证

本项目基于 **MIT License** 开源 —— 可自由使用、修改、分发与商用，仅需保留版权声明。详见 [LICENSE](LICENSE)。
