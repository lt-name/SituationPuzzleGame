package cn.lanink.situationpuzzlegame.cache;

import cn.lanink.situationpuzzlegame.SituationPuzzleGame;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PuzzleCache {

    private final SituationPuzzleGame plugin;
    private final Config cacheConfig;
    private final Map<String, Deque<CachedPuzzle>> cache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> seenHistory = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public PuzzleCache(SituationPuzzleGame plugin) {
        this.plugin = plugin;
        File cacheFile = new File(plugin.getDataFolder(), "puzzle_cache.yml");
        this.cacheConfig = new Config(cacheFile, Config.YAML);
        loadAll();
    }

    private void loadAll() {
        for (String difficulty : cacheConfig.getKeys(false)) {
            if ("seen".equals(difficulty)) continue;
            Object value = cacheConfig.get(difficulty);
            if (!(value instanceof List)) continue;
            List<?> rawList = (List<?>) value;
            Deque<CachedPuzzle> deque = new ConcurrentLinkedDeque<>();
            for (Object item : rawList) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> map = (Map<?, ?>) item;
                String title = (String) map.get("title");
                String truth = (String) map.get("truth");
                if (title != null && truth != null && !title.isBlank() && !truth.isBlank()) {
                    deque.add(new CachedPuzzle(title, truth));
                }
            }
            if (!deque.isEmpty()) {
                cache.put(difficulty, deque);
            }
        }

        Object seenObj = cacheConfig.get("seen");
        if (seenObj instanceof Map) {
            Map<?, ?> seenMap = (Map<?, ?>) seenObj;
            for (Map.Entry<?, ?> entry : seenMap.entrySet()) {
                String playerName = ((String) entry.getKey()).toLowerCase();
                if (entry.getValue() instanceof List) {
                    List<?> titles = (List<?>) entry.getValue();
                    Set<String> titleSet = ConcurrentHashMap.newKeySet();
                    for (Object t : titles) {
                        if (t instanceof String s && !s.isBlank()) titleSet.add(s);
                    }
                    if (!titleSet.isEmpty()) {
                        seenHistory.put(playerName, titleSet);
                    }
                }
            }
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        for (Map.Entry<String, Deque<CachedPuzzle>> entry : cache.entrySet()) {
            List<Map<String, String>> list = new ArrayList<>();
            for (CachedPuzzle p : entry.getValue()) {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("title", p.title);
                map.put("truth", p.truth);
                list.add(map);
            }
            cacheConfig.set(entry.getKey(), list);
        }

        Map<String, List<String>> seenMap = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : seenHistory.entrySet()) {
            seenMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        cacheConfig.set("seen", seenMap);

        cacheConfig.save();
        dirty = false;
    }

    public CachedPuzzle getPuzzleForPlayer(String difficulty, String playerName) {
        Deque<CachedPuzzle> deque = cache.get(difficulty);
        if (deque == null || deque.isEmpty()) return null;

        Set<String> seen = seenHistory.getOrDefault(playerName.toLowerCase(), Set.of());
        List<CachedPuzzle> skipped = new ArrayList<>();
        CachedPuzzle found = null;

        CachedPuzzle current;
        while ((current = deque.poll()) != null) {
            if (found == null && !seen.contains(current.title())) {
                found = current;
            } else {
                skipped.add(current);
            }
        }
        deque.addAll(skipped);

        if (found != null) {
            markAsSeen(playerName, found.title());
        }
        return found;
    }

    public void markAsSeen(String playerName, String title) {
        seenHistory.computeIfAbsent(playerName.toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(title);
        dirty = true;
    }

    public void addPuzzle(String difficulty, String title, String truth) {
        int max = plugin.getPluginConfig().getCacheMaxPerDifficulty();
        Deque<CachedPuzzle> deque = cache.computeIfAbsent(difficulty, k -> new ConcurrentLinkedDeque<>());
        if (deque.size() >= max) return;
        deque.add(new CachedPuzzle(title, truth));
        dirty = true;
    }

    public int getCacheSize(String difficulty) {
        Deque<CachedPuzzle> deque = cache.get(difficulty);
        return deque == null ? 0 : deque.size();
    }

    public record CachedPuzzle(String title, String truth) {}
}
