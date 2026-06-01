package cn.lanink.situationpuzzlegame.ui;

import cn.nukkit.Player;
import cn.nukkit.ddui.CustomForm;
import cn.nukkit.ddui.MessageBox;
import cn.nukkit.ddui.Observable;
import cn.nukkit.ddui.ObservableOptions;
import cn.nukkit.ddui.element.DropdownElement;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.lanink.situationpuzzlegame.SituationPuzzleGame;
import cn.lanink.situationpuzzlegame.cache.PuzzleCache;
import cn.lanink.situationpuzzlegame.config.PluginConfig;
import cn.lanink.situationpuzzlegame.game.GameRoom;
import cn.lanink.situationpuzzlegame.game.GameState;
import cn.lanink.situationpuzzlegame.stats.PlayerStats;
import cn.lanink.situationpuzzlegame.stats.StatsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class UIFactory {

    private static final int MIN_DDUI_PROTOCOL = ProtocolInfo.v1_26_0;
    private static final Set<Player> pendingGeneration = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private static boolean checkDduiSupport(Player player) {
        if (player.protocol >= MIN_DDUI_PROTOCOL) return true;
        player.sendMessage("§c你的客户端版本过低，无法使用海龟汤游戏界面。");
        player.sendMessage("§7请升级到 Minecraft 1.26.0 或更高版本。");
        return false;
    }

    // ==================== 主菜单 ====================

    public static void showMainMenu(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(player)) return;
        CustomForm form = new CustomForm("§l§6海龟汤");
        form.header("§f欢迎来到海龟汤游戏！");
        form.label("§7海龟汤是一种情境猜谜游戏。\n出题者给出一个看似不合理的场景，\n猜题者通过提出是非题来还原真相。");
        form.divider();

        PluginConfig cfg = plugin.getPluginConfig();
        if (cfg.isGeneratorEnabled() && cfg.isAnswererEnabled()) {
            form.button("单人模式", p -> showSinglePlayerDifficultyForm(plugin, p));
        }
        form.button("创建房间", p -> showCreateRoomMenu(plugin, p));
        form.button("加入房间", p -> showRoomListForm(plugin, p));
        if (cfg.isStatsEnabled()) {
            form.divider();
            form.button("排行榜", p -> showLeaderboardMenu(plugin, p));
            form.button("我的统计", p -> showPersonalStats(plugin, p));
        }
        form.show(player);
    }

    // ==================== 创建房间 ====================

    private static void showCreateRoomMenu(SituationPuzzleGame plugin, Player player) {
        if (plugin.getPlayerRoom(player) != null) {
            player.sendMessage("§c你已经在一个房间中！");
            return;
        }

        CustomForm form = new CustomForm("§l§a创建房间");
        form.header("§e选择出题方式");

        if (plugin.getPluginConfig().isGeneratorEnabled()) {
            form.button("AI 出题", p -> showDifficultySelectForm(plugin, p));
        }
        form.button("手动出题", p -> showManualCreateForm(plugin, p));
        form.button("返回", p -> showMainMenu(plugin, p));
        form.show(player);
    }

    // ==================== AI 出题（创建房间子流程） ====================

    private static void showDifficultySelectForm(SituationPuzzleGame plugin, Player player) {
        PluginConfig cfg = plugin.getPluginConfig();
        List<String> keys = cfg.getDifficultyKeys();

        if (keys.isEmpty()) {
            player.sendMessage("§c未配置难度等级！");
            return;
        }

        CustomForm form = new CustomForm("§l§dAI 出题 - 选择难度");
        form.header("§e请选择题目难度：");

        List<DropdownElement.Item> items = new ArrayList<>();
        for (String key : keys) {
            String label = cfg.getDifficultyStars(key) + " " + cfg.getDifficultyName(key) + " - " + cfg.getDifficultyDescription(key);
            items.add(DropdownElement.Item.builder().label(label).build());
        }

        int defaultIndex = Math.max(0, keys.indexOf("normal"));
        Observable<Long> selected = new Observable<>((long) defaultIndex);
        form.dropdown("难度", items, selected);
        form.divider();
        form.button("生成题目", p -> {
            int index = (int) (long) selected.getValue();
            if (index < 0 || index >= keys.size()) {
                p.sendMessage("§c无效选择！");
                return;
            }
            String difficulty = keys.get(index);
            generateAndShow(plugin, p, difficulty);
        });
        form.button("返回", p -> showCreateRoomMenu(plugin, p));
        form.show(player);
    }

    private static void generateAndShow(SituationPuzzleGame plugin, Player player, String difficulty) {
        if (plugin.getPluginConfig().isCacheEnabled()) {
            PuzzleCache.CachedPuzzle cached = plugin.getPuzzleCache().getPuzzleForPlayer(difficulty, player.getName());
            if (cached != null) {
                showAiConfirmForm(plugin, player, cached.title(), cached.truth(), difficulty);
                return;
            }
        }

        if (!pendingGeneration.add(player)) {
            player.sendMessage("§c题目正在生成中，请稍候！");
            return;
        }
        String prompt = plugin.getPluginConfig().getDifficultyPrompt(difficulty);
        CustomForm loadingForm = new CustomForm("§l§dAI 出题");
        Observable<String> status = new Observable<>("");
        loadingForm.label(status);
        loadingForm.show(player);

        cn.nukkit.scheduler.TaskHandler animTask = startLoadingAnimation(plugin, status, "正在生成题目，请稍候");

        plugin.getAiService().generatePuzzle(prompt).whenComplete((result, ex) -> {
            plugin.getServer().getScheduler().scheduleTask(plugin, () -> {
                animTask.cancel();
                pendingGeneration.remove(player);
                if (!player.isConnected()) return;
                if (ex != null) {
                    plugin.getLogger().error("AI 题目生成调用失败", ex);
                    showErrorForm(plugin, player, "§cAI 调用失败：" + ex.getMessage(), () -> showDifficultySelectForm(plugin, player));
                } else if (result.isSuccess()) {
                    if (plugin.getPluginConfig().isCacheEnabled()) {
                        plugin.getPuzzleCache().addPuzzle(difficulty, result.getTitle(), result.getTruth());
                        plugin.getPuzzleCache().markAsSeen(player.getName(), result.getTitle());
                        plugin.getPuzzleCache().save();
                    }
                    showAiConfirmForm(plugin, player, result.getTitle(), result.getTruth(), difficulty);
                } else {
                    plugin.getLogger().error("AI 题目生成失败: " + result.getError());
                    showErrorForm(plugin, player, "§c题目生成失败：" + result.getError(), () -> showDifficultySelectForm(plugin, player));
                }
            });
        });
    }

    // ==================== 加入房间 ====================

    private static void showAiConfirmForm(SituationPuzzleGame plugin, Player player,
                                           String title, String truth, String difficulty) {
        CustomForm form = new CustomForm("§l§dAI 生成题目");
        form.header("§eAI 已生成以下题目：");
        form.label("§f汤面：§e" + title);
        form.label("§f汤底：§7" + truth);
        form.divider();

        form.button("确认创建", p -> {
            GameRoom room = plugin.createRoom(p, title, truth);
            if (room != null) {
                p.sendMessage("§a房间创建成功！");
                showLobbyForm(plugin, p);
            } else {
                p.sendMessage("§c创建失败！");
            }
        });

        form.button("重新生成", p -> {
            generateAndShow(plugin, p, difficulty);
        });
        form.button("返回", p -> showCreateRoomMenu(plugin, p));
        form.show(player);
    }

    // ==================== 手动出题 ====================

    private static void showManualCreateForm(SituationPuzzleGame plugin, Player player) {
        if (plugin.getPlayerRoom(player) != null) {
            player.sendMessage("§c你已经在一个房间中！");
            return;
        }

        CustomForm form = new CustomForm("§l§a创建房间");
        Observable<String> titleObs = new Observable<>("", ObservableOptions.builder().clientWritable(true).build());
        Observable<String> truthObs = new Observable<>("", ObservableOptions.builder().clientWritable(true).build());

        form.label("§e请设置海龟汤的题目：");
        form.textField("汤面（题目描述）", titleObs);
        form.textField("汤底（完整真相）", truthObs);
        form.divider();
        form.button("确认创建", p -> {
            String title = titleObs.getValue();
            String truth = truthObs.getValue();
            if (title == null || title.isBlank()) {
                p.sendMessage("§c汤面不能为空！");
                return;
            }
            if (truth == null || truth.isBlank()) {
                p.sendMessage("§c汤底不能为空！");
                return;
            }
            GameRoom room = plugin.createRoom(p, title, truth);
            if (room == null) {
                p.sendMessage("§c创建失败！");
                return;
            }
            p.sendMessage("§a房间创建成功！");
            showLobbyForm(plugin, p);
        });
        form.button("返回", p -> showCreateRoomMenu(plugin, p));
        form.show(player);
    }

    // ==================== 加入房间 ====================

    public static void showRoomListForm(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(player)) return;
        List<GameRoom> availableRooms = plugin.getAvailableRooms();
        if (availableRooms.isEmpty()) {
            MessageBox msg = new MessageBox("§l§b加入房间");
            msg.body("§c当前没有可加入的房间！");
            msg.button1("返回", p -> showMainMenu(plugin, p));
            msg.button2("返回", p -> showMainMenu(plugin, p));
            msg.show(player);
            return;
        }

        CustomForm form = new CustomForm("§l§b加入房间");
        List<DropdownElement.Item> items = new ArrayList<>();
        for (GameRoom room : availableRooms) {
            items.add(DropdownElement.Item.builder()
                    .label(room.getRoomId() + " (" + room.getAllPlayers().size() + "人)")
                    .build());
        }

        Observable<Long> selected = new Observable<>(0L);
        form.dropdown("选择房间", items, selected);
        form.divider();
        form.button("加入", p -> {
            int index = (int) (long) selected.getValue();
            if (index < 0 || index >= availableRooms.size()) {
                p.sendMessage("§c无效选择！");
                return;
            }
            GameRoom room = availableRooms.get(index);
            if (plugin.joinRoom(p, room)) {
                room.getAllPlayers().forEach(rp ->
                        rp.sendMessage("§a" + p.getName() + " 加入了房间！"));
                showLobbyForm(plugin, p);
            } else {
                p.sendMessage("§c加入失败！");
            }
        });
        form.show(player);
    }

    // ==================== 大厅 ====================

    public static void showLobbyForm(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(player)) return;
        GameRoom room = plugin.getPlayerRoom(player);
        if (room == null) return;

        CustomForm form = new CustomForm("§l§e房间：§f" + room.getRoomId());

        StringBuilder playerList = new StringBuilder("§f玩家列表：\n");
        playerList.append("§6★ §f").append(room.getHost().getName()).append(" §7(主持人)\n");
        for (Player p : room.getPlayers()) {
            playerList.append("§f  ").append(p.getName()).append("\n");
        }
        form.label(playerList.toString());

        if (room.isHost(player)) {
            form.button("开始游戏", p -> {
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
                    rp.sendMessage("§6§l    海龟汤游戏开始！");
                    rp.sendMessage("§a=============================");
                    rp.sendMessage("§e汤面：§f" + room.getPuzzleTitle());
                    rp.sendMessage("§7输入 /hgt 打开游戏界面");
                    showGameForm(plugin, rp);
                }
            });
            form.divider();
            form.button("解散房间", p -> plugin.destroyRoom(room));
        } else {
            form.label("§7等待主持人开始游戏...");
            form.divider();
            form.button("离开房间", p -> {
                plugin.leaveRoom(p);
                p.sendMessage("§a已离开房间！");
            });
        }

        form.show(player);
    }

    // ==================== 上下文分发 ====================

    public static void showContextForm(SituationPuzzleGame plugin, Player player, GameRoom room) {
        if (!checkDduiSupport(player)) return;
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
        if (!checkDduiSupport(player)) return;
        GameRoom room = plugin.getPlayerRoom(player);
        if (room == null || room.getState() != GameState.PLAYING) return;

        if (room.isHost(player)) {
            showHostGameForm(plugin, player, room);
        } else {
            showGuesserGameForm(plugin, player, room);
        }
    }

    private static void showGuesserGameForm(SituationPuzzleGame plugin, Player player, GameRoom room) {
        CustomForm form = new CustomForm("§l§6海龟汤 - §f" + room.getRoomId());

        form.header("§e汤面");
        form.label("§f" + room.getPuzzleTitle());
        form.divider();

        Observable<String> qaObs = room.getQaObservable();
        qaObs.setValue(room.buildQAText());
        form.header("§e提问记录");
        form.label(qaObs);
        form.divider();

        Observable<String> questionObs = new Observable<>("", ObservableOptions.builder().clientWritable(true).build());
        form.textField("输入你的问题", questionObs);
        form.button("提交问题", p -> {
            String question = questionObs.getValue();
            if (question == null || question.isBlank()) {
                p.sendMessage("§c问题不能为空！");
                return;
            }
            room.askQuestion(p, question);
            if (plugin.getPluginConfig().isStatsEnabled()) {
                plugin.getStatsManager().getStats(p.getName()).recordMultiQuestion();
                plugin.getStatsManager().markDirty();
            }
            room.getHost().sendMessage("§b" + p.getName() + " §f提问：§e" + question);
            p.sendMessage("§a问题已提交！");
            questionObs.setValue("");
        });

        form.divider();
        form.button("离开房间", p -> {
            plugin.leaveRoom(p);
            p.sendMessage("§a已离开房间！");
        });

        form.show(player);
    }

    private static void showHostGameForm(SituationPuzzleGame plugin, Player player, GameRoom room) {
        CustomForm form = new CustomForm("§l§6海龟汤 - §f" + room.getRoomId() + " §7(主持人)");

        form.header("§e汤面");
        form.label("§f" + room.getPuzzleTitle());
        form.label("§7汤底：§f" + room.getPuzzleTruth());
        form.divider();

        GameRoom.Question current = room.getFirstUnansweredQuestion();
        if (current != null) {
            form.header("§e待回答问题 §7(" + room.getUnansweredCount() + "个待回答)");
            form.label("§b" + current.getAskerName() + " §f问：§e" + current.getQuestion());
            form.divider();

            form.button("是", p -> {
                room.answerQuestion(current, "§a是");
                plugin.recordQuestionAnswered(room, current);
                notifyAnswer(room, current);
                form.close(p);
            });
            form.button("不是", p -> {
                room.answerQuestion(current, "§c不是");
                notifyAnswer(room, current);
                form.close(p);
            });
            form.button("无关", p -> {
                room.answerQuestion(current, "§7与此无关");
                notifyAnswer(room, current);
                form.close(p);
            });

            if (plugin.getPluginConfig().isAnswererEnabled()) {
                form.button("AI 回答", p -> {
                    showHostAiAnswerLoading(plugin, p, room, current);
                });
            }
        } else {
            form.label("§7暂无待回答的问题");
        }

        form.divider();

        String qaText = room.buildQAText();
        if (!qaText.isEmpty()) {
            form.header("§e提问记录");
            form.label(qaText);
        }

        form.divider();
        form.button("结束游戏", p -> {
            MessageBox confirm = new MessageBox("§l§c确认结束");
            confirm.body("§e确定要结束游戏吗？\n§7将向所有玩家揭晓汤底。");
            confirm.button1("确定结束", cp -> {
                room.finishGame();
                if (plugin.getPluginConfig().isStatsEnabled()) {
                    for (Player rp : room.getAllPlayers()) {
                        plugin.getStatsManager().getStats(rp.getName()).recordMultiGameCompleted();
                    }
                    plugin.getStatsManager().markDirty();
                }
                for (Player rp : room.getAllPlayers()) {
                    rp.sendMessage("§a=============================");
                    rp.sendMessage("§6§l    游戏结束！");
                    rp.sendMessage("§a=============================");
                    rp.sendMessage("§e汤底：§f" + room.getPuzzleTruth());
                    showResultForm(plugin, rp, room);
                }
            });
            confirm.button2("继续游戏", cp -> showHostGameForm(plugin, cp, room));
            confirm.show(p);
        });

        form.show(player);
    }

    // ==================== 结果界面 ====================

    public static void showResultForm(SituationPuzzleGame plugin, Player player, GameRoom room) {
        if (!checkDduiSupport(player)) return;
        MessageBox msg = new MessageBox("§l§6游戏结束 - §f" + room.getRoomId());
        msg.body("§e汤面：§f" + room.getPuzzleTitle() + "\n\n§e汤底：§f" + room.getPuzzleTruth());

        String actionLabel = room.isHost(player) ? "解散房间" : "离开房间";
        msg.button1(actionLabel, p -> {
            if (room.isHost(p)) {
                plugin.destroyRoom(room);
            } else {
                plugin.leaveRoom(p);
                p.sendMessage("§a已离开房间！");
            }
        });
        msg.button2("关闭", p -> {});
        msg.show(player);
    }

    // ==================== 单人模式 ====================

    private static void showSinglePlayerDifficultyForm(SituationPuzzleGame plugin, Player player) {
        if (plugin.getPlayerRoom(player) != null) {
            player.sendMessage("§c你已经在一个房间中！");
            return;
        }

        PluginConfig cfg = plugin.getPluginConfig();
        List<String> keys = cfg.getDifficultyKeys();

        if (keys.isEmpty()) {
            player.sendMessage("§c未配置难度等级！");
            return;
        }

        CustomForm form = new CustomForm("§l§d单人模式 - 选择难度");
        form.header("§e选择题目难度，AI 将为你出题并回答问题");

        List<DropdownElement.Item> items = new ArrayList<>();
        for (String key : keys) {
            String label = cfg.getDifficultyStars(key) + " " + cfg.getDifficultyName(key) + " - " + cfg.getDifficultyDescription(key);
            items.add(DropdownElement.Item.builder().label(label).build());
        }

        int defaultIndex = Math.max(0, keys.indexOf("normal"));
        Observable<Long> selected = new Observable<>((long) defaultIndex);
        form.dropdown("难度", items, selected);
        form.divider();
        form.button("开始游戏", p -> {
            int index = (int) (long) selected.getValue();
            if (index < 0 || index >= keys.size()) {
                p.sendMessage("§c无效选择！");
                return;
            }
            generateSinglePlayerPuzzle(plugin, p, keys.get(index));
        });
        form.button("返回", p -> showMainMenu(plugin, p));
        form.show(player);
    }

    private static void generateSinglePlayerPuzzle(SituationPuzzleGame plugin, Player player, String difficulty) {
        if (plugin.getPluginConfig().isCacheEnabled()) {
            PuzzleCache.CachedPuzzle cached = plugin.getPuzzleCache().getPuzzleForPlayer(difficulty, player.getName());
            if (cached != null) {
                showSinglePlayerConfirmForm(plugin, player, cached.title(), cached.truth(), difficulty);
                return;
            }
        }

        if (!pendingGeneration.add(player)) {
            player.sendMessage("§c题目正在生成中，请稍候！");
            return;
        }
        String prompt = plugin.getPluginConfig().getDifficultyPrompt(difficulty);
        CustomForm loadingForm = new CustomForm("§l§d单人模式 - AI 出题");
        Observable<String> status = new Observable<>("");
        loadingForm.label(status);
        loadingForm.show(player);

        cn.nukkit.scheduler.TaskHandler animTask = startLoadingAnimation(plugin, status, "正在生成题目，请稍候");

        plugin.getAiService().generatePuzzle(prompt).whenComplete((result, ex) -> {
            plugin.getServer().getScheduler().scheduleTask(plugin, () -> {
                animTask.cancel();
                pendingGeneration.remove(player);
                if (!player.isConnected()) return;
                if (ex != null) {
                    plugin.getLogger().error("AI 题目生成调用失败(单人)", ex);
                    showErrorForm(plugin, player, "§cAI 调用失败：" + ex.getMessage(), () -> showSinglePlayerDifficultyForm(plugin, player));
                } else if (result.isSuccess()) {
                    if (plugin.getPluginConfig().isCacheEnabled()) {
                        plugin.getPuzzleCache().addPuzzle(difficulty, result.getTitle(), result.getTruth());
                        plugin.getPuzzleCache().markAsSeen(player.getName(), result.getTitle());
                        plugin.getPuzzleCache().save();
                    }
                    showSinglePlayerConfirmForm(plugin, player, result.getTitle(), result.getTruth(), difficulty);
                } else {
                    plugin.getLogger().error("AI 题目生成失败(单人): " + result.getError());
                    showErrorForm(plugin, player, "§c题目生成失败：" + result.getError(), () -> showSinglePlayerDifficultyForm(plugin, player));
                }
            });
        });
    }

    private static void showSinglePlayerConfirmForm(SituationPuzzleGame plugin, Player player,
                                                     String title, String truth, String difficulty) {
        CustomForm form = new CustomForm("§l§d单人模式 - AI 出题");
        form.header("§eAI 已生成题目：");
        form.label("§f汤面：§e" + title);
        form.divider();

        form.button("开始推理", p -> {
            GameRoom room = plugin.createSinglePlayerRoom(p, title, truth);
            if (room != null) {
                p.sendMessage("§a=============================");
                p.sendMessage("§6§l    海龟汤 - 单人模式");
                p.sendMessage("§a=============================");
                p.sendMessage("§e汤面：§f" + title);
                showSinglePlayerGameForm(plugin, p);
            } else {
                p.sendMessage("§c创建失败！");
            }
        });

        form.button("重新生成", p -> generateSinglePlayerPuzzle(plugin, p, difficulty));
        form.button("返回", p -> showMainMenu(plugin, p));
        form.show(player);
    }

    public static void showSinglePlayerGameForm(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(player)) return;
        GameRoom room = plugin.getPlayerRoom(player);
        if (room == null || !room.isSinglePlayer() || room.getState() != GameState.PLAYING) return;

        CustomForm form = new CustomForm("§l§6海龟汤 - 单人模式");

        form.header("§e汤面");
        form.label("§f" + room.getPuzzleTitle());
        form.divider();

        Observable<String> qaObs = new Observable<>(buildAnsweredQAText(room));
        form.label(qaObs);

        Observable<String> pendingObs = new Observable<>("");
        form.label(pendingObs);
        form.divider();

        if (!plugin.getPluginConfig().isAnswererEnabled()) {
            form.label("§c回答 AI 未配置，无法进行单人游戏");
            form.show(player);
            return;
        }

        Observable<String> questionObs = new Observable<>("", ObservableOptions.builder().clientWritable(true).build());
        form.textField("输入你的问题（是/否类问题）", questionObs);

        boolean[] waiting = {false};
        String[] animFrames = {"§e⏳ AI 正在思考", "§e⏳ AI 正在思考 §7.", "§e⏳ AI 正在思考 §7..", "§e⏳ AI 正在思考 §7..."};

        form.button("提交问题", p -> {
            if (waiting[0]) return;
            String question = questionObs.getValue();
            if (question == null || question.isBlank()) {
                p.sendMessage("§c问题不能为空！");
                return;
            }
            waiting[0] = true;
            GameRoom.Question q = room.askQuestion(p, question);
            if (plugin.getPluginConfig().isStatsEnabled()) {
                plugin.getStatsManager().getStats(p.getName()).recordSoloQuestion();
                plugin.getStatsManager().markDirty();
            }
            plugin.getServer().getScheduler().scheduleTask(plugin, () -> questionObs.setValue(""));

            String prefix = "§b你：§f" + question + "\n";
            pendingObs.setValue(prefix + animFrames[0]);
            int[] idx = {0};
            cn.nukkit.scheduler.TaskHandler animTask = plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, () -> {
                idx[0] = (idx[0] + 1) % animFrames.length;
                pendingObs.setValue(prefix + animFrames[idx[0]]);
            }, 10);

            plugin.getAiService().answerQuestion(room.getPuzzleTruth(), question)
                    .whenComplete((answer, ex) -> {
                        plugin.getServer().getScheduler().scheduleTask(plugin, () -> {
                            animTask.cancel();
                            if (!player.isConnected()) return;
                            waiting[0] = false;
                            if (ex != null) {
                                plugin.getLogger().error("AI 回答失败(单人)", ex);
                                pendingObs.setValue(prefix + "§cAI 回答失败：" + ex.getMessage());
                                return;
                            }
                            room.answerQuestion(q, answer);
                            plugin.recordQuestionAnswered(room, q);
                            qaObs.setValue(buildAnsweredQAText(room));
                            pendingObs.setValue("");
                        });
                    });
        });

        form.divider();
        form.button("放弃并查看真相", p -> {
            MessageBox confirm = new MessageBox("§l§c确认放弃");
            confirm.body("§e确定要放弃并查看真相吗？\n§7此操作不可撤销。");
            confirm.button1("确定放弃", cp -> {
                room.finishGame();
                if (plugin.getPluginConfig().isStatsEnabled()) {
                    plugin.getStatsManager().getStats(cp.getName()).recordSoloGameCompleted();
                    plugin.getStatsManager().markDirty();
                }
                cp.sendMessage("§a=============================");
                cp.sendMessage("§6§l    游戏结束！");
                cp.sendMessage("§a=============================");
                cp.sendMessage("§e汤底：§f" + room.getPuzzleTruth());
                showResultForm(plugin, cp, room);
            });
            confirm.button2("继续推理", cp -> showSinglePlayerGameForm(plugin, cp));
            confirm.show(p);
        });

        form.show(player);
    }

    private static String buildAnsweredQAText(GameRoom room) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (GameRoom.Question q : room.getQuestions()) {
            if (!q.isAnswered()) continue;
            count++;
            sb.append("§b").append(q.getAskerName()).append("§f：").append(q.getQuestion()).append("\n");
            sb.append("  §7→ ").append(q.getAnswer()).append("\n");
        }
        if (count == 0) return "";
        return "§e提问记录 (§f" + count + "个§e)\n" + sb;
    }

    private static void showHostAiAnswerLoading(SituationPuzzleGame plugin, Player player,
                                                 GameRoom room, GameRoom.Question current) {
        CustomForm form = new CustomForm("§l§6AI 正在回答");
        form.header("§b" + current.getAskerName() + " §f问：§e" + current.getQuestion());

        Observable<String> status = new Observable<>("");
        form.label(status);
        form.show(player);

        cn.nukkit.scheduler.TaskHandler animTask = startLoadingAnimation(plugin, status, "AI 正在思考");

        plugin.getAiService().answerQuestion(room.getPuzzleTruth(), current.getQuestion())
                .whenComplete((answer, ex) -> {
                    plugin.getServer().getScheduler().scheduleTask(plugin, () -> {
                        animTask.cancel();
                        if (!player.isConnected()) return;
                        if (ex != null) {
                            plugin.getLogger().error("AI 回答失败(多人)", ex);
                            animTask.cancel();
                            if (player.isConnected()) {
                                showErrorForm(plugin, player, "§cAI 回答失败：" + ex.getMessage(), () -> showHostGameForm(plugin, player, room));
                            }
                            return;
                        }
                        room.answerQuestion(current, answer);
                        plugin.recordQuestionAnswered(room, current);
                        notifyAnswer(room, current);
                        status.setValue("§f→ " + answer);
                        plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                            if (player.isConnected()) {
                                showHostGameForm(plugin, player, room);
                            }
                        }, 40);
                    });
                });
    }

    // ==================== 排行榜与统计 ====================

    public static void showLeaderboardMenu(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(player)) return;
        CustomForm form = new CustomForm("§l§6排行榜");
        form.header("§e选择排行类别");
        form.button("单人完成数", p -> showLeaderboard(plugin, p, "solo_completed"));
        form.button("多人完成数", p -> showLeaderboard(plugin, p, "multi_completed"));
        form.button("总游戏数", p -> showLeaderboard(plugin, p, "total_games"));
        form.button("提问次数", p -> showLeaderboard(plugin, p, "questions"));
        form.button("命中率 (最小10问)", p -> showLeaderboard(plugin, p, "hit_rate"));
        form.button("主持次数", p -> showLeaderboard(plugin, p, "host"));
        form.button("单人最佳连胜", p -> showLeaderboard(plugin, p, "solo_streak"));
        form.divider();
        form.button("返回", p -> showMainMenu(plugin, p));
        form.show(player);
    }

    public static void showLeaderboard(SituationPuzzleGame plugin, Player player, String metric) {
        if (!checkDduiSupport(player)) return;
        StatsManager sm = plugin.getStatsManager();
        int limit = plugin.getPluginConfig().getLeaderboardSize();
        int minQ = plugin.getPluginConfig().getMinQuestionsForHitRate();

        List<PlayerStats> top = switch (metric) {
            case "solo_completed" -> sm.getTopBySoloCompleted(limit);
            case "multi_completed" -> sm.getTopByMultiCompleted(limit);
            case "total_games" -> sm.getTopByTotalGames(limit);
            case "questions" -> sm.getTopByQuestionsAsked(limit);
            case "hit_rate" -> sm.getTopByHitRate(limit, minQ);
            case "host" -> sm.getTopByHostCount(limit);
            case "solo_streak" -> sm.getTopBySoloStreak(limit);
            default -> List.of();
        };

        String title = metricTitle(metric);
        CustomForm form = new CustomForm("§l§6排行榜 §f- §e" + title);

        if (top.isEmpty()) {
            form.label("§7暂无数据");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < top.size(); i++) {
                PlayerStats s = top.get(i);
                String medal = switch (i) {
                    case 0 -> "§6#1";
                    case 1 -> "§7#2";
                    case 2 -> "§e#3";
                    default -> "§f#" + (i + 1);
                };
                sb.append(medal).append(" §f").append(s.getPlayerName())
                        .append(" §7- §e").append(formatMetricValue(metric, s)).append("\n");
            }
            form.label(sb.toString());
        }

        form.divider();
        form.button("返回排行菜单", p -> showLeaderboardMenu(plugin, p));
        form.show(player);
    }

    public static void showPersonalStats(SituationPuzzleGame plugin, Player player) {
        if (!checkDduiSupport(player)) return;
        PlayerStats stats = plugin.getStatsManager().getStats(player.getName());

        CustomForm form = new CustomForm("§l§6我的统计 §f- §e" + player.getName());

        form.header("§e--- 单人模式 ---");
        form.label(
                "§f游戏次数：§e" + stats.getSoloGamesPlayed() + "\n" +
                "§f已完成：§a" + stats.getSoloGamesCompleted() + "\n" +
                "§f已放弃：§c" + stats.getSoloGamesAbandoned() + "\n" +
                "§f提问次数：§e" + stats.getSoloQuestionsAsked() + "\n" +
                "§f命中次数(是)：§a" + stats.getSoloQuestionsHit() + "\n" +
                "§f完成率：§e" + String.format("%.1f%%", stats.getSoloCompletionRate() * 100) + "\n" +
                "§f当前连胜：§6" + stats.getSoloCurrentStreak() + "\n" +
                "§f最佳连胜：§6" + stats.getSoloBestStreak());

        form.divider();
        form.header("§e--- 多人模式 ---");
        form.label(
                "§f游戏次数：§e" + stats.getMultiGamesPlayed() + "\n" +
                "§f已完成：§a" + stats.getMultiGamesCompleted() + "\n" +
                "§f已放弃：§c" + stats.getMultiGamesAbandoned() + "\n" +
                "§f提问次数：§e" + stats.getMultiQuestionsAsked() + "\n" +
                "§f命中次数(是)：§a" + stats.getMultiQuestionsHit() + "\n" +
                "§f完成率：§e" + String.format("%.1f%%", stats.getMultiCompletionRate() * 100));

        form.divider();
        form.header("§e--- 主持 ---");
        form.label("§f主持场次：§e" + stats.getHostCount());

        form.divider();
        form.button("关闭", p -> {});
        form.show(player);
    }

    private static String metricTitle(String metric) {
        return switch (metric) {
            case "solo_completed" -> "单人完成数";
            case "multi_completed" -> "多人完成数";
            case "total_games" -> "总游戏数";
            case "questions" -> "提问次数";
            case "hit_rate" -> "命中率";
            case "host" -> "主持次数";
            case "solo_streak" -> "单人最佳连胜";
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
        String[] frames = {
                "§e⏳ " + message,
                "§e⏳ " + message + " §7.",
                "§e⏳ " + message + " §7..",
                "§e⏳ " + message + " §7..."
        };
        status.setValue(frames[0]);
        int[] index = {0};
        return plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, () -> {
            index[0] = (index[0] + 1) % frames.length;
            status.setValue(frames[index[0]]);
        }, 10);
    }

    private static void notifyAnswer(GameRoom room, GameRoom.Question question) {
        for (Player p : room.getAllPlayers()) {
            p.sendMessage("§f" + question.getAskerName() + "的提问「§e"
                    + question.getQuestion() + "§f」→ " + question.getAnswer());
        }
    }

    private static void showErrorForm(SituationPuzzleGame plugin, Player player, String message, Runnable onBack) {
        CustomForm form = new CustomForm("§l§c错误");
        form.label(message);
        form.divider();
        form.button("返回", p -> onBack.run());
        form.button("关闭", p -> {});
        form.show(player);
    }
}
