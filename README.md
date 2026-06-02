# SituationPuzzleGame

一个面向 Nukkit-MOT 服务器的海龟汤插件。玩家可以在服务器内通过表单创建房间、加入房间、提问、回答和查看统计；服主可以接入 AI，让插件自动生成题目并辅助回答「是 / 不是 / 无关」。

## 适合谁用

- 想在 Minecraft Bedrock 服务器里开一局海龟汤、情境猜谜、推理小游戏的服主
- 想让玩家自己创建房间、主持游戏，或者使用 AI 自动出题的服务器
- 想提供单人 AI 推理模式和多人房间模式的休闲服、小游戏服

## 主要功能

- 多人房间：玩家创建房间，主持人掌握汤底，其他玩家提问推理
- 单人模式：AI 出题、自动回答问题，并在玩家还原关键真相时自动判定胜利
- AI 出题：按难度自动生成汤面和汤底
- AI 回答：主持人可让 AI 辅助判断玩家问题
- 手动出题：不配置出题 AI 也可以由玩家手动填写汤面和汤底
- 排行榜与个人统计：记录游戏次数、完成数、提问数、命中率、主持次数等
- 题目缓存：缓存 AI 生成题目，减少重复 API 调用
- 多语言资源：内置简体中文和英文语言文件
- 兼容界面：新客户端使用 Data-Driven UI，旧客户端使用普通表单和聊天输入

## 运行要求

- 服务端：Nukkit-MOT
- Java：17 或更高
- 客户端：Minecraft Bedrock 1.26.0 或更高可使用完整 Data-Driven UI
- 低版本客户端仍可使用旧版表单流程，文本输入通过聊天栏完成

## 安装方法

1. 将 `SituationPuzzleGame` 插件 jar 放入服务器的 `plugins` 目录。
2. 启动服务器一次，让插件生成默认配置文件。
3. 打开 `plugins/SituationPuzzleGame/config.yml`，按需要配置 AI、统计和缓存。
4. 重启服务器。
5. 玩家进入服务器后输入 `/hgt` 打开海龟汤菜单。

## 玩家命令

| 命令 | 说明 |
| --- | --- |
| `/hgt` | 打开主菜单；如果玩家已在房间内，则打开当前游戏界面 |
| `/hgt rank` | 打开排行榜 |
| `/hgt top` | 打开排行榜 |
| `/hgt stats` | 查看自己的统计数据 |

权限节点：

```text
situationpuzzlegame.use
```

该权限默认对所有玩家开放。

## 基本玩法

### 多人模式

1. 玩家输入 `/hgt`。
2. 选择「创建房间」。
3. 选择「AI 出题」或「手动出题」。
4. 其他玩家输入 `/hgt`，选择「加入房间」。
5. 主持人点击「开始游戏」。
6. 猜题者提交是非题，主持人回答「是」「不是」「无关」。
7. 主持人结束游戏后，插件会向房间玩家展示汤底。

### 单人模式

单人模式需要同时配置出题 AI 和回答 AI。

1. 玩家输入 `/hgt`。
2. 选择「单人模式」。
3. 选择难度。
4. AI 生成题目后，玩家开始推理。
5. 玩家提出是非题，AI 自动回答；也可以直接提交最终推理。
6. 当 AI 判断玩家已经还原关键真相时，单人模式会自动获胜并展示汤底。
7. 玩家放弃后可查看汤底，但不会计入单人完成或连胜。

## AI 配置说明

配置文件位置：

```text
plugins/SituationPuzzleGame/config.yml
```

插件把 AI 分成两类：

- `generator`：出题 AI，负责生成汤面和汤底
- `answerer`：回答 AI，负责根据汤底判断玩家问题是、不是或无关

默认配置中已经给出了 DeepSeek 和 OpenCode GO 的示例提供商。服主至少需要做两件事：

1. 把 `api-key` 改成真实密钥，不要保留默认占位文本。
2. 确认 `generator.provider` 和 `answerer.provider` 指向你要使用的提供商名称。

