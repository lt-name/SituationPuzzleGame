package cn.lanink.situationpuzzlegame.ui;

import cn.lanink.situationpuzzlegame.SituationPuzzleGame;
import cn.lanink.situationpuzzlegame.cache.PuzzleCache;
import cn.lanink.situationpuzzlegame.config.PluginConfig;
import cn.lanink.situationpuzzlegame.game.GameRoom;
import cn.lanink.situationpuzzlegame.game.GameState;
import cn.lanink.situationpuzzlegame.i18n.Texts;
import cn.lanink.situationpuzzlegame.stats.PlayerStats;
import cn.lanink.situationpuzzlegame.stats.StatsManager;
import cn.nukkit.Player;
import cn.nukkit.ddui.CustomForm;
import cn.nukkit.ddui.MessageBox;
import cn.nukkit.ddui.Observable;
import cn.nukkit.ddui.ObservableOptions;
import cn.nukkit.ddui.element.DropdownElement;
import cn.nukkit.lang.LangCode;
import cn.nukkit.network.protocol.ProtocolInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UIFactory {

    private static final int MIN_DDUI_PROTOCOL = ProtocolInfo.v1_26_0;
    private static final Set<Player> pendingGeneration =
            Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private static LangCode lang(Player player) {
        return Texts.lang(player);
    }

    private static String t(SituationPuzzleGame plugin, Player player, String key, Object... args) {
        return Texts.t(plugin, player, key, args);
    }

    private static String t(SituationPuzzleGame plugin, GameRoom room, String key, Object... args) {
        return Texts.t(plugin, room, key, args);
    }

    private static void sendToRoom(
            SituationPuzzleGame plugin, GameRoom room, String key, Object... args) {
        for (Player p : room.getAllPlayers()) {
            if (p.isConnected()) {
                p.sendMessage(t(plugin, p, key, args));
            }
        }
    }

    public static boolean supportsDdui(Player player) {
        return player.protocol >= MIN_DDUI_PROTOCOL;
    }

    private static boolean checkDduiSupport(SituationPuzzleGame plugin, Player player) {
        if (supportsDdui(player)) return true;
        player.sendMessage(t(plugin, player, "message.client-too-old"));
        player.sendMessage(t(plugin, player, "message.client-upgrade"));
        return false;
    }

    // ==================== 主菜单 ====================

    public static void showMainMenu(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(plugin, player)) return;
        CustomForm form = new CustomForm(t(plugin, player, "title.app"));
        form.header(t(plugin, player, "main.header"));
        form.label(t(plugin, player, "main.description"));
        form.divider();

        PluginConfig cfg = plugin.getPluginConfig();
        if (cfg.isGeneratorEnabled() && cfg.isAnswererEnabled()) {
            form.button(
                    t(plugin, player, "button.single-player"),
                    p -> showSinglePlayerDifficultyForm(plugin, p));
        }
        form.button(t(plugin, player, "button.create-room"), p -> showCreateRoomMenu(plugin, p));
        form.button(t(plugin, player, "button.join-room"), p -> showRoomListForm(plugin, p));
        if (cfg.isStatsEnabled()) {
            form.divider();
            form.button(t(plugin, player, "button.leaderboard"), p -> showLeaderboardMenu(plugin, p));
            form.button(t(plugin, player, "button.my-stats"), p -> showPersonalStats(plugin, p));
        }
        form.show(player);
    }

    // ==================== 创建房间 ====================

    private static void showCreateRoomMenu(SituationPuzzleGame plugin, Player player) {
        if (plugin.getPlayerRoom(player) != null) {
            player.sendMessage(t(plugin, player, "message.already-in-room"));
            return;
        }

        CustomForm form = new CustomForm(t(plugin, player, "title.create-room"));
        form.header(t(plugin, player, "create.header"));

        if (plugin.getPluginConfig().isGeneratorEnabled()) {
            form.button(
                    t(plugin, player, "button.ai-generate"), p -> showDifficultySelectForm(plugin, p));
        }
        form.button(t(plugin, player, "button.manual-create"), p -> showManualCreateForm(plugin, p));
        form.button(t(plugin, player, "button.back"), p -> showMainMenu(plugin, p));
        form.show(player);
    }

    // ==================== AI 出题（创建房间子流程） ====================

    private static void showDifficultySelectForm(SituationPuzzleGame plugin, Player player) {
        PluginConfig cfg = plugin.getPluginConfig();
        List<String> keys = cfg.getDifficultyKeys();

        if (keys.isEmpty()) {
            player.sendMessage(t(plugin, player, "message.no-difficulties"));
            return;
        }

        CustomForm form = new CustomForm(t(plugin, player, "title.ai-difficulty"));
        form.header(t(plugin, player, "difficulty.header"));

        List<DropdownElement.Item> items = new ArrayList<>();
        for (String key : keys) {
            String label =
                    cfg.getDifficultyStars(key)
                            + " "
                            + cfg.getDifficultyName(key, lang(player))
                            + " - "
                            + cfg.getDifficultyDescription(key, lang(player));
            items.add(DropdownElement.Item.builder().label(label).build());
        }

        int defaultIndex = Math.max(0, keys.indexOf("normal"));
        Observable<Long> selected = new Observable<>((long) defaultIndex);
        form.dropdown(t(plugin, player, "label.difficulty"), items, selected);
        form.divider();
        form.button(
                t(plugin, player, "button.generate-puzzle"),
                p -> {
                    int index = (int) (long) selected.getValue();
                    if (index < 0 || index >= keys.size()) {
                        p.sendMessage(t(plugin, p, "message.invalid-selection"));
                        return;
                    }
                    String difficulty = keys.get(index);
                    generateAndShow(plugin, p, difficulty);
                });
        form.button(t(plugin, player, "button.back"), p -> showCreateRoomMenu(plugin, p));
        form.show(player);
    }

    private static void generateAndShow(
            SituationPuzzleGame plugin, Player player, String difficulty) {
        if (plugin.getPluginConfig().isCacheEnabled()) {
            PuzzleCache.CachedPuzzle cached =
                    plugin.getPuzzleCache().getPuzzleForPlayer(difficulty, player.getName());
            if (cached != null) {
                showAiConfirmForm(plugin, player, cached.title(), cached.truth(), difficulty);
                return;
            }
        }

        if (!pendingGeneration.add(player)) {
            player.sendMessage(t(plugin, player, "message.generation-pending"));
            return;
        }
        LangCode lang = lang(player);
        String prompt = plugin.getPluginConfig().getDifficultyPrompt(difficulty, lang);
        CustomForm loadingForm = new CustomForm(t(plugin, player, "title.ai-generate"));
        Observable<String> status = new Observable<>("");
        loadingForm.label(status);
        loadingForm.show(player);

        cn.nukkit.scheduler.TaskHandler animTask =
                startLoadingAnimation(plugin, status, t(plugin, player, "loading.generating-puzzle"));

        plugin
                .getAiService()
                .generatePuzzle(prompt, lang)
                .whenComplete(
                        (result, ex) -> {
                            plugin
                                    .getServer()
                                    .getScheduler()
                                    .scheduleTask(
                                            plugin,
                                            () -> {
                                                animTask.cancel();
                                                pendingGeneration.remove(player);
                                                if (!player.isConnected()) return;
                                                if (ex != null) {
                                                    plugin.getLogger().error("AI 题目生成调用失败", ex);
                                                    showErrorForm(
                                                            plugin,
                                                            player,
                                                            t(plugin, player, "message.ai-call-failed", ex.getMessage()),
                                                            () -> showDifficultySelectForm(plugin, player));
                                                } else if (result.isSuccess()) {
                                                    if (plugin.getPluginConfig().isCacheEnabled()) {
                                                        plugin
                                                                .getPuzzleCache()
                                                                .addPuzzle(difficulty, result.getTitle(), result.getTruth());
                                                        plugin.getPuzzleCache().markAsSeen(player.getName(), result.getTitle());
                                                        plugin.getPuzzleCache().save();
                                                    }
                                                    showAiConfirmForm(
                                                            plugin, player, result.getTitle(), result.getTruth(), difficulty);
                                                } else {
                                                    plugin.getLogger().error("AI 题目生成失败: " + result.getError());
                                                    showErrorForm(
                                                            plugin,
                                                            player,
                                                            t(
                                                                    plugin,
                                                                    player,
                                                                    "message.puzzle-generation-failed",
                                                                    result.getError()),
                                                            () -> showDifficultySelectForm(plugin, player));
                                                }
                                            });
                        });
    }

    // ==================== 加入房间 ====================

    private static void showAiConfirmForm(
            SituationPuzzleGame plugin, Player player, String title, String truth, String difficulty) {
        CustomForm form = new CustomForm(t(plugin, player, "title.ai-generated"));
        form.header(t(plugin, player, "ai-confirm.header"));
        form.label(t(plugin, player, "label.puzzle-title-value", title));
        form.label(t(plugin, player, "label.puzzle-truth-value", truth));
        form.divider();

        form.button(
                t(plugin, player, "button.confirm-create"),
                p -> {
                    GameRoom room = plugin.createRoom(p, title, truth);
                    if (room != null) {
                        p.sendMessage(t(plugin, p, "message.room-created"));
                        showLobbyForm(plugin, p);
                    } else {
                        p.sendMessage(t(plugin, p, "message.create-failed"));
                    }
                });

        form.button(
                t(plugin, player, "button.regenerate"),
                p -> {
                    generateAndShow(plugin, p, difficulty);
                });
        form.button(t(plugin, player, "button.back"), p -> showCreateRoomMenu(plugin, p));
        form.show(player);
    }

    // ==================== 手动出题 ====================

    private static void showManualCreateForm(SituationPuzzleGame plugin, Player player) {
        if (plugin.getPlayerRoom(player) != null) {
            player.sendMessage(t(plugin, player, "message.already-in-room"));
            return;
        }

        CustomForm form = new CustomForm(t(plugin, player, "title.create-room"));
        Observable<String> titleObs =
                new Observable<>("", ObservableOptions.builder().clientWritable(true).build());
        Observable<String> truthObs =
                new Observable<>("", ObservableOptions.builder().clientWritable(true).build());

        form.label(t(plugin, player, "manual.label"));
        form.textField(t(plugin, player, "field.puzzle-title"), titleObs);
        form.textField(t(plugin, player, "field.puzzle-truth"), truthObs);
        form.divider();
        form.button(
                t(plugin, player, "button.confirm-create"),
                p -> {
                    String title = titleObs.getValue();
                    String truth = truthObs.getValue();
                    if (title == null || title.isBlank()) {
                        p.sendMessage(t(plugin, p, "message.title-empty"));
                        return;
                    }
                    if (truth == null || truth.isBlank()) {
                        p.sendMessage(t(plugin, p, "message.truth-empty"));
                        return;
                    }
                    GameRoom room = plugin.createRoom(p, title, truth);
                    if (room == null) {
                        p.sendMessage(t(plugin, p, "message.create-failed"));
                        return;
                    }
                    p.sendMessage(t(plugin, p, "message.room-created"));
                    showLobbyForm(plugin, p);
                });
        form.button(t(plugin, player, "button.back"), p -> showCreateRoomMenu(plugin, p));
        form.show(player);
    }

    // ==================== 加入房间 ====================

    public static void showRoomListForm(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(plugin, player)) return;
        List<GameRoom> availableRooms = plugin.getAvailableRooms();
        if (availableRooms.isEmpty()) {
            MessageBox msg = new MessageBox(t(plugin, player, "title.join-room"));
            msg.body(t(plugin, player, "message.no-rooms"));
            msg.button1(t(plugin, player, "button.back"), p -> showMainMenu(plugin, p));
            msg.button2(t(plugin, player, "button.back"), p -> showMainMenu(plugin, p));
            msg.show(player);
            return;
        }

        CustomForm form = new CustomForm(t(plugin, player, "title.join-room"));
        List<DropdownElement.Item> items = new ArrayList<>();
        for (GameRoom room : availableRooms) {
            items.add(
                    DropdownElement.Item.builder()
                            .label(
                                    t(
                                            plugin,
                                            player,
                                            "room-list.item",
                                            room.getRoomId(),
                                            room.getAllPlayers().size()))
                            .build());
        }

        Observable<Long> selected = new Observable<>(0L);
        form.dropdown(t(plugin, player, "field.select-room"), items, selected);
        form.divider();
        form.button(
                t(plugin, player, "button.join"),
                p -> {
                    int index = (int) (long) selected.getValue();
                    if (index < 0 || index >= availableRooms.size()) {
                        p.sendMessage(t(plugin, p, "message.invalid-selection"));
                        return;
                    }
                    GameRoom room = availableRooms.get(index);
                    if (plugin.joinRoom(p, room)) {
                        room.getAllPlayers()
                                .forEach(
                                        rp -> rp.sendMessage(t(plugin, rp, "message.player-joined-room", p.getName())));
                        showLobbyForm(plugin, p);
                    } else {
                        p.sendMessage(t(plugin, p, "message.join-failed"));
                    }
                });
        form.show(player);
    }

    // ==================== 大厅 ====================

    public static void showLobbyForm(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(plugin, player)) return;
        GameRoom room = plugin.getPlayerRoom(player);
        if (room == null) return;

        CustomForm form = new CustomForm(t(plugin, player, "title.room", room.getRoomId()));

        StringBuilder playerList = new StringBuilder(t(plugin, player, "lobby.player-list-header"));
        playerList
                .append("§6★ §f")
                .append(room.getHost().getName())
                .append(" ")
                .append(t(plugin, player, "lobby.host-suffix"))
                .append("\n");
        for (Player p : room.getPlayers()) {
            playerList.append("§f  ").append(p.getName()).append("\n");
        }
        form.label(playerList.toString());

        if (room.isHost(player)) {
            form.button(
                    t(plugin, player, "button.start-game"),
                    p -> {
                        if (room.getState() != GameState.WAITING) return;
                        room.startGame();
                        if (plugin.getPluginConfig().isStatsEnabled()) {
                            for (Player rp : room.getAllPlayers()) {
                                plugin.getStatsManager().getStats(rp.getName()).recordMultiGamePlayed();
                            }
                            plugin.getStatsManager().markDirty();
                        }
                        for (Player rp : room.getAllPlayers()) {
                            rp.sendMessage("§a=============================");
                            rp.sendMessage(t(plugin, rp, "message.game-started"));
                            rp.sendMessage("§a=============================");
                            rp.sendMessage(t(plugin, rp, "message.puzzle-title", room.getPuzzleTitle()));
                            if (supportsDdui(rp)) {
                                rp.sendMessage(t(plugin, rp, "message.open-ui"));
                                showGameForm(plugin, rp);
                            } else {
                                plugin.getLegacyUIFactory().showContext(rp);
                            }
                        }
                    });
            form.divider();
            form.button(t(plugin, player, "button.destroy-room"), p -> plugin.destroyRoom(room));
        } else {
            form.label(t(plugin, player, "lobby.waiting-host"));
            form.divider();
            form.button(
                    t(plugin, player, "button.leave-room"),
                    p -> {
                        plugin.leaveRoom(p);
                        p.sendMessage(t(plugin, p, "message.left-room"));
                    });
        }

        form.show(player);
    }

    // ==================== 上下文分发 ====================

    public static void showContextForm(SituationPuzzleGame plugin, Player player, GameRoom room) {
        if (!checkDduiSupport(plugin, player)) return;
        if (room.isSinglePlayer()) {
            switch (room.getState()) {
                case PLAYING -> showSinglePlayerGameForm(plugin, player);
                case FINISHED -> showResultForm(plugin, player, room);
            }
            return;
        }
        switch (room.getState()) {
            case WAITING -> showLobbyForm(plugin, player);
            case PLAYING -> showGameForm(plugin, player);
            case FINISHED -> showResultForm(plugin, player, room);
        }
    }

    // ==================== 游戏界面 ====================

    public static void showGameForm(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(plugin, player)) return;
        GameRoom room = plugin.getPlayerRoom(player);
        if (room == null || room.getState() != GameState.PLAYING) return;

        if (room.isHost(player)) {
            showHostGameForm(plugin, player, room);
        } else {
            showGuesserGameForm(plugin, player, room);
        }
    }

    private static void showGuesserGameForm(
            SituationPuzzleGame plugin, Player player, GameRoom room) {
        CustomForm form = new CustomForm(t(plugin, player, "title.game-room", room.getRoomId()));

        form.header(t(plugin, player, "header.puzzle-title"));
        form.label("§f" + room.getPuzzleTitle());
        form.divider();

        Observable<String> qaObs = room.getQaObservable(lang(player));
        qaObs.setValue(room.buildQAText(lang(player)));
        form.header(t(plugin, player, "header.qa-record"));
        form.label(qaObs);
        form.divider();

        Observable<String> questionObs =
                new Observable<>("", ObservableOptions.builder().clientWritable(true).build());
        form.textField(t(plugin, player, "field.question"), questionObs);
        form.button(
                t(plugin, player, "button.submit-question"),
                p -> {
                    String question = questionObs.getValue();
                    if (question == null || question.isBlank()) {
                        p.sendMessage(t(plugin, p, "message.question-empty"));
                        return;
                    }
                    room.askQuestion(p, question);
                    if (plugin.getPluginConfig().isStatsEnabled()) {
                        plugin.getStatsManager().getStats(p.getName()).recordMultiQuestion();
                        plugin.getStatsManager().markDirty();
                    }
                    room.getHost()
                            .sendMessage(
                                    t(plugin, room.getHost(), "message.host-question", p.getName(), question));
                    p.sendMessage(t(plugin, p, "message.question-submitted"));
                    questionObs.setValue("");
                });

        form.divider();
        form.button(
                t(plugin, player, "button.leave-room"),
                p -> {
                    plugin.leaveRoom(p);
                    p.sendMessage(t(plugin, p, "message.left-room"));
                });

        form.show(player);
    }

    private static void showHostGameForm(SituationPuzzleGame plugin, Player player, GameRoom room) {
        CustomForm form = new CustomForm(t(plugin, player, "title.host-game-room", room.getRoomId()));

        form.header(t(plugin, player, "header.puzzle-title"));
        form.label("§f" + room.getPuzzleTitle());
        form.label(t(plugin, player, "label.puzzle-truth-value", room.getPuzzleTruth()));
        form.divider();

        GameRoom.Question current = room.getFirstUnansweredQuestion();
        if (current != null) {
            form.header(t(plugin, player, "header.pending-questions", room.getUnansweredCount()));
            form.label(
                    t(plugin, player, "label.question-from", current.getAskerName(), current.getQuestion()));
            form.divider();

            form.button(
                    t(plugin, player, "answer.yes"),
                    p -> {
                        room.answerQuestion(current, GameRoom.AnswerType.YES);
                        plugin.recordQuestionAnswered(room, current);
                        notifyAnswer(plugin, room, current);
                        form.close(p);
                    });
            form.button(
                    t(plugin, player, "answer.no"),
                    p -> {
                        room.answerQuestion(current, GameRoom.AnswerType.NO);
                        notifyAnswer(plugin, room, current);
                        form.close(p);
                    });
            form.button(
                    t(plugin, player, "answer.irrelevant"),
                    p -> {
                        room.answerQuestion(current, GameRoom.AnswerType.IRRELEVANT);
                        notifyAnswer(plugin, room, current);
                        form.close(p);
                    });

            if (plugin.getPluginConfig().isAnswererEnabled()) {
                form.button(
                        t(plugin, player, "button.ai-answer"),
                        p -> {
                            showHostAiAnswerLoading(plugin, p, room, current);
                        });
            }
        } else {
            form.label(t(plugin, player, "label.no-pending-questions"));
        }

        form.divider();

        String qaText = room.buildQAText(lang(player));
        if (!qaText.isEmpty()) {
            form.header(t(plugin, player, "header.qa-record"));
            form.label(qaText);
        }

        form.divider();
        form.button(
                t(plugin, player, "button.finish-game"),
                p -> {
                    MessageBox confirm = new MessageBox(t(plugin, player, "title.confirm-finish"));
                    confirm.body(t(plugin, player, "confirm.finish-body"));
                    confirm.button1(
                            t(plugin, player, "button.confirm-finish"),
                            cp -> {
                                room.finishGame();
                                if (plugin.getPluginConfig().isStatsEnabled()) {
                                    for (Player rp : room.getAllPlayers()) {
                                        plugin.getStatsManager().getStats(rp.getName()).recordMultiGameCompleted();
                                    }
                                    plugin.getStatsManager().markDirty();
                                }
                                for (Player rp : room.getAllPlayers()) {
                                    rp.sendMessage("§a=============================");
                                    rp.sendMessage(t(plugin, rp, "message.game-ended"));
                                    rp.sendMessage("§a=============================");
                                    rp.sendMessage(t(plugin, rp, "message.puzzle-truth", room.getPuzzleTruth()));
                                    if (supportsDdui(rp)) {
                                        showResultForm(plugin, rp, room);
                                    } else {
                                        plugin.getLegacyUIFactory().showContext(rp);
                                    }
                                }
                            });
                    confirm.button2(
                            t(plugin, player, "button.continue-game"), cp -> showHostGameForm(plugin, cp, room));
                    confirm.show(p);
                });

        form.show(player);
    }

    // ==================== 结果界面 ====================

    public static void showResultForm(SituationPuzzleGame plugin, Player player, GameRoom room) {
        if (!checkDduiSupport(plugin, player)) return;
        MessageBox msg = new MessageBox(t(plugin, player, "title.result", room.getRoomId()));
        msg.body(t(plugin, player, "result.body", room.getPuzzleTitle(), room.getPuzzleTruth()));

        String actionLabel =
                room.isHost(player)
                        ? t(plugin, player, "button.destroy-room")
                        : t(plugin, player, "button.leave-room");
        msg.button1(
                actionLabel,
                p -> {
                    if (room.isHost(p)) {
                        plugin.destroyRoom(room);
                    } else {
                        plugin.leaveRoom(p);
                        p.sendMessage(t(plugin, p, "message.left-room"));
                    }
                });
        msg.button2(t(plugin, player, "button.close"), p -> {});
        msg.show(player);
    }

    // ==================== 单人模式 ====================

    private static void showSinglePlayerDifficultyForm(SituationPuzzleGame plugin, Player player) {
        if (plugin.getPlayerRoom(player) != null) {
            player.sendMessage(t(plugin, player, "message.already-in-room"));
            return;
        }

        PluginConfig cfg = plugin.getPluginConfig();
        List<String> keys = cfg.getDifficultyKeys();

        if (keys.isEmpty()) {
            player.sendMessage(t(plugin, player, "message.no-difficulties"));
            return;
        }

        CustomForm form = new CustomForm(t(plugin, player, "title.single-difficulty"));
        form.header(t(plugin, player, "single.difficulty-header"));

        List<DropdownElement.Item> items = new ArrayList<>();
        for (String key : keys) {
            String label =
                    cfg.getDifficultyStars(key)
                            + " "
                            + cfg.getDifficultyName(key, lang(player))
                            + " - "
                            + cfg.getDifficultyDescription(key, lang(player));
            items.add(DropdownElement.Item.builder().label(label).build());
        }

        int defaultIndex = Math.max(0, keys.indexOf("normal"));
        Observable<Long> selected = new Observable<>((long) defaultIndex);
        form.dropdown(t(plugin, player, "label.difficulty"), items, selected);
        form.divider();
        form.button(
                t(plugin, player, "button.start-game"),
                p -> {
                    int index = (int) (long) selected.getValue();
                    if (index < 0 || index >= keys.size()) {
                        p.sendMessage(t(plugin, p, "message.invalid-selection"));
                        return;
                    }
                    generateSinglePlayerPuzzle(plugin, p, keys.get(index));
                });
        form.button(t(plugin, player, "button.back"), p -> showMainMenu(plugin, p));
        form.show(player);
    }

    private static void generateSinglePlayerPuzzle(
            SituationPuzzleGame plugin, Player player, String difficulty) {
        if (plugin.getPluginConfig().isCacheEnabled()) {
            PuzzleCache.CachedPuzzle cached =
                    plugin.getPuzzleCache().getPuzzleForPlayer(difficulty, player.getName());
            if (cached != null) {
                showSinglePlayerConfirmForm(plugin, player, cached.title(), cached.truth(), difficulty);
                return;
            }
        }

        if (!pendingGeneration.add(player)) {
            player.sendMessage(t(plugin, player, "message.generation-pending"));
            return;
        }
        LangCode lang = lang(player);
        String prompt = plugin.getPluginConfig().getDifficultyPrompt(difficulty, lang);
        CustomForm loadingForm = new CustomForm(t(plugin, player, "title.single-ai-generate"));
        Observable<String> status = new Observable<>("");
        loadingForm.label(status);
        loadingForm.show(player);

        cn.nukkit.scheduler.TaskHandler animTask =
                startLoadingAnimation(plugin, status, t(plugin, player, "loading.generating-puzzle"));

        plugin
                .getAiService()
                .generatePuzzle(prompt, lang)
                .whenComplete(
                        (result, ex) -> {
                            plugin
                                    .getServer()
                                    .getScheduler()
                                    .scheduleTask(
                                            plugin,
                                            () -> {
                                                animTask.cancel();
                                                pendingGeneration.remove(player);
                                                if (!player.isConnected()) return;
                                                if (ex != null) {
                                                    plugin.getLogger().error("AI 题目生成调用失败(单人)", ex);
                                                    showErrorForm(
                                                            plugin,
                                                            player,
                                                            t(plugin, player, "message.ai-call-failed", ex.getMessage()),
                                                            () -> showSinglePlayerDifficultyForm(plugin, player));
                                                } else if (result.isSuccess()) {
                                                    if (plugin.getPluginConfig().isCacheEnabled()) {
                                                        plugin
                                                                .getPuzzleCache()
                                                                .addPuzzle(difficulty, result.getTitle(), result.getTruth());
                                                        plugin.getPuzzleCache().markAsSeen(player.getName(), result.getTitle());
                                                        plugin.getPuzzleCache().save();
                                                    }
                                                    showSinglePlayerConfirmForm(
                                                            plugin, player, result.getTitle(), result.getTruth(), difficulty);
                                                } else {
                                                    plugin.getLogger().error("AI 题目生成失败(单人): " + result.getError());
                                                    showErrorForm(
                                                            plugin,
                                                            player,
                                                            t(
                                                                    plugin,
                                                                    player,
                                                                    "message.puzzle-generation-failed",
                                                                    result.getError()),
                                                            () -> showSinglePlayerDifficultyForm(plugin, player));
                                                }
                                            });
                        });
    }

    private static void showSinglePlayerConfirmForm(
            SituationPuzzleGame plugin, Player player, String title, String truth, String difficulty) {
        CustomForm form = new CustomForm(t(plugin, player, "title.single-ai-generate"));
        form.header(t(plugin, player, "single.confirm-header"));
        form.label(t(plugin, player, "label.puzzle-title-value", title));
        form.divider();

        form.button(
                t(plugin, player, "button.start-reasoning"),
                p -> {
                    GameRoom room = plugin.createSinglePlayerRoom(p, title, truth);
                    if (room != null) {
                        p.sendMessage("§a=============================");
                        p.sendMessage(t(plugin, p, "message.single-started"));
                        p.sendMessage("§a=============================");
                        p.sendMessage(t(plugin, p, "message.puzzle-title", title));
                        showSinglePlayerGameForm(plugin, p);
                    } else {
                        p.sendMessage(t(plugin, p, "message.create-failed"));
                    }
                });

        form.button(
                t(plugin, player, "button.regenerate"),
                p -> generateSinglePlayerPuzzle(plugin, p, difficulty));
        form.button(t(plugin, player, "button.back"), p -> showMainMenu(plugin, p));
        form.show(player);
    }

    public static void showSinglePlayerGameForm(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(plugin, player)) return;
        GameRoom room = plugin.getPlayerRoom(player);
        if (room == null || !room.isSinglePlayer() || room.getState() != GameState.PLAYING) return;

        CustomForm form = new CustomForm(t(plugin, player, "title.single-game"));

        form.header(t(plugin, player, "header.puzzle-title"));
        form.label("§f" + room.getPuzzleTitle());
        form.divider();

        Observable<String> qaObs = new Observable<>(buildAnsweredQAText(plugin, room, lang(player)));
        form.label(qaObs);

        Observable<String> pendingObs = new Observable<>("");
        form.label(pendingObs);
        form.divider();

        if (!plugin.getPluginConfig().isAnswererEnabled()) {
            form.label(t(plugin, player, "message.answerer-not-configured"));
            form.show(player);
            return;
        }

        Observable<String> questionObs =
                new Observable<>("", ObservableOptions.builder().clientWritable(true).build());
        form.textField(t(plugin, player, "field.single-question"), questionObs);

        boolean[] waiting = {false};
        String[] animFrames = loadingFrames(t(plugin, player, "loading.ai-thinking"));

        form.button(
                t(plugin, player, "button.submit-question"),
                p -> {
                    if (waiting[0]) return;
                    String question = questionObs.getValue();
                    if (question == null || question.isBlank()) {
                        p.sendMessage(t(plugin, p, "message.question-empty"));
                        return;
                    }
                    waiting[0] = true;
                    GameRoom.Question q = room.askQuestion(p, question);
                    if (plugin.getPluginConfig().isStatsEnabled()) {
                        plugin.getStatsManager().getStats(p.getName()).recordSoloQuestion();
                        plugin.getStatsManager().markDirty();
                    }
                    plugin.getServer().getScheduler().scheduleTask(plugin, () -> questionObs.setValue(""));

                    String prefix = t(plugin, p, "label.you-question", question);
                    pendingObs.setValue(prefix + animFrames[0]);
                    int[] idx = {0};
                    cn.nukkit.scheduler.TaskHandler animTask =
                            plugin
                                    .getServer()
                                    .getScheduler()
                                    .scheduleRepeatingTask(
                                            plugin,
                                            () -> {
                                                idx[0] = (idx[0] + 1) % animFrames.length;
                                                pendingObs.setValue(prefix + animFrames[idx[0]]);
                                            },
                                            10);

                    plugin
                            .getAiService()
                            .answerSoloQuestion(room.getPuzzleTruth(), question, lang(p))
                            .whenComplete(
                                    (result, ex) -> {
                                        plugin
                                                .getServer()
                                                .getScheduler()
                                                .scheduleTask(
                                                        plugin,
                                                        () -> {
                                                            animTask.cancel();
                                                            if (!isActivePlayingRoom(plugin, p, room)) return;
                                                            waiting[0] = false;
                                                            if (ex != null) {
                                                                plugin.getLogger().error("AI 回答失败(单人)", ex);
                                                                pendingObs.setValue(
                                                                        prefix
                                                                                + t(
                                                                                        plugin,
                                                                                        player,
                                                                                        "message.ai-answer-failed",
                                                                                        ex.getMessage()));
                                                                return;
                                                            }
                                                            GameRoom.AnswerType answer = result.answerType();
                                                            room.answerQuestion(q, answer);
                                                            plugin.recordQuestionAnswered(room, q);
                                                            qaObs.setValue(buildAnsweredQAText(plugin, room, lang(player)));
                                                            pendingObs.setValue("");
                                                            if (result.solved()) {
                                                                finishSinglePlayerGame(plugin, p, room, true);
                                                            }
                                                        });
                                    });
                });

        form.divider();
        form.button(
                t(plugin, player, "button.give-up"),
                p -> {
                    MessageBox confirm = new MessageBox(t(plugin, player, "title.confirm-give-up"));
                    confirm.body(t(plugin, player, "confirm.give-up-body"));
                    confirm.button1(
                            t(plugin, player, "button.confirm-give-up"),
                            cp -> finishSinglePlayerGame(plugin, cp, room, false));
                    confirm.button2(
                            t(plugin, player, "button.continue-reasoning"),
                            cp -> showSinglePlayerGameForm(plugin, cp));
                    confirm.show(p);
                });

        form.show(player);
    }

    private static void finishSinglePlayerGame(
            SituationPuzzleGame plugin, Player player, GameRoom room, boolean solved) {
        if (!isActivePlayingRoom(plugin, player, room)) return;
        room.finishGame();
        if (plugin.getPluginConfig().isStatsEnabled()) {
            PlayerStats stats = plugin.getStatsManager().getStats(player.getName());
            if (solved) {
                stats.recordSoloGameCompleted();
            } else {
                stats.recordSoloGameAbandoned();
            }
            plugin.getStatsManager().markDirty();
        }
        player.sendMessage("§a=============================");
        player.sendMessage(t(plugin, player, solved ? "message.single-solved" : "message.game-ended"));
        if (solved) {
            player.sendMessage(t(plugin, player, "message.single-auto-win"));
        }
        player.sendMessage("§a=============================");
        player.sendMessage(t(plugin, player, "message.puzzle-truth", room.getPuzzleTruth()));
        showResultForm(plugin, player, room);
    }

    private static boolean isActivePlayingRoom(
            SituationPuzzleGame plugin, Player player, GameRoom room) {
        return player.isConnected()
                && plugin.getPlayerRoom(player) == room
                && room.getState() == GameState.PLAYING;
    }

    private static String buildAnsweredQAText(
            SituationPuzzleGame plugin, GameRoom room, LangCode lang) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (GameRoom.Question q : room.getQuestions()) {
            if (!q.isAnswered()) continue;
            count++;
            sb.append("§b").append(q.getAskerName()).append("§f：").append(q.getQuestion()).append("\n");
            sb.append("  §7→ ").append(Texts.answerLabel(q.getAnswerType(), lang)).append("\n");
        }
        if (count == 0) return "";
        return Texts.t(plugin, lang, "qa.answered-header", count) + sb;
    }

    private static void showHostAiAnswerLoading(
            SituationPuzzleGame plugin, Player player, GameRoom room, GameRoom.Question current) {
        CustomForm form = new CustomForm(t(plugin, player, "title.ai-answering"));
        form.header(
                t(plugin, player, "label.question-from", current.getAskerName(), current.getQuestion()));

        Observable<String> status = new Observable<>("");
        form.label(status);
        form.show(player);

        cn.nukkit.scheduler.TaskHandler animTask =
                startLoadingAnimation(plugin, status, t(plugin, player, "loading.ai-thinking"));

        plugin
                .getAiService()
                .answerQuestion(room.getPuzzleTruth(), current.getQuestion(), lang(player))
                .whenComplete(
                        (answer, ex) -> {
                            plugin
                                    .getServer()
                                    .getScheduler()
                                    .scheduleTask(
                                            plugin,
                                            () -> {
                                                animTask.cancel();
                                                if (!player.isConnected()) return;
                                                if (ex != null) {
                                                    plugin.getLogger().error("AI 回答失败(多人)", ex);
                                                    animTask.cancel();
                                                    if (player.isConnected()) {
                                                        showErrorForm(
                                                                plugin,
                                                                player,
                                                                t(plugin, player, "message.ai-answer-failed", ex.getMessage()),
                                                                () -> showHostGameForm(plugin, player, room));
                                                    }
                                                    return;
                                                }
                                                room.answerQuestion(current, answer);
                                                plugin.recordQuestionAnswered(room, current);
                                                notifyAnswer(plugin, room, current);
                                                status.setValue("§f→ " + Texts.answerLabel(answer, lang(player)));
                                                plugin
                                                        .getServer()
                                                        .getScheduler()
                                                        .scheduleDelayedTask(
                                                                plugin,
                                                                () -> {
                                                                    if (player.isConnected()) {
                                                                        showHostGameForm(plugin, player, room);
                                                                    }
                                                                },
                                                                40);
                                            });
                        });
    }

    // ==================== 排行榜与统计 ====================

    public static void showLeaderboardMenu(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(plugin, player)) return;
        CustomForm form = new CustomForm(t(plugin, player, "title.leaderboard"));
        form.header(t(plugin, player, "leaderboard.header"));
        form.button(
                metricTitle(plugin, player, "solo_completed"),
                p -> showLeaderboard(plugin, p, "solo_completed"));
        form.button(
                metricTitle(plugin, player, "multi_completed"),
                p -> showLeaderboard(plugin, p, "multi_completed"));
        form.button(
                metricTitle(plugin, player, "total_games"), p -> showLeaderboard(plugin, p, "total_games"));
        form.button(
                metricTitle(plugin, player, "questions"), p -> showLeaderboard(plugin, p, "questions"));
        form.button(
                t(
                        plugin,
                        player,
                        "metric.hit-rate-min",
                        plugin.getPluginConfig().getMinQuestionsForHitRate()),
                p -> showLeaderboard(plugin, p, "hit_rate"));
        form.button(metricTitle(plugin, player, "host"), p -> showLeaderboard(plugin, p, "host"));
        form.button(
                metricTitle(plugin, player, "solo_streak"), p -> showLeaderboard(plugin, p, "solo_streak"));
        form.divider();
        form.button(t(plugin, player, "button.back"), p -> showMainMenu(plugin, p));
        form.show(player);
    }

    public static void showLeaderboard(SituationPuzzleGame plugin, Player player, String metric) {
        if (!checkDduiSupport(plugin, player)) return;
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

        String title = metricTitle(plugin, player, metric);
        CustomForm form = new CustomForm(t(plugin, player, "title.leaderboard-metric", title));

        if (top.isEmpty()) {
            form.label(t(plugin, player, "label.no-data"));
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < top.size(); i++) {
                PlayerStats s = top.get(i);
                String medal =
                        switch (i) {
                            case 0 -> "§6#1";
                            case 1 -> "§7#2";
                            case 2 -> "§e#3";
                            default -> "§f#" + (i + 1);
                        };
                sb.append(medal)
                        .append(" §f")
                        .append(s.getPlayerName())
                        .append(" §7- §e")
                        .append(formatMetricValue(metric, s))
                        .append("\n");
            }
            form.label(sb.toString());
        }

        form.divider();
        form.button(t(plugin, player, "button.back-leaderboard"), p -> showLeaderboardMenu(plugin, p));
        form.show(player);
    }

    public static void showPersonalStats(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(plugin, player)) return;
        PlayerStats stats = plugin.getStatsManager().getStats(player.getName());

        CustomForm form = new CustomForm(t(plugin, player, "title.my-stats", player.getName()));

        form.header(t(plugin, player, "stats.header.solo"));
        form.label(
                t(plugin, player, "stats.games-played", stats.getSoloGamesPlayed())
                        + "\n"
                        + t(plugin, player, "stats.completed", stats.getSoloGamesCompleted())
                        + "\n"
                        + t(plugin, player, "stats.abandoned", stats.getSoloGamesAbandoned())
                        + "\n"
                        + t(plugin, player, "stats.questions", stats.getSoloQuestionsAsked())
                        + "\n"
                        + t(plugin, player, "stats.hits", stats.getSoloQuestionsHit())
                        + "\n"
                        + t(
                                plugin,
                                player,
                                "stats.completion-rate",
                                String.format("%.1f%%", stats.getSoloCompletionRate() * 100))
                        + "\n"
                        + t(plugin, player, "stats.current-streak", stats.getSoloCurrentStreak())
                        + "\n"
                        + t(plugin, player, "stats.best-streak", stats.getSoloBestStreak()));

        form.divider();
        form.header(t(plugin, player, "stats.header.multi"));
        form.label(
                t(plugin, player, "stats.games-played", stats.getMultiGamesPlayed())
                        + "\n"
                        + t(plugin, player, "stats.completed", stats.getMultiGamesCompleted())
                        + "\n"
                        + t(plugin, player, "stats.abandoned", stats.getMultiGamesAbandoned())
                        + "\n"
                        + t(plugin, player, "stats.questions", stats.getMultiQuestionsAsked())
                        + "\n"
                        + t(plugin, player, "stats.hits", stats.getMultiQuestionsHit())
                        + "\n"
                        + t(
                                plugin,
                                player,
                                "stats.completion-rate",
                                String.format("%.1f%%", stats.getMultiCompletionRate() * 100)));

        form.divider();
        form.header(t(plugin, player, "stats.header.host"));
        form.label(t(plugin, player, "stats.host-count", stats.getHostCount()));

        form.divider();
        form.button(t(plugin, player, "button.close"), p -> {});
        form.show(player);
    }

    private static String metricTitle(SituationPuzzleGame plugin, Player player, String metric) {
        return switch (metric) {
            case "solo_completed" -> t(plugin, player, "metric.solo-completed");
            case "multi_completed" -> t(plugin, player, "metric.multi-completed");
            case "total_games" -> t(plugin, player, "metric.total-games");
            case "questions" -> t(plugin, player, "metric.questions");
            case "hit_rate" -> t(plugin, player, "metric.hit-rate");
            case "host" -> t(plugin, player, "metric.host");
            case "solo_streak" -> t(plugin, player, "metric.solo-streak");
            default -> metric;
        };
    }

    private static String formatMetricValue(String metric, PlayerStats s) {
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

    // ==================== 工具方法 ====================

    private static cn.nukkit.scheduler.TaskHandler startLoadingAnimation(
            SituationPuzzleGame plugin, Observable<String> status, String message) {
        String[] frames = loadingFrames(message);
        status.setValue(frames[0]);
        int[] index = {0};
        return plugin
                .getServer()
                .getScheduler()
                .scheduleRepeatingTask(
                        plugin,
                        () -> {
                            index[0] = (index[0] + 1) % frames.length;
                            status.setValue(frames[index[0]]);
                        },
                        10);
    }

    private static String[] loadingFrames(String message) {
        return new String[] {
            "§e⏳ " + message,
            "§e⏳ " + message + " §7.",
            "§e⏳ " + message + " §7..",
            "§e⏳ " + message + " §7..."
        };
    }

    private static void notifyAnswer(
            SituationPuzzleGame plugin, GameRoom room, GameRoom.Question question) {
        for (Player p : room.getAllPlayers()) {
            p.sendMessage(
                    t(
                            plugin,
                            p,
                            "message.answer-notify",
                            question.getAskerName(),
                            question.getQuestion(),
                            Texts.answerLabel(question.getAnswerType(), lang(p))));
        }
    }

    private static void showErrorForm(
            SituationPuzzleGame plugin, Player player, String message, Runnable onBack) {
        CustomForm form = new CustomForm(t(plugin, player, "title.error"));
        form.label(message);
        form.divider();
        form.button(t(plugin, player, "button.back"), p -> onBack.run());
        form.button(t(plugin, player, "button.close"), p -> {});
        form.show(player);
    }
}
