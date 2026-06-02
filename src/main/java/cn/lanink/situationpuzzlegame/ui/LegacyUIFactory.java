package cn.lanink.situationpuzzlegame.ui;

import cn.lanink.situationpuzzlegame.SituationPuzzleGame;
import cn.lanink.situationpuzzlegame.ai.AiService;
import cn.lanink.situationpuzzlegame.cache.PuzzleCache;
import cn.lanink.situationpuzzlegame.config.PluginConfig;
import cn.lanink.situationpuzzlegame.game.GameRoom;
import cn.lanink.situationpuzzlegame.game.GameState;
import cn.lanink.situationpuzzlegame.i18n.Texts;
import cn.lanink.situationpuzzlegame.stats.PlayerStats;
import cn.lanink.situationpuzzlegame.stats.StatsManager;
import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.response.FormResponse;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.lang.LangCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class LegacyUIFactory implements Listener {

    private enum ChatMode {
        MANUAL_TITLE,
        MANUAL_TRUTH
    }

    private record ChatSession(ChatMode mode, String title) {}

    private record ButtonAction(String label, Consumer<Player> action) {}

    private final SituationPuzzleGame plugin;
    private final Map<Player, Map<Integer, Consumer<FormResponseSimple>>> formHandlers =
            new IdentityHashMap<>();
    private final Map<Player, ChatSession> chatSessions = new IdentityHashMap<>();
    private final Set<Player> pendingGeneration =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Player> pendingAiAnswer =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public LegacyUIFactory(SituationPuzzleGame plugin) {
        this.plugin = plugin;
    }

    public void clearAll() {
        formHandlers.clear();
        chatSessions.clear();
        pendingGeneration.clear();
        pendingAiAnswer.clear();
    }

    public void clearPlayer(Player player) {
        formHandlers.remove(player);
        chatSessions.remove(player);
        pendingGeneration.remove(player);
        pendingAiAnswer.remove(player);
    }

    public void showContext(Player player) {
        GameRoom room = plugin.getPlayerRoom(player);
        if (room == null) {
            showMainMenu(player);
            return;
        }
        if (room.isSinglePlayer()) {
            switch (room.getState()) {
                case PLAYING -> showSinglePlayerGameMenu(player, room);
                case FINISHED -> showResultMenu(player, room);
                default -> showMainMenu(player);
            }
            return;
        }
        switch (room.getState()) {
            case WAITING -> showLobbyMenu(player, room);
            case PLAYING -> showGameMenu(player, room);
            case FINISHED -> showResultMenu(player, room);
        }
    }

    public void showMainMenu(Player player) {
        PluginConfig cfg = plugin.getPluginConfig();
        List<ButtonAction> actions = new ArrayList<>();
        if (cfg.isGeneratorEnabled() && cfg.isAnswererEnabled()) {
            actions.add(button(t(player, "button.single-player"), this::showSinglePlayerDifficultyMenu));
        }
        actions.add(button(t(player, "button.create-room"), this::showCreateRoomMenu));
        actions.add(button(t(player, "button.join-room"), this::showRoomListMenu));
        if (cfg.isStatsEnabled()) {
            actions.add(button(t(player, "button.leaderboard"), this::showLeaderboardMenu));
            actions.add(button(t(player, "button.my-stats"), this::showPersonalStats));
        }
        showSimple(
                player,
                t(player, "title.app"),
                t(player, "main.description") + "\n\n" + t(player, "legacy.hint.chat-input"),
                actions);
    }

    public void showLeaderboardMenu(Player player) {
        if (!plugin.getPluginConfig().isStatsEnabled()) {
            showMainMenu(player);
            return;
        }
        List<ButtonAction> actions = new ArrayList<>();
        actions.add(button(metricTitle(player, "solo_completed"), p -> showLeaderboard(p, "solo_completed")));
        actions.add(button(metricTitle(player, "multi_completed"), p -> showLeaderboard(p, "multi_completed")));
        actions.add(button(metricTitle(player, "total_games"), p -> showLeaderboard(p, "total_games")));
        actions.add(button(metricTitle(player, "questions"), p -> showLeaderboard(p, "questions")));
        actions.add(
                button(
                        t(player, "metric.hit-rate-min", plugin.getPluginConfig().getMinQuestionsForHitRate()),
                        p -> showLeaderboard(p, "hit_rate")));
        actions.add(button(metricTitle(player, "host"), p -> showLeaderboard(p, "host")));
        actions.add(button(metricTitle(player, "solo_streak"), p -> showLeaderboard(p, "solo_streak")));
        actions.add(button(t(player, "button.back"), this::showMainMenu));
        showSimple(player, t(player, "title.leaderboard"), t(player, "leaderboard.header"), actions);
    }

    public void showLeaderboard(Player player, String metric) {
        StatsManager sm = plugin.getStatsManager();
        int limit = plugin.getPluginConfig().getLeaderboardSize();
        int minQ = plugin.getPluginConfig().getMinQuestionsForHitRate();
        List<PlayerStats> top =
                switch (metric) {
                    case "solo_completed" -> sm.getTopBySoloCompleted(limit);
                    case "multi_completed" -> sm.getTopByMultiCompleted(limit);
                    case "total_games" -> sm.getTopByTotalGames(limit);
                    case "questions" -> sm.getTopByQuestionsAsked(limit);
                    case "hit_rate" -> sm.getTopByHitRate(limit, minQ);
                    case "host" -> sm.getTopByHostCount(limit);
                    case "solo_streak" -> sm.getTopBySoloStreak(limit);
                    default -> List.of();
                };

        StringBuilder content = new StringBuilder();
        if (top.isEmpty()) {
            content.append(t(player, "label.no-data"));
        } else {
            for (int i = 0; i < top.size(); i++) {
                PlayerStats s = top.get(i);
                String medal =
                        switch (i) {
                            case 0 -> "§6#1";
                            case 1 -> "§7#2";
                            case 2 -> "§e#3";
                            default -> "§f#" + (i + 1);
                        };
                content.append(medal)
                        .append(" §f")
                        .append(s.getPlayerName())
                        .append(" §7- §e")
                        .append(formatMetricValue(metric, s))
                        .append("\n");
            }
        }

        showSimple(
                player,
                t(player, "title.leaderboard-metric", metricTitle(player, metric)),
                content.toString(),
                List.of(button(t(player, "button.back-leaderboard"), this::showLeaderboardMenu)));
    }

    public void showPersonalStats(Player player) {
        if (!plugin.getPluginConfig().isStatsEnabled()) {
            showMainMenu(player);
            return;
        }
        PlayerStats stats = plugin.getStatsManager().getStats(player.getName());
        String content =
                t(player, "stats.header.solo")
                        + "\n"
                        + t(player, "stats.games-played", stats.getSoloGamesPlayed())
                        + "\n"
                        + t(player, "stats.completed", stats.getSoloGamesCompleted())
                        + "\n"
                        + t(player, "stats.abandoned", stats.getSoloGamesAbandoned())
                        + "\n"
                        + t(player, "stats.questions", stats.getSoloQuestionsAsked())
                        + "\n"
                        + t(player, "stats.hits", stats.getSoloQuestionsHit())
                        + "\n"
                        + t(
                                player,
                                "stats.completion-rate",
                                String.format("%.1f%%", stats.getSoloCompletionRate() * 100))
                        + "\n"
                        + t(player, "stats.current-streak", stats.getSoloCurrentStreak())
                        + "\n"
                        + t(player, "stats.best-streak", stats.getSoloBestStreak())
                        + "\n\n"
                        + t(player, "stats.header.multi")
                        + "\n"
                        + t(player, "stats.games-played", stats.getMultiGamesPlayed())
                        + "\n"
                        + t(player, "stats.completed", stats.getMultiGamesCompleted())
                        + "\n"
                        + t(player, "stats.abandoned", stats.getMultiGamesAbandoned())
                        + "\n"
                        + t(player, "stats.questions", stats.getMultiQuestionsAsked())
                        + "\n"
                        + t(player, "stats.hits", stats.getMultiQuestionsHit())
                        + "\n"
                        + t(
                                player,
                                "stats.completion-rate",
                                String.format("%.1f%%", stats.getMultiCompletionRate() * 100))
                        + "\n\n"
                        + t(player, "stats.header.host")
                        + "\n"
                        + t(player, "stats.host-count", stats.getHostCount());
        showSimple(
                player,
                t(player, "title.my-stats", player.getName()),
                content,
                List.of(button(t(player, "button.back"), this::showMainMenu)));
    }

    private void showCreateRoomMenu(Player player) {
        if (plugin.getPlayerRoom(player) != null) {
            player.sendMessage(t(player, "message.already-in-room"));
            return;
        }
        List<ButtonAction> actions = new ArrayList<>();
        if (plugin.getPluginConfig().isGeneratorEnabled()) {
            actions.add(button(t(player, "button.ai-generate"), this::showDifficultySelectMenu));
        }
        actions.add(button(t(player, "button.manual-create"), this::startManualCreateInput));
        actions.add(button(t(player, "button.back"), this::showMainMenu));
        showSimple(player, t(player, "title.create-room"), t(player, "create.header"), actions);
    }

    private void showDifficultySelectMenu(Player player) {
        showDifficultyMenu(player, false);
    }

    private void showSinglePlayerDifficultyMenu(Player player) {
        if (plugin.getPlayerRoom(player) != null) {
            player.sendMessage(t(player, "message.already-in-room"));
            return;
        }
        showDifficultyMenu(player, true);
    }

    private void showDifficultyMenu(Player player, boolean singlePlayer) {
        PluginConfig cfg = plugin.getPluginConfig();
        List<String> keys = cfg.getDifficultyKeys();
        if (keys.isEmpty()) {
            player.sendMessage(t(player, "message.no-difficulties"));
            return;
        }

        List<ButtonAction> actions = new ArrayList<>();
        for (String key : keys) {
            String label =
                    cfg.getDifficultyStars(key)
                            + " "
                            + cfg.getDifficultyName(key, lang(player))
                            + " - "
                            + cfg.getDifficultyDescription(key, lang(player));
            actions.add(
                    button(
                            label,
                            p -> {
                                if (singlePlayer) {
                                    generateSinglePlayerPuzzle(p, key);
                                } else {
                                    generateMultiplayerPuzzle(p, key);
                                }
                            }));
        }
        actions.add(
                button(
                        t(player, "button.back"),
                        singlePlayer ? this::showMainMenu : this::showCreateRoomMenu));
        showSimple(
                player,
                singlePlayer ? t(player, "title.single-difficulty") : t(player, "title.ai-difficulty"),
                singlePlayer ? t(player, "single.difficulty-header") : t(player, "difficulty.header"),
                actions);
    }

    private void startManualCreateInput(Player player) {
        if (plugin.getPlayerRoom(player) != null) {
            player.sendMessage(t(player, "message.already-in-room"));
            return;
        }
        chatSessions.put(player, new ChatSession(ChatMode.MANUAL_TITLE, null));
        player.sendMessage(t(player, "legacy.manual-title-prompt"));
        player.sendMessage(t(player, "legacy.cancel-hint"));
    }

    private void showRoomListMenu(Player player) {
        List<GameRoom> availableRooms = plugin.getAvailableRooms();
        if (availableRooms.isEmpty()) {
            showSimple(
                    player,
                    t(player, "title.join-room"),
                    t(player, "message.no-rooms"),
                    List.of(button(t(player, "button.back"), this::showMainMenu)));
            return;
        }

        List<ButtonAction> actions = new ArrayList<>();
        for (GameRoom room : availableRooms) {
            actions.add(
                    button(
                            t(player, "room-list.item", room.getRoomId(), room.getAllPlayers().size()),
                            p -> joinRoom(p, room)));
        }
        actions.add(button(t(player, "button.back"), this::showMainMenu));
        showSimple(player, t(player, "title.join-room"), t(player, "field.select-room"), actions);
    }

    private void joinRoom(Player player, GameRoom room) {
        if (plugin.joinRoom(player, room)) {
            room.getAllPlayers()
                    .forEach(
                            rp -> rp.sendMessage(t(rp, "message.player-joined-room", player.getName())));
            showLobbyMenu(player, room);
        } else {
            player.sendMessage(t(player, "message.join-failed"));
            showRoomListMenu(player);
        }
    }

    private void showLobbyMenu(Player player, GameRoom room) {
        if (room.getState() != GameState.WAITING) {
            showContext(player);
            return;
        }

        List<ButtonAction> actions = new ArrayList<>();
        if (room.isHost(player)) {
            actions.add(button(t(player, "button.start-game"), p -> startMultiplayerGame(p, room)));
            actions.add(button(t(player, "button.destroy-room"), p -> plugin.destroyRoom(room)));
        } else {
            actions.add(
                    button(
                            t(player, "button.leave-room"),
                            p -> {
                                plugin.leaveRoom(p);
                                clearPlayer(p);
                                p.sendMessage(t(p, "message.left-room"));
                            }));
        }
        actions.add(button(t(player, "legacy.button.refresh"), p -> showLobbyMenu(p, room)));
        showSimple(
                player,
                t(player, "title.room", room.getRoomId()),
                buildLobbyText(player, room) + "\n" + t(player, "legacy.lobby-chat-hint"),
                actions);
    }

    private void showGameMenu(Player player, GameRoom room) {
        if (room.getState() != GameState.PLAYING) {
            showContext(player);
            return;
        }
        if (room.isHost(player)) {
            showHostGameMenu(player, room);
        } else {
            showGuesserGameMenu(player, room);
        }
    }

    private void showGuesserGameMenu(Player player, GameRoom room) {
        List<ButtonAction> actions = new ArrayList<>();
        actions.add(button(t(player, "legacy.button.refresh"), p -> showGuesserGameMenu(p, room)));
        actions.add(
                button(
                        t(player, "button.leave-room"),
                        p -> {
                            plugin.leaveRoom(p);
                            clearPlayer(p);
                            p.sendMessage(t(p, "message.left-room"));
                        }));
        String content =
                t(player, "message.puzzle-title", room.getPuzzleTitle())
                        + "\n\n"
                        + t(player, "legacy.guesser-chat-hint")
                        + "\n\n"
                        + qaText(player, room);
        showSimple(player, t(player, "title.game-room", room.getRoomId()), content, actions);
    }

    private void showHostGameMenu(Player player, GameRoom room) {
        List<ButtonAction> actions = new ArrayList<>();
        GameRoom.Question current = room.getFirstUnansweredQuestion();
        StringBuilder content = new StringBuilder();
        content.append(t(player, "message.puzzle-title", room.getPuzzleTitle()))
                .append("\n")
                .append(t(player, "label.puzzle-truth-value", room.getPuzzleTruth()))
                .append("\n\n");
        if (current == null) {
            content.append(t(player, "label.no-pending-questions"));
        } else {
            content.append(t(player, "header.pending-questions", room.getUnansweredCount()))
                    .append("\n")
                    .append(t(player, "label.question-from", current.getAskerName(), current.getQuestion()));
            actions.add(button(t(player, "answer.yes"), p -> answerCurrentQuestion(p, room, GameRoom.AnswerType.YES)));
            actions.add(button(t(player, "answer.no"), p -> answerCurrentQuestion(p, room, GameRoom.AnswerType.NO)));
            actions.add(
                    button(
                            t(player, "answer.irrelevant"),
                            p -> answerCurrentQuestion(p, room, GameRoom.AnswerType.IRRELEVANT)));
            if (plugin.getPluginConfig().isAnswererEnabled()) {
                actions.add(button(t(player, "button.ai-answer"), p -> answerCurrentQuestionWithAi(p, room)));
            }
        }
        content.append("\n\n").append(t(player, "legacy.host-chat-hint"));
        String qa = qaText(player, room);
        if (!qa.isBlank()) {
            content.append("\n\n").append(qa);
        }

        actions.add(button(t(player, "button.finish-game"), p -> showFinishConfirmMenu(p, room)));
        actions.add(button(t(player, "legacy.button.refresh"), p -> showHostGameMenu(p, room)));
        showSimple(player, t(player, "title.host-game-room", room.getRoomId()), content.toString(), actions);
    }

    private void showFinishConfirmMenu(Player player, GameRoom room) {
        showSimple(
                player,
                t(player, "title.confirm-finish"),
                t(player, "confirm.finish-body"),
                List.of(
                        button(t(player, "button.confirm-finish"), p -> finishMultiplayerGame(p, room)),
                        button(t(player, "button.continue-game"), p -> showHostGameMenu(p, room))));
    }

    private void showSinglePlayerGameMenu(Player player, GameRoom room) {
        List<ButtonAction> actions = new ArrayList<>();
        actions.add(button(t(player, "legacy.button.refresh"), p -> showSinglePlayerGameMenu(p, room)));
        actions.add(button(t(player, "button.give-up"), p -> showGiveUpConfirmMenu(p, room)));
        String content =
                t(player, "message.puzzle-title", room.getPuzzleTitle())
                        + "\n\n"
                        + t(player, "legacy.single-chat-hint")
                        + "\n\n"
                        + buildAnsweredQAText(room, lang(player));
        showSimple(player, t(player, "title.single-game"), content, actions);
    }

    private void showGiveUpConfirmMenu(Player player, GameRoom room) {
        showSimple(
                player,
                t(player, "title.confirm-give-up"),
                t(player, "confirm.give-up-body"),
                List.of(
                        button(t(player, "button.confirm-give-up"), p -> finishSinglePlayerGame(p, room)),
                        button(t(player, "button.continue-reasoning"), p -> showSinglePlayerGameMenu(p, room))));
    }

    private void showResultMenu(Player player, GameRoom room) {
        String actionLabel =
                room.isHost(player)
                        ? t(player, "button.destroy-room")
                        : t(player, "button.leave-room");
        showSimple(
                player,
                t(player, "title.result", room.getRoomId()),
                t(player, "result.body", room.getPuzzleTitle(), room.getPuzzleTruth()),
                List.of(
                        button(
                                actionLabel,
                                p -> {
                                    if (room.isHost(p)) {
                                        plugin.destroyRoom(room);
                                    } else {
                                        plugin.leaveRoom(p);
                                        clearPlayer(p);
                                        p.sendMessage(t(p, "message.left-room"));
                                    }
                                }),
                        button(t(player, "button.close"), p -> {})));
    }

    private void generateMultiplayerPuzzle(Player player, String difficulty) {
        if (plugin.getPlayerRoom(player) != null) {
            player.sendMessage(t(player, "message.already-in-room"));
            return;
        }
        if (plugin.getPluginConfig().isCacheEnabled()) {
            PuzzleCache.CachedPuzzle cached =
                    plugin.getPuzzleCache().getPuzzleForPlayer(difficulty, player.getName());
            if (cached != null) {
                showAiConfirmMenu(player, cached.title(), cached.truth(), difficulty);
                return;
            }
        }
        generatePuzzle(
                player,
                difficulty,
                result -> showAiConfirmMenu(player, result.getTitle(), result.getTruth(), difficulty),
                () -> showDifficultySelectMenu(player));
    }

    private void generateSinglePlayerPuzzle(Player player, String difficulty) {
        if (plugin.getPlayerRoom(player) != null) {
            player.sendMessage(t(player, "message.already-in-room"));
            return;
        }
        if (plugin.getPluginConfig().isCacheEnabled()) {
            PuzzleCache.CachedPuzzle cached =
                    plugin.getPuzzleCache().getPuzzleForPlayer(difficulty, player.getName());
            if (cached != null) {
                showSinglePlayerConfirmMenu(player, cached.title(), cached.truth(), difficulty);
                return;
            }
        }
        generatePuzzle(
                player,
                difficulty,
                result -> showSinglePlayerConfirmMenu(player, result.getTitle(), result.getTruth(), difficulty),
                () -> showSinglePlayerDifficultyMenu(player));
    }

    private void generatePuzzle(
            Player player,
            String difficulty,
            Consumer<AiService.GenerateResult> onSuccess,
            Runnable onBack) {
        if (!pendingGeneration.add(player)) {
            player.sendMessage(t(player, "message.generation-pending"));
            return;
        }
        player.sendMessage("§e" + t(player, "loading.generating-puzzle") + "...");
        LangCode lang = lang(player);
        String prompt = plugin.getPluginConfig().getDifficultyPrompt(difficulty, lang);
        plugin
                .getAiService()
                .generatePuzzle(prompt, lang)
                .whenComplete(
                        (result, ex) ->
                                plugin
                                        .getServer()
                                        .getScheduler()
                                        .scheduleTask(
                                                plugin,
                                                () -> {
                                                    pendingGeneration.remove(player);
                                                    if (!player.isConnected()) return;
                                                    if (ex != null) {
                                                        plugin.getLogger().error("AI 题目生成调用失败", ex);
                                                        player.sendMessage(
                                                                t(player, "message.ai-call-failed", ex.getMessage()));
                                                        onBack.run();
                                                        return;
                                                    }
                                                    if (!result.isSuccess()) {
                                                        plugin.getLogger().error("AI 题目生成失败: " + result.getError());
                                                        player.sendMessage(
                                                                t(
                                                                        player,
                                                                        "message.puzzle-generation-failed",
                                                                        result.getError()));
                                                        onBack.run();
                                                        return;
                                                    }
                                                    if (plugin.getPluginConfig().isCacheEnabled()) {
                                                        plugin
                                                                .getPuzzleCache()
                                                                .addPuzzle(
                                                                        difficulty,
                                                                        result.getTitle(),
                                                                        result.getTruth());
                                                        plugin.getPuzzleCache().markAsSeen(player.getName(), result.getTitle());
                                                        plugin.getPuzzleCache().save();
                                                    }
                                                    onSuccess.accept(result);
                                                }));
    }

    private void showAiConfirmMenu(Player player, String title, String truth, String difficulty) {
        showSimple(
                player,
                t(player, "title.ai-generated"),
                t(player, "label.puzzle-title-value", title)
                        + "\n"
                        + t(player, "label.puzzle-truth-value", truth),
                List.of(
                        button(
                                t(player, "button.confirm-create"),
                                p -> {
                                    GameRoom room = plugin.createRoom(p, title, truth);
                                    if (room == null) {
                                        p.sendMessage(t(p, "message.create-failed"));
                                        return;
                                    }
                                    p.sendMessage(t(p, "message.room-created"));
                                    showLobbyMenu(p, room);
                                }),
                        button(t(player, "button.regenerate"), p -> generateMultiplayerPuzzle(p, difficulty)),
                        button(t(player, "button.back"), this::showCreateRoomMenu)));
    }

    private void showSinglePlayerConfirmMenu(Player player, String title, String truth, String difficulty) {
        showSimple(
                player,
                t(player, "title.single-ai-generate"),
                t(player, "label.puzzle-title-value", title),
                List.of(
                        button(
                                t(player, "button.start-reasoning"),
                                p -> {
                                    GameRoom room = plugin.createSinglePlayerRoom(p, title, truth);
                                    if (room == null) {
                                        p.sendMessage(t(p, "message.create-failed"));
                                        return;
                                    }
                                    p.sendMessage("§a=============================");
                                    p.sendMessage(t(p, "message.single-started"));
                                    p.sendMessage("§a=============================");
                                    p.sendMessage(t(p, "message.puzzle-title", title));
                                    p.sendMessage(t(p, "legacy.single-chat-hint"));
                                    showSinglePlayerGameMenu(p, room);
                                }),
                        button(t(player, "button.regenerate"), p -> generateSinglePlayerPuzzle(p, difficulty)),
                        button(t(player, "button.back"), this::showMainMenu)));
    }

    private void startMultiplayerGame(Player player, GameRoom room) {
        if (!room.isHost(player) || room.getState() != GameState.WAITING) return;
        room.startGame();
        if (plugin.getPluginConfig().isStatsEnabled()) {
            for (Player rp : room.getAllPlayers()) {
                plugin.getStatsManager().getStats(rp.getName()).recordMultiGamePlayed();
            }
            plugin.getStatsManager().markDirty();
        }
        for (Player rp : room.getAllPlayers()) {
            rp.sendMessage("§a=============================");
            rp.sendMessage(t(rp, "message.game-started"));
            rp.sendMessage("§a=============================");
            rp.sendMessage(t(rp, "message.puzzle-title", room.getPuzzleTitle()));
            if (UIFactory.supportsDdui(rp)) {
                rp.sendMessage(t(rp, "message.open-ui"));
                UIFactory.showGameForm(plugin, rp);
            } else {
                rp.sendMessage(t(rp, room.isHost(rp) ? "legacy.host-chat-hint" : "legacy.guesser-chat-hint"));
                showGameMenu(rp, room);
            }
        }
    }

    private void finishMultiplayerGame(Player player, GameRoom room) {
        if (!room.isHost(player) || room.getState() != GameState.PLAYING) return;
        room.finishGame();
        if (plugin.getPluginConfig().isStatsEnabled()) {
            for (Player rp : room.getAllPlayers()) {
                plugin.getStatsManager().getStats(rp.getName()).recordMultiGameCompleted();
            }
            plugin.getStatsManager().markDirty();
        }
        for (Player rp : room.getAllPlayers()) {
            rp.sendMessage("§a=============================");
            rp.sendMessage(t(rp, "message.game-ended"));
            rp.sendMessage("§a=============================");
            rp.sendMessage(t(rp, "message.puzzle-truth", room.getPuzzleTruth()));
            if (UIFactory.supportsDdui(rp)) {
                UIFactory.showResultForm(plugin, rp, room);
            } else {
                showResultMenu(rp, room);
            }
        }
    }

    private void finishSinglePlayerGame(Player player, GameRoom room) {
        if (!room.isSinglePlayer() || room.getState() != GameState.PLAYING) return;
        room.finishGame();
        if (plugin.getPluginConfig().isStatsEnabled()) {
            plugin.getStatsManager().getStats(player.getName()).recordSoloGameCompleted();
            plugin.getStatsManager().markDirty();
        }
        player.sendMessage("§a=============================");
        player.sendMessage(t(player, "message.game-ended"));
        player.sendMessage("§a=============================");
        player.sendMessage(t(player, "message.puzzle-truth", room.getPuzzleTruth()));
        showResultMenu(player, room);
    }

    private void answerCurrentQuestion(Player player, GameRoom room, GameRoom.AnswerType answerType) {
        if (!room.isHost(player) || room.getState() != GameState.PLAYING) return;
        GameRoom.Question current = room.getFirstUnansweredQuestion();
        if (current == null) {
            player.sendMessage(t(player, "label.no-pending-questions"));
            showHostGameMenu(player, room);
            return;
        }
        room.answerQuestion(current, answerType);
        plugin.recordQuestionAnswered(room, current);
        notifyAnswer(room, current);
        showHostGameMenu(player, room);
    }

    private void answerCurrentQuestionWithAi(Player player, GameRoom room) {
        if (!room.isHost(player) || room.getState() != GameState.PLAYING) return;
        GameRoom.Question current = room.getFirstUnansweredQuestion();
        if (current == null) {
            player.sendMessage(t(player, "label.no-pending-questions"));
            return;
        }
        if (!plugin.getPluginConfig().isAnswererEnabled()) {
            player.sendMessage(t(player, "message.answerer-not-configured"));
            return;
        }
        if (!pendingAiAnswer.add(player)) {
            player.sendMessage(t(player, "legacy.ai-pending"));
            return;
        }
        player.sendMessage("§e" + t(player, "loading.ai-thinking") + "...");
        plugin
                .getAiService()
                .answerQuestion(room.getPuzzleTruth(), current.getQuestion(), lang(player))
                .whenComplete(
                        (answer, ex) ->
                                plugin
                                        .getServer()
                                        .getScheduler()
                                        .scheduleTask(
                                                plugin,
                                                () -> {
                                                    pendingAiAnswer.remove(player);
                                                    if (!isActivePlayingRoom(player, room)) return;
                                                    if (ex != null) {
                                                        plugin.getLogger().error("AI 回答失败(多人聊天模式)", ex);
                                                        player.sendMessage(
                                                                t(player, "message.ai-answer-failed", ex.getMessage()));
                                                        showHostGameMenu(player, room);
                                                        return;
                                                    }
                                                    if (!current.isAnswered()) {
                                                        room.answerQuestion(current, answer);
                                                        plugin.recordQuestionAnswered(room, current);
                                                        notifyAnswer(room, current);
                                                    }
                                                    showHostGameMenu(player, room);
                                                }));
    }

    @EventHandler
    public void onFormResponded(PlayerFormRespondedEvent event) {
        Map<Integer, Consumer<FormResponseSimple>> playerHandlers = formHandlers.get(event.getPlayer());
        if (playerHandlers == null) return;
        Consumer<FormResponseSimple> handler = playerHandlers.remove(event.getFormID());
        if (playerHandlers.isEmpty()) {
            formHandlers.remove(event.getPlayer());
        }
        if (handler == null || event.wasClosed()) return;
        FormResponse response = event.getResponse();
        if (response instanceof FormResponseSimple simple) {
            handler.accept(simple);
        }
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (UIFactory.supportsDdui(player)) return;
        String message = event.getMessage();
        if (message == null) return;
        if (!isManagingChat(player)) return;
        if (message.startsWith("!")) {
            if (message.length() == 1) {
                event.setCancelled(true);
                return;
            }
            event.setMessage(message.substring(1));
            return;
        }
        if (handleChatInput(player, message.trim())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayer(event.getPlayer());
    }

    private boolean handleChatInput(Player player, String message) {
        ChatSession session = chatSessions.get(player);
        if (session != null) {
            handleChatSession(player, message, session);
            return true;
        }

        if (message.isBlank()) return false;
        GameRoom room = plugin.getPlayerRoom(player);
        if (room == null) return false;
        if (room.getState() == GameState.WAITING) {
            return handleWaitingChat(player, room, message);
        }
        if (room.getState() == GameState.FINISHED) {
            if (isLeave(message)) {
                if (room.isHost(player)) {
                    plugin.destroyRoom(room);
                } else {
                    plugin.leaveRoom(player);
                    clearPlayer(player);
                    player.sendMessage(t(player, "message.left-room"));
                }
                return true;
            }
            return false;
        }
        if (room.isSinglePlayer()) {
            return handleSinglePlayerChat(player, room, message);
        }
        if (room.isHost(player)) {
            return handleHostChat(player, room, message);
        }
        return handleGuesserChat(player, room, message);
    }

    private boolean isManagingChat(Player player) {
        return chatSessions.containsKey(player) || plugin.getPlayerRoom(player) != null;
    }

    private void handleChatSession(Player player, String message, ChatSession session) {
        if (isCancel(message)) {
            chatSessions.remove(player);
            player.sendMessage(t(player, "legacy.input-cancelled"));
            showCreateRoomMenu(player);
            return;
        }
        if (session.mode() == ChatMode.MANUAL_TITLE) {
            if (message.isBlank()) {
                player.sendMessage(t(player, "message.title-empty"));
                return;
            }
            chatSessions.put(player, new ChatSession(ChatMode.MANUAL_TRUTH, message));
            player.sendMessage(t(player, "legacy.manual-truth-prompt"));
            player.sendMessage(t(player, "legacy.cancel-hint"));
            return;
        }
        if (session.mode() == ChatMode.MANUAL_TRUTH) {
            if (message.isBlank()) {
                player.sendMessage(t(player, "message.truth-empty"));
                return;
            }
            chatSessions.remove(player);
            GameRoom room = plugin.createRoom(player, session.title(), message);
            if (room == null) {
                player.sendMessage(t(player, "message.create-failed"));
                showCreateRoomMenu(player);
                return;
            }
            player.sendMessage(t(player, "message.room-created"));
            showLobbyMenu(player, room);
        }
    }

    private boolean handleWaitingChat(Player player, GameRoom room, String message) {
        if (isLeave(message)) {
            if (room.isHost(player)) {
                plugin.destroyRoom(room);
            } else {
                plugin.leaveRoom(player);
                clearPlayer(player);
                player.sendMessage(t(player, "message.left-room"));
            }
            return true;
        }
        if (room.isHost(player) && isStart(message)) {
            startMultiplayerGame(player, room);
            return true;
        }
        if (isRefresh(message)) {
            showLobbyMenu(player, room);
            return true;
        }
        return false;
    }

    private boolean handleGuesserChat(Player player, GameRoom room, String message) {
        if (isLeave(message)) {
            plugin.leaveRoom(player);
            clearPlayer(player);
            player.sendMessage(t(player, "message.left-room"));
            return true;
        }
        if (isRefresh(message) || isQa(message)) {
            showGuesserGameMenu(player, room);
            return true;
        }
        GameRoom.Question q = room.askQuestion(player, message);
        if (plugin.getPluginConfig().isStatsEnabled()) {
            plugin.getStatsManager().getStats(player.getName()).recordMultiQuestion();
            plugin.getStatsManager().markDirty();
        }
        room.getHost().sendMessage(t(room.getHost(), "message.host-question", player.getName(), message));
        player.sendMessage(t(player, "message.question-submitted"));
        if (!UIFactory.supportsDdui(room.getHost())) {
            showHostGameMenu(room.getHost(), room);
        }
        return q != null;
    }

    private boolean handleHostChat(Player player, GameRoom room, String message) {
        if (isFinish(message)) {
            showFinishConfirmMenu(player, room);
            return true;
        }
        if (isRefresh(message) || isQa(message)) {
            showHostGameMenu(player, room);
            return true;
        }
        GameRoom.AnswerType answer = parseAnswer(message);
        if (answer != null) {
            answerCurrentQuestion(player, room, answer);
            return true;
        }
        if (isAi(message)) {
            answerCurrentQuestionWithAi(player, room);
            return true;
        }
        return false;
    }

    private boolean handleSinglePlayerChat(Player player, GameRoom room, String message) {
        if (isFinish(message)) {
            showGiveUpConfirmMenu(player, room);
            return true;
        }
        if (isRefresh(message) || isQa(message)) {
            showSinglePlayerGameMenu(player, room);
            return true;
        }
        if (!plugin.getPluginConfig().isAnswererEnabled()) {
            player.sendMessage(t(player, "message.answerer-not-configured"));
            return true;
        }
        if (!pendingAiAnswer.add(player)) {
            player.sendMessage(t(player, "legacy.ai-pending"));
            return true;
        }
        GameRoom.Question q = room.askQuestion(player, message);
        if (plugin.getPluginConfig().isStatsEnabled()) {
            plugin.getStatsManager().getStats(player.getName()).recordSoloQuestion();
            plugin.getStatsManager().markDirty();
        }
        player.sendMessage(t(player, "label.you-question", message) + "§e" + t(player, "loading.ai-thinking") + "...");
        plugin
                .getAiService()
                .answerQuestion(room.getPuzzleTruth(), message, lang(player))
                .whenComplete(
                        (answer, ex) ->
                                plugin
                                        .getServer()
                                        .getScheduler()
                                        .scheduleTask(
                                                plugin,
                                                () -> {
                                                    pendingAiAnswer.remove(player);
                                                    if (!isActivePlayingRoom(player, room)) return;
                                                    if (ex != null) {
                                                        plugin.getLogger().error("AI 回答失败(单人聊天模式)", ex);
                                                        player.sendMessage(
                                                                t(player, "message.ai-answer-failed", ex.getMessage()));
                                                        return;
                                                    }
                                                    room.answerQuestion(q, answer);
                                                    plugin.recordQuestionAnswered(room, q);
                                                    player.sendMessage(
                                                            t(
                                                                    player,
                                                                    "message.answer-notify",
                                                                    q.getAskerName(),
                                                                    q.getQuestion(),
                                                                    Texts.answerLabel(answer, lang(player))));
                                                    showSinglePlayerGameMenu(player, room);
                                                }));
        return true;
    }

    private boolean isActivePlayingRoom(Player player, GameRoom room) {
        return player.isConnected()
                && plugin.getPlayerRoom(player) == room
                && room.getState() == GameState.PLAYING;
    }

    private void notifyAnswer(GameRoom room, GameRoom.Question question) {
        for (Player p : room.getAllPlayers()) {
            p.sendMessage(
                    t(
                            p,
                            "message.answer-notify",
                            question.getAskerName(),
                            question.getQuestion(),
                            Texts.answerLabel(question.getAnswerType(), lang(p))));
        }
    }

    private void showSimple(Player player, String title, String content, List<ButtonAction> actions) {
        FormWindowSimple form = new FormWindowSimple(title, content == null ? "" : content);
        for (ButtonAction action : actions) {
            form.addButton(new ElementButton(action.label()));
        }
        int formId = player.showFormWindow(form);
        formHandlers
                .computeIfAbsent(player, p -> new java.util.HashMap<>())
                .put(
                        formId,
                        response -> {
                            int index = response.getClickedButtonId();
                            if (index < 0 || index >= actions.size()) {
                                player.sendMessage(t(player, "message.invalid-selection"));
                                return;
                            }
                            actions.get(index).action().accept(player);
                        });
    }

    private ButtonAction button(String label, Consumer<Player> action) {
        return new ButtonAction(label, action);
    }

    private String buildLobbyText(Player player, GameRoom room) {
        StringBuilder playerList = new StringBuilder(t(player, "lobby.player-list-header"));
        playerList
                .append("§6★ §f")
                .append(room.getHost().getName())
                .append(" ")
                .append(t(player, "lobby.host-suffix"))
                .append("\n");
        for (Player p : room.getPlayers()) {
            playerList.append("§f  ").append(p.getName()).append("\n");
        }
        return playerList.toString();
    }

    private String qaText(Player player, GameRoom room) {
        String text = room.buildQAText(lang(player));
        return text.isBlank() ? t(player, "label.no-data") : text;
    }

    private String buildAnsweredQAText(GameRoom room, LangCode lang) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (GameRoom.Question q : room.getQuestions()) {
            if (!q.isAnswered()) continue;
            count++;
            sb.append("§b").append(q.getAskerName()).append("§f：").append(q.getQuestion()).append("\n");
            sb.append("  §7→ ").append(Texts.answerLabel(q.getAnswerType(), lang)).append("\n");
        }
        if (count == 0) return t(lang, "label.no-data");
        return t(lang, "qa.answered-header", count) + sb;
    }

    private String metricTitle(Player player, String metric) {
        return switch (metric) {
            case "solo_completed" -> t(player, "metric.solo-completed");
            case "multi_completed" -> t(player, "metric.multi-completed");
            case "total_games" -> t(player, "metric.total-games");
            case "questions" -> t(player, "metric.questions");
            case "hit_rate" -> t(player, "metric.hit-rate");
            case "host" -> t(player, "metric.host");
            case "solo_streak" -> t(player, "metric.solo-streak");
            default -> metric;
        };
    }

    private String formatMetricValue(String metric, PlayerStats s) {
        return switch (metric) {
            case "solo_completed" -> String.valueOf(s.getSoloGamesCompleted());
            case "multi_completed" -> String.valueOf(s.getMultiGamesCompleted());
            case "total_games" -> String.valueOf(s.getTotalGamesPlayed());
            case "questions" -> String.valueOf(s.getTotalQuestionsAsked());
            case "hit_rate" -> String.format("%.1f%%", s.getHitRate() * 100);
            case "host" -> String.valueOf(s.getHostCount());
            case "solo_streak" -> String.valueOf(s.getSoloBestStreak());
            default -> "-";
        };
    }

    private GameRoom.AnswerType parseAnswer(String message) {
        String normalized = normalize(message);
        if (Set.of("是", "yes", "y", "1").contains(normalized)) {
            return GameRoom.AnswerType.YES;
        }
        if (Set.of("不是", "否", "no", "n", "0").contains(normalized)) {
            return GameRoom.AnswerType.NO;
        }
        if (Set.of("无关", "不相关", "irrelevant", "unrelated", "i").contains(normalized)) {
            return GameRoom.AnswerType.IRRELEVANT;
        }
        return null;
    }

    private boolean isStart(String message) {
        return Set.of("开始", "start", "go").contains(normalize(message));
    }

    private boolean isFinish(String message) {
        return Set.of("结束", "真相", "放弃", "end", "truth", "giveup", "give-up").contains(normalize(message));
    }

    private boolean isLeave(String message) {
        return Set.of("离开", "退出", "leave", "quit", "exit").contains(normalize(message));
    }

    private boolean isCancel(String message) {
        return Set.of("取消", "cancel").contains(normalize(message));
    }

    private boolean isRefresh(String message) {
        return Set.of("刷新", "菜单", "menu", "refresh").contains(normalize(message));
    }

    private boolean isQa(String message) {
        return Set.of("记录", "qa", "log").contains(normalize(message));
    }

    private boolean isAi(String message) {
        return "ai".equals(normalize(message));
    }

    private String normalize(String message) {
        return message.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private LangCode lang(Player player) {
        return Texts.lang(player);
    }

    private String t(Player player, String key, Object... args) {
        return Texts.t(plugin, player, key, args);
    }

    private String t(LangCode lang, String key, Object... args) {
        return Texts.t(plugin, lang, key, args);
    }
}