示例：

```yaml
providers:
  deepseek-generator:
    api-type: "openai"
    api-url: "https://api.deepseek.com"
    api-key: "你的 DeepSeek API 密钥"
    model: "deepseek-v4-pro"
    thinking-type: "enabled"
    reasoning-effort: "high"

  deepseek-answerer:
    api-type: "openai"
    api-url: "https://api.deepseek.com"
    api-key: "你的 DeepSeek API 密钥"
    model: "deepseek-v4-flash"
    thinking-type: "disabled"

generator:
  provider: "deepseek-generator"

answerer:
  provider: "deepseek-answerer"
```

`api-type` 支持：

- `openai`：OpenAI 兼容的 `/chat/completions` 格式
- `anthropic`：Anthropic `/v1/messages` 格式

`api-url` 可以填写基础地址。插件会根据 `api-type` 自动补全请求端点；如果你已经填写完整的 `/chat/completions` 或 `/messages` 地址，插件会直接使用。

## 难度配置

默认难度有：

- 简单：新人向
- 普通：标准海龟汤
- 困难：推理爱好者
- 地狱：硬核推理

你可以在 `generator.difficulties` 中修改显示名称、星级和描述。

## 统计与缓存

统计配置：

```yaml
stats:
  enabled: true
  leaderboard-size: 10
  auto-save-interval: 300
  min-questions-for-hit-rate: 10
```

统计数据会保存到：

```text
plugins/SituationPuzzleGame/stats.yml
```

题目缓存配置：

```yaml
cache:
  enabled: true
  max-per-difficulty: 20
```

缓存数据会保存到：

```text
plugins/SituationPuzzleGame/puzzle_cache.yml
```

开启缓存后，插件会保存 AI 生成过的题目，并尽量避免同一玩家重复抽到已看过的题目。

## 旧客户端使用方式

如果玩家客户端版本低于完整 Data-Driven UI 要求，插件会使用旧版表单菜单。旧版模式下，部分文本输入需要直接发送到聊天栏：

- 手动创建题目时，在聊天栏发送汤面和汤底
- 猜题者直接在聊天栏发送问题
- 主持人可发送 `是`、`不是`、`无关` 回答当前问题
- 主持人可发送 `AI` 调用 AI 回答
- 输入 `记录` 可刷新提问记录
- 输入 `离开` 可退出房间
- 输入 `!消息` 可发送普通聊天内容

## 常见问题

### 主菜单里没有单人模式

单人模式需要同时启用出题 AI 和回答 AI。请检查：

- `generator.provider` 是否指向存在的提供商
- `answerer.provider` 是否指向存在的提供商
- 两个提供商的 `api-key` 是否已经填写

### AI 出题或回答失败

请检查：

- `api-key` 是否正确
- `api-url` 是否能从服务器访问
- `api-type` 是否与服务商协议匹配
- `model` 是否是服务商支持的模型名
- 服务商账号是否有余额、额度或可用订阅

错误详情通常会输出在服务端控制台日志中。

### 玩家无法使用命令

请确认玩家拥有：

```text
situationpuzzlegame.use
```

该权限默认开放；如果服务器安装了权限管理插件，请检查是否覆盖了默认权限。

### 旧客户端不能输入表单文本

旧客户端会走聊天输入流程。让玩家按界面提示直接把汤面、汤底、问题或回答发送到聊天栏即可。

## 文件速查

| 文件 | 用途 |
| --- | --- |
| `plugins/SituationPuzzleGame/config.yml` | 主配置，包含 AI、统计、缓存、难度 |
| `plugins/SituationPuzzleGame/stats.yml` | 玩家统计数据 |
| `plugins/SituationPuzzleGame/puzzle_cache.yml` | AI 题目缓存 |
| `plugins/SituationPuzzleGame/language/zh_CN.lang` | 中文文案和 AI 提示词 |
| `plugins/SituationPuzzleGame/language/en_US.lang` | 英文文案和 AI 提示词 |
