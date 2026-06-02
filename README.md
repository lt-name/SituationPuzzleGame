# SituationPuzzleGame

Language: [![EN](https://img.shields.io/badge/EN-4c8bf5)](/README.md) [![中文](https://img.shields.io/badge/中文-d9d9d9)](/README_zh.md)

A "Situation Puzzle" (also known as "Lateral Thinking Puzzle") plugin for Nukkit-MOT servers. Players can create rooms, join games, ask questions, and view stats — all through in-game forms. Server owners can connect an AI to auto-generate puzzles and answer yes/no/irrelevant questions.

## Who Is This For

- Server owners who want to run situation puzzle / lateral thinking / deduction mini-games on their Minecraft Bedrock server
- Servers that want players to host their own rooms, or use AI to auto-generate and auto-answer puzzles
- Casual or mini-game servers looking for both single-player AI mode and multi-player room mode

## Features

- **Multi-player rooms**: Players create rooms, the host holds the truth, and others ask questions to deduce the answer
- **Single-player mode**: AI generates puzzles and auto-answers questions; wins automatically when the player deduces key truths
- **AI puzzle generation**: Auto-generates puzzle titles and truths by difficulty level
- **AI answering**: Hosts can let AI help judge player questions
- **Manual puzzles**: Without an AI generator, players can manually enter puzzle title and truth
- **Leaderboard & stats**: Tracks games played, completions, questions asked, hit rate, hosting count, and more
- **Puzzle cache**: Caches AI-generated puzzles to reduce repeated API calls
- **Multi-language**: Bundled with Simplified Chinese and English language files
- **Legacy client support**: New clients use Data-Driven UI; older clients fall back to standard forms with chat input

## Requirements

- Server: Nukkit-MOT
- Java: 17 or higher
- Client: Minecraft Bedrock 1.26.0+ for full Data-Driven UI
- Older clients can still use the legacy form flow with chat-based text input

## Installation

1. Place the `SituationPuzzleGame` plugin jar into your server's `plugins` directory.
2. Start the server once to generate the default config files.
3. Open `plugins/SituationPuzzleGame/config.yml` and configure AI, stats, and cache as needed.
4. Restart the server.
5. Players enter `/hgt` in-game to open the puzzle menu.

## Player Commands

| Command | Description |
| --- | --- |
| `/hgt` | Opens the main menu; if the player is already in a room, opens the current game UI |
| `/hgt rank` | Opens the leaderboard |
| `/hgt top` | Opens the leaderboard |
| `/hgt stats` | Shows your personal stats |

Permission node:

```text
situationpuzzlegame.use
```

This permission is granted to all players by default.

## How to Play

### Multi-player Mode

1. Player enters `/hgt`.
2. Select "Create Room".
3. Choose "AI Puzzle" or "Manual Puzzle".
4. Other players enter `/hgt` and select "Join Room".
5. The host clicks "Start Game".
6. Guessers submit yes/no questions; the host answers "Yes", "No", or "Irrelevant".
7. When the host ends the game, the truth is revealed to all players in the room.

### Single-player Mode

Single-player mode requires both a generator AI and an answerer AI to be configured.

1. Player enters `/hgt`.
2. Select "Single Player Mode".
3. Choose a difficulty.
4. After the AI generates a puzzle, the player begins deducing.
5. The player asks yes/no questions and the AI auto-answers; they can also submit a final deduction at any time.
6. When the AI determines the player has recovered the key truths, the game auto-wins and reveals the truth.
7. Giving up lets the player view the truth, but does not count as a single-player completion or streak.

## AI Configuration

Config file location:

```text
plugins/SituationPuzzleGame/config.yml
```

The plugin splits AI into two roles:

- `generator`: The puzzle-generating AI, responsible for creating puzzle titles and truths
- `answerer`: The answering AI, responsible for judging player questions as yes, no, or irrelevant

The default config includes example providers for DeepSeek and OpenCode GO. At minimum, server owners need to:

1. Replace `api-key` with a real key — do not keep the default placeholder text.
2. Confirm that `generator.provider` and `answerer.provider` point to the provider name you want to use.

Example:

```yaml
providers:
  deepseek-generator:
    api-type: "openai"
    api-url: "https://api.deepseek.com"
    api-key: "your DeepSeek API key"
    model: "deepseek-v4-pro"
    thinking-type: "enabled"
    reasoning-effort: "high"

  deepseek-answerer:
    api-type: "openai"
    api-url: "https://api.deepseek.com"
    api-key: "your DeepSeek API key"
    model: "deepseek-v4-flash"
    thinking-type: "disabled"

generator:
  provider: "deepseek-generator"

answerer:
  provider: "deepseek-answerer"
```

Supported `api-type` values:

- `openai`: OpenAI-compatible `/chat/completions` format
- `anthropic`: Anthropic `/v1/messages` format

`api-url` accepts a base URL — the plugin auto-appends the correct endpoint based on `api-type`. If you provide a full URL (e.g. `/chat/completions` or `/messages`), the plugin uses it as-is.

## Difficulty Configuration

Default difficulties:

- Easy: Beginner-friendly
- Normal: Standard situation puzzles
- Hard: For deduction enthusiasts
- Hell: Hardcore deduction

You can customize display names, star ratings, and descriptions in `generator.difficulties`.

## Stats & Caching

Stats config:

```yaml
stats:
  enabled: true
  leaderboard-size: 10
  auto-save-interval: 300
  min-questions-for-hit-rate: 10
```

Stats data is saved to:

```text
plugins/SituationPuzzleGame/stats.yml
```

Puzzle cache config:

```yaml
cache:
  enabled: true
  max-per-difficulty: 20
```

Cache data is saved to:

```text
plugins/SituationPuzzleGame/puzzle_cache.yml
```

When caching is enabled, the plugin stores previously generated puzzles and avoids giving the same player a puzzle they've already seen.

## Legacy Client Usage

If a player's client version does not support full Data-Driven UI, the plugin falls back to legacy form menus. In legacy mode, some text input is handled through the chat bar:

- When manually creating a puzzle, send the title and truth in chat
- Guessers send questions directly in chat
- The host can send `是` (yes), `不是` (no), `无关` (irrelevant) to answer the current question
- The host can send `AI` to invoke AI answering
- Type `记录` (record) to refresh the question log
- Type `离开` (leave) to leave the room
- Type `!message` to send normal chat messages

## FAQ

### Single-player mode is missing from the main menu

Single-player mode requires both a generator AI and an answerer AI. Please check:

- `generator.provider` points to an existing provider
- `answerer.provider` points to an existing provider
- Both providers have a valid `api-key`

### AI puzzle generation or answering fails

Please check:

- `api-key` is correct
- `api-url` is reachable from the server
- `api-type` matches the provider's protocol
- `model` is a valid model name supported by the provider
- Your provider account has available credits, quota, or an active subscription

Error details are usually printed in the server console log.

### Players cannot use commands

Make sure the player has:

```text
situationpuzzlegame.use
```

This permission is granted by default; if your server has a permissions management plugin, check whether it overrides the default.

### Legacy clients cannot input text in forms

Legacy clients use the chat input flow. Have players follow the on-screen instructions and send titles, truths, questions, or answers directly in the chat bar.

## File Reference

| File | Purpose |
| --- | --- |
| `plugins/SituationPuzzleGame/config.yml` | Main config: AI, stats, cache, difficulties |
| `plugins/SituationPuzzleGame/stats.yml` | Player statistics data |
| `plugins/SituationPuzzleGame/puzzle_cache.yml` | AI puzzle cache |
| `plugins/SituationPuzzleGame/language/zh_CN.lang` | Chinese text and AI prompts |
| `plugins/SituationPuzzleGame/language/en_US.lang` | English text and AI prompts |
