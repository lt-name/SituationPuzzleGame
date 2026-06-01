package cn.lanink.situationpuzzlegame;

import cn.nukkit.Player;
import cn.nukkit.ddui.DataDrivenScreen;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.plugin.PluginBase;
import cn.lanink.situationpuzzlegame.ai.AiService;
import cn.lanink.situationpuzzlegame.cache.PuzzleCache;
import cn.lanink.situationpuzzlegame.command.MainCommand;
import cn.lanink.situationpuzzlegame.config.PluginConfig;
import cn.lanink.situationpuzzlegame.game.GameRoom;
import cn.lanink.situationpuzzlegame.game.GameState;
import cn.lanink.situationpuzzlegame.stats.StatsManager;

import java.util.*;

public class SituationPuzzleGame extends PluginBase implements Listener {

    private final Map<String, GameRoom> rooms = new LinkedHashMap<>();
    private final Map<Player, GameRoom> playerRooms = new LinkedHashMap<>();
    private PluginConfig pluginConfig;
    private AiService aiService;
    private PuzzleCache puzzleCache;
    private StatsManager statsManager;

    @Override
    public void onEnable() {
        pluginConfig = new PluginConfig(this);
        aiService = new AiService(pluginConfig);
        puzzleCache = new PuzzleCache(this);
        statsManager = new StatsManager(this);
        if (pluginConfig.isStatsEnabled()) {
            statsManager.scheduleAutoSave(this, pluginConfig.getStatsAutoSaveInterval());
        }

        getServer().getCommandMap().register("SituationPuzzleGame", new MainCommand(this));
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SituationPuzzleGame 插件已启用！");
    }

    @Override
    public void onDisable() {
        for (GameRoom room : new ArrayList<>(rooms.values())) {
            destroyRoom(room);
        }
        if (puzzleCache != null) {
            puzzleCache.save();
        }
        if (statsManager != null) {
            statsManager.save();
        }
        getLogger().info("SituationPuzzleGame 插件已卸载！");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameRoom room = playerRooms.get(player);
        if (room == null) return;

        if (room.isHost(player)) {
            destroyRoom(room);
        } else {
            leaveRoom(player);
        }
    }

    public GameRoom createRoom(Player host, String puzzleTitle, String puzzleTruth) {
        if (playerRooms.containsKey(host)) return null;
        GameRoom room = new GameRoom(host.getName(), host, puzzleTitle, puzzleTruth);
        rooms.put(room.getRoomId(), room);
        playerRooms.put(host, room);
        if (pluginConfig.isStatsEnabled()) {
            statsManager.getStats(host.getName()).recordHostGame();
            statsManager.markDirty();
        }
        return room;
    }

    public GameRoom createSinglePlayerRoom(Player player, String puzzleTitle, String puzzleTruth) {
        if (playerRooms.containsKey(player)) return null;
        GameRoom room = new GameRoom(player.getName() + "-solo", player, puzzleTitle, puzzleTruth, true);
        rooms.put(room.getRoomId(), room);
        playerRooms.put(player, room);
        room.startGame();
        if (pluginConfig.isStatsEnabled()) {
            statsManager.getStats(player.getName()).recordSoloGamePlayed();
            statsManager.markDirty();
        }
        return room;
    }

    public boolean joinRoom(Player player, GameRoom room) {
        if (playerRooms.containsKey(player)) return false;
        if (room.getState() != GameState.WAITING) return false;
        if (room.hasPlayer(player)) return false;
        room.addPlayer(player);
        playerRooms.put(player, room);
        return true;
    }

    public void leaveRoom(Player player) {
        GameRoom room = playerRooms.remove(player);
        if (room == null) return;
        if (pluginConfig.isStatsEnabled() && room.getState() == GameState.PLAYING && !room.isSinglePlayer()) {
            statsManager.getStats(player.getName()).recordMultiGameAbandoned();
            statsManager.markDirty();
        }
        room.removePlayer(player);
        for (Player p : room.getAllPlayers()) {
            if (p.isConnected()) {
                p.sendMessage("§e" + player.getName() + " 离开了房间");
            }
        }
    }

    public void destroyRoom(GameRoom room) {
        if (pluginConfig.isStatsEnabled() && room.getState() == GameState.PLAYING) {
            for (Player p : room.getAllPlayers()) {
                if (room.isSinglePlayer()) {
                    statsManager.getStats(p.getName()).recordSoloGameAbandoned();
                } else {
                    statsManager.getStats(p.getName()).recordMultiGameAbandoned();
                }
            }
            statsManager.markDirty();
        }
        rooms.remove(room.getRoomId());
        for (Player p : new ArrayList<>(room.getAllPlayers())) {
            playerRooms.remove(p);
            if (p.isConnected()) {
                p.sendMessage("§c房间已解散！");
                DataDrivenScreen.removeActiveScreen(p);
            }
        }
    }

    public GameRoom getPlayerRoom(Player player) {
        return playerRooms.get(player);
    }

    public List<GameRoom> getAvailableRooms() {
        return rooms.values().stream()
                .filter(r -> !r.isSinglePlayer())
                .filter(r -> r.getState() == GameState.WAITING)
                .toList();
    }

    public PluginConfig getPluginConfig() { return pluginConfig; }
    public AiService getAiService() { return aiService; }
    public PuzzleCache getPuzzleCache() { return puzzleCache; }
    public StatsManager getStatsManager() { return statsManager; }

    public void recordQuestionAnswered(GameRoom room, GameRoom.Question question) {
        if (!pluginConfig.isStatsEnabled()) return;
        String answer = question.getAnswer();
        if (answer == null || !answer.startsWith("§a是")) return;
        String askerName = question.getAskerName();
        if (room.isSinglePlayer()) {
            statsManager.getStats(askerName).recordSoloHit();
        } else {
            statsManager.getStats(askerName).recordMultiHit();
        }
        statsManager.markDirty();
    }
}
