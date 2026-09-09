# 游戏文件格式规范（format: 1）

一个游戏文件就是一个 `.json` 文件。把它通过游戏库首页的「上传安装」装入，即可游玩；
同 `id` 的文件会覆盖已安装版本（覆盖内置版本后，删除外部副本可回退到内置版）。

- 编码：UTF-8；大小不超过 5MB
- 数量上限：场景 ≤ 200、每场景动作 ≤ 100、每动作分支 ≤ 50、每处行数组 ≤ 300 行、单行 ≤ 2000 字
- 未知字段、重复 JSON 键、超过 64 层的嵌套都会报错，便于发现拼写错误

## 顶层结构

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
  "clues": ["clue_a", "clue_b"],
  "scenes": { "...": "..." }
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `format` | 是 | 固定为 `1` |
| `id` | 是 | 小写字母/数字/连字符，须匹配 `^[a-z0-9][a-z0-9-]{0,63}$`，也是落盘文件名 |
| `title` | 是 | 游戏标题 |
| `startScene` | 是 | 起始场景的 id，必须存在于 `scenes` |
| `endings` | 建议 | 结局标题数组，用于结局统计「结局 x/N」 |
| `clues` | 建议 | 关键线索 flag 名数组，用于统计「线索 n/总数」（收集线索即 `setFlag` 同名 flag） |
| `genre`/`version`/`author`/`description` | 否 | 元信息，展示用 |

## 场景（scenes）

```json
"boot": {
  "numbered": true,
  "enter": {
    "branches": [ { "when": { "notFlag": "visited_boot" }, "lines": ["首访长描述……"], "effects": [ { "setFlag": "visited_boot" } ] } ],
    "lines": ["重访短描述……"]
  },
  "effects": [ ],
  "fallback": [ { "text": "（不知道能做什么时的提示）", "style": "dim" } ],
  "actions": [ ]
}
```

- `enter`：入场文本。可以是纯行数组，也可以是 `{ branches, lines }`——按序取第一个 `when` 为真的分支，否则用 `lines`。分支可带 `effects`（例如首访 `setFlag visited_*`，实现"首访细描、重访简述"）。
- `effects`：每次进入场景都会应用的效果（可选）。
- `fallback`：无法匹配任何动作时的兜底回复（建议写明当前可做的事）。
- `numbered: true`：回复末尾自动列出带 `label` 的动作作为编号选项，玩家输入数字即可选择。
- 所有 `goto` 的目标场景必须存在；`numbered` 场景至少要有一个带 `label` 的动作。

## 动作（actions）

```json
{
  "label": "接入信号，建立上行链路",
  "keywords": ["接入", "连接", "信号"],
  "when": { "notFlag": "connected" },
  "branches": [
    { "when": { "flag": "has_key" }, "lines": ["……"], "effects": [], "goto": "hub" }
  ],
  "lines": ["动作默认台词……"],
  "effects": [ { "setFlag": "connected" } ],
  "goto": "hub"
}
```

- `keywords`：玩家输入包含关键词即触发；多个动作同时命中时，取**最长关键词**匹配者。
- `label`：仅 `numbered` 场景需要，作为编号选项文本。
- `when`：动作可见性条件（对编号列表和关键词匹配同时生效）。
- `branches`：按序取第一个 `when` 为真的分支；全不中则执行动作自身的 `lines/effects/goto`。
  分支全不中且动作自身无任何内容时，该动作视为不可用（走场景 `fallback`，不应用任何效果）。
- `goto`：跳转到目标场景——目标场景的入场文本会**追加进同一条回复**，并应用其入场效果。
- 一次性动作：用 `when: { "notFlag": "done_x" }` + `effects: [{ "setFlag": "done_x" }]`，或 `notItem`/`item` 实现。

## 条件（when）

缺省 = 恒真；多个键同时出现 = 隐式 AND。

```json
{ "flag": "clue_a" }                     // 已设置 flag
{ "notFlag": "visited_x" }               // 未设置 flag
{ "item": "门禁卡" }                      // 背包中有该物品（精确名称）
{ "notItem": "门禁卡" }
{ "trustMin": 3 }  / { "trustMax": 1 }   // 信任值区间
{ "counterMin": { "ignores": 1 } }       // 计数器 ≥ 阈值（addCounter 每次 +1）
{ "and": [ {...}, {...} ] }
{ "or":  [ {...}, {...} ] }
{ "not": {...} }
```

嵌套深度 ≤ 8 层。**条件求值发生在本轮效果应用之前**——例如「忽略两次后触发叹息」应写
`counterMin: {"ignores": 1}`（第一次忽略使计数器变 1，第二次忽略时条件才为真）。

## 效果（effects）

数组，按序执行：

```json
{ "setFlag": "clue_med" }      // 设置 flag（线索统计依赖与 clues 数组同名的 flag）
{ "addItem": "门禁卡（核心机房）" }
{ "trust": 1 }                  // 信任值 ±n
{ "addCounter": "ignores" }     // 计数器 +1
{ "ending": "静默" }            // 结束游戏，结局标题；必须是最后一个效果
```

触发 `ending` 后，引擎自动追加 `—— 结局 · 标题 ——` 与统计行（结局 x/N · 信任值 · 线索 n/总数），
之后玩家输入任意内容即自动重开。

## 行（lines）

纯字符串 = `narr` 样式；也可写对象指定样式：

```json
{ "text": "获得物品：门禁卡", "style": "item" }
```

可选样式：`banner` `system` `ai` `narr` `warn` `item` `voice` `ending` `dim`。

## 全局指令（所有故事通用，无需定义）

`帮助` / `背包`（物品）/ `重新开始` / `关于`。关键词动作请避开这些词。

## 参考示例

- `aurora-7.json` —— 《曙光-7 深空研究站》：编号选项、信任值、计数器、双物品门槛、一次性动作的完整示范。
- `rainy-night-manor.json` —— 《雨夜山庄杀人事件》：自由输入探索、首访/重访分支、证据复合条件、错误指认宽限（计数器）的示范。
