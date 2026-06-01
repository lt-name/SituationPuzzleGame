package cn.lanink.situationpuzzlegame.stats;

import cn.lanink.situationpuzzlegame.SituationPuzzleGame;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {

    private final Config statsConfig;
    private final Map<String, PlayerStats> cache = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public StatsManager(SituationPuzzleGame plugin) {
        File statsFile = new File(plugin.getDataFolder(), "stats.yml");
        this.statsConfig = new Config(statsFile, Config.YAML);
        loadAll();
    }

    private void loadAll() {
        for (String key : statsConfig.getKeys(false)) {
            ConfigSection section = statsConfig.getSection(key);
            if (section == null || section.isEmpty()) continue;
            cache.put(key.toLowerCase(), new PlayerStats(key, section));
        }
    }

    public void save() {
        if (!dirty) return;
        for (Map.Entry<String, PlayerStats> entry : cache.entrySet()) {
            ConfigSection section = new ConfigSection();
            entry.getValue().saveTo(section);
            statsConfig.set(entry.getValue().getPlayerName(), section.getAllMap());
        }
        statsConfig.save();
        dirty = false;
    }

    public void scheduleAutoSave(SituationPuzzleGame plugin, int intervalSeconds) {
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, this::save, intervalSeconds * 20);
    }

    public PlayerStats getStats(String playerName) {
        return cache.computeIfAbsent(playerName.toLowerCase(), k -> new PlayerStats(playerName));
    }

    public void markDirty() {
        dirty = true;
    }

    // --- 排行榜查询 ---

    public List<PlayerStats> getTopBySoloCompleted(int limit) {
        return cache.values().stream()
                .filter(s -> s.getSoloGamesCompleted() > 0)
                .sorted(Comparator.comparingInt(PlayerStats::getSoloGamesCompleted).reversed()
                        .thenComparing(Comparator.comparingLong(PlayerStats::getLastActiveTime).reversed()))
                .limit(limit)
                .toList();
    }

    public List<PlayerStats> getTopByMultiCompleted(int limit) {
        return cache.values().stream()
                .filter(s -> s.getMultiGamesCompleted() > 0)
                .sorted(Comparator.comparingInt(PlayerStats::getMultiGamesCompleted).reversed()
                        .thenComparing(Comparator.comparingLong(PlayerStats::getLastActiveTime).reversed()))
                .limit(limit)
                .toList();
    }

    public List<PlayerStats> getTopByTotalGames(int limit) {
        return cache.values().stream()
                .filter(s -> s.getTotalGamesPlayed() > 0)
                .sorted(Comparator.comparingInt(PlayerStats::getTotalGamesPlayed).reversed()
                        .thenComparing(Comparator.comparingLong(PlayerStats::getLastActiveTime).reversed()))
                .limit(limit)
                .toList();
    }

    public List<PlayerStats> getTopByQuestionsAsked(int limit) {
        return cache.values().stream()
                .filter(s -> s.getTotalQuestionsAsked() > 0)
                .sorted(Comparator.comparingInt(PlayerStats::getTotalQuestionsAsked).reversed()
                        .thenComparing(Comparator.comparingLong(PlayerStats::getLastActiveTime).reversed()))
                .limit(limit)
                .toList();
    }

    public List<PlayerStats> getTopByHitRate(int limit, int minQuestions) {
        return cache.values().stream()
                .filter(s -> s.getTotalQuestionsAsked() >= minQuestions)
                .sorted(Comparator.comparingDouble(PlayerStats::getHitRate).reversed()
                        .thenComparing(Comparator.comparingLong(PlayerStats::getLastActiveTime).reversed()))
                .limit(limit)
                .toList();
    }

    public List<PlayerStats> getTopByHostCount(int limit) {
        return cache.values().stream()
                .filter(s -> s.getHostCount() > 0)
                .sorted(Comparator.comparingInt(PlayerStats::getHostCount).reversed()
                        .thenComparing(Comparator.comparingLong(PlayerStats::getLastActiveTime).reversed()))
                .limit(limit)
                .toList();
    }

    public List<PlayerStats> getTopBySoloStreak(int limit) {
        return cache.values().stream()
                .filter(s -> s.getSoloBestStreak() > 0)
                .sorted(Comparator.comparingInt(PlayerStats::getSoloBestStreak).reversed()
                        .thenComparing(Comparator.comparingLong(PlayerStats::getLastActiveTime).reversed()))
                .limit(limit)
                .toList();
    }
}
