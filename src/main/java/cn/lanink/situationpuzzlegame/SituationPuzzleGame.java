package cn.lanink.situationpuzzlegame;

import cn.lanink.situationpuzzlegame.ai.AiService;
import cn.lanink.situationpuzzlegame.cache.PuzzleCache;
import cn.lanink.situationpuzzlegame.command.MainCommand;
import cn.lanink.situationpuzzlegame.config.PluginConfig;
import cn.lanink.situationpuzzlegame.game.GameRoom;
import cn.lanink.situationpuzzlegame.game.GameState;
import cn.lanink.situationpuzzlegame.i18n.Texts;
import cn.lanink.situationpuzzlegame.stats.StatsManager;
import cn.lanink.situationpuzzlegame.ui.LegacyUIFactory;
import cn.nukkit.Player;
import cn.nukkit.ddui.DataDrivenScreen;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.lang.LangCode;
import cn.nukkit.lang.PluginI18n;
import cn.nukkit.lang.PluginI18nManager;
import cn.nukkit.plugin.PluginBase;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.Getter;

public class SituationPuzzleGame extends PluginBase implements Listener {

    private final Map<String, GameRoom> rooms = new LinkedHashMap<>();
    private final Map<Player, GameRoom> playerRooms = new LinkedHashMap<>();
    @Getter private PluginConfig pluginConfig;
    @Getter private AiService aiService;
    @Getter private PuzzleCache puzzleCache;
    @Getter private StatsManager statsManager;
    @Getter private PluginI18n i18n;
    @Getter private LegacyUIFactory legacyUIFactory;

    @Override
    public void onEnable() {
        i18n = PluginI18nManager.register(this);
        i18n.setFallbackLanguage(LangCode.zh_CN);
        syncAndReloadLocalLanguages();
        Texts.bind(i18n);
        pluginConfig = new PluginConfig(this);
        aiService = new AiService(pluginConfig);
        puzzleCache = new PuzzleCache(this);
        statsManager = new StatsManager(this);
        legacyUIFactory = new LegacyUIFactory(this);
        if (pluginConfig.isStatsEnabled()) {
            statsManager.scheduleAutoSave(this, pluginConfig.getStatsAutoSaveInterval());
        }

        getServer().getCommandMap().register("SituationPuzzleGame", new MainCommand(this));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(legacyUIFactory, this);
        getLogger().info(Texts.t(this, LangCode.zh_CN, "log.plugin.enabled"));
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
        if (legacyUIFactory != null) {
            legacyUIFactory.clearAll();
        }
        getLogger().info(Texts.t(this, LangCode.zh_CN, "log.plugin.disabled"));
    }

    private void syncAndReloadLocalLanguages() {
        int updatedFiles = 0;
        int addedKeys = 0;
        try (JarFile jarFile = new JarFile(getFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String resourcePath = entry.getName();
                if (entry.isDirectory()
                        || !resourcePath.startsWith("language/")
                        || !resourcePath.endsWith(".lang")) {
                    continue;
                }

                LanguageSyncResult result = syncLocalLanguageFile(resourcePath);
                if (i18n.reloadLang(result.langCode(), result.localFile().getPath())) {
                    if (result.addedKeys() > 0) {
                        updatedFiles++;
                        addedKeys += result.addedKeys();
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            getLogger().warning("自动补全本地语言文件失败，将使用 jar 内置语言文件。", e);
            return;
        }

        if (addedKeys > 0) {
            getLogger()
                    .info(
                            "已自动补全本地语言文件："
                                    + updatedFiles
                                    + " 个文件，新增 "
                                    + addedKeys
                                    + " 个文本项。");
        }
    }

    private LanguageSyncResult syncLocalLanguageFile(String resourcePath) throws IOException {
        LangCode langCode = parseLanguageCode(resourcePath);
        List<String> bundledLines = readBundledLanguageLines(resourcePath);
        List<LanguageLine> bundledEntries = parseLanguageLines(bundledLines);
        File localFile = new File(getDataFolder(), resourcePath);
        Path localPath = localFile.toPath();

        if (!localFile.exists()) {
            Files.createDirectories(localPath.getParent());
            Files.write(localPath, bundledLines, StandardCharsets.UTF_8);
            return new LanguageSyncResult(langCode, localFile, bundledEntries.size());
        }

        Set<String> existingKeys =
                parseLanguageKeys(Files.readAllLines(localPath, StandardCharsets.UTF_8));
        List<String> missingLines = new ArrayList<>();
        for (LanguageLine bundledEntry : bundledEntries) {
            if (!existingKeys.contains(bundledEntry.key())) {
                missingLines.add(bundledEntry.line());
            }
        }
        if (missingLines.isEmpty()) {
            return new LanguageSyncResult(langCode, localFile, 0);
        }

        StringBuilder appendText = new StringBuilder();
        if (Files.size(localPath) > 0 && !endsWithLineBreak(localPath)) {
            appendText.append(System.lineSeparator());
        }
        for (String missingLine : missingLines) {
            appendText.append(missingLine).append(System.lineSeparator());
        }
        Files.writeString(
                localPath,
                appendText.toString(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        return new LanguageSyncResult(langCode, localFile, missingLines.size());
    }

    private LangCode parseLanguageCode(String resourcePath) {
        String fileName = resourcePath.substring("language/".length());
        return LangCode.valueOf(fileName.substring(0, fileName.length() - ".lang".length()));
    }

    private List<String> readBundledLanguageLines(String resourcePath) throws IOException {
        try (InputStream inputStream = getResource(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing bundled language resource: " + resourcePath);
            }
            return Arrays.asList(
                    new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                            .split("\\R", -1));
        }
    }

    private List<LanguageLine> parseLanguageLines(List<String> lines) {
        List<LanguageLine> result = new ArrayList<>();
        for (String line : lines) {
            String key = parseLanguageKey(line);
            if (key != null) {
                result.add(new LanguageLine(key, line));
            }
        }
        return result;
    }

    private Set<String> parseLanguageKeys(List<String> lines) {
        Set<String> result = new HashSet<>();
        for (String line : lines) {
            String key = parseLanguageKey(line);
            if (key != null) {
                result.add(key);
            }
        }
        return result;
    }

    private String parseLanguageKey(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
            return null;
        }
        int separator = trimmed.indexOf('=');
        if (separator <= 0) {
            return null;
        }
        return trimmed.substring(0, separator);
    }

    private boolean endsWithLineBreak(Path path) throws IOException {
        byte[] content = Files.readAllBytes(path);
        if (content.length == 0) {
            return true;
        }
        byte last = content[content.length - 1];
        return last == '\n' || last == '\r';
    }

    private record LanguageLine(String key, String line) {}

    private record LanguageSyncResult(LangCode langCode, File localFile, int addedKeys) {}

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
        room.setLanguageCode(Texts.lang(host));
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
        GameRoom room =
                new GameRoom(player.getName() + "-solo", player, puzzleTitle, puzzleTruth, true);
        room.setLanguageCode(Texts.lang(player));
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
        if (rooms.get(room.getRoomId()) != room) return false;
        if (room.getState() != GameState.WAITING) return false;
        if (room.hasPlayer(player)) return false;
        room.addPlayer(player);
        playerRooms.put(player, room);
        return true;
    }

    public void leaveRoom(Player player) {
        GameRoom room = playerRooms.remove(player);
        if (room == null) return;
        if (legacyUIFactory != null) {
            legacyUIFactory.clearPlayer(player);
        }
        if (pluginConfig.isStatsEnabled()
                && room.getState() == GameState.PLAYING
                && !room.isSinglePlayer()) {
            statsManager.getStats(player.getName()).recordMultiGameAbandoned();
            statsManager.markDirty();
        }
        room.removePlayer(player);
        for (Player p : room.getAllPlayers()) {
            if (p.isConnected()) {
                p.sendMessage(Texts.t(this, p, "message.player-left-room", player.getName()));
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
            if (legacyUIFactory != null) {
                legacyUIFactory.clearPlayer(p);
            }
            if (p.isConnected()) {
                p.sendMessage(Texts.t(this, p, "message.room-destroyed"));
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

    public void recordQuestionAnswered(GameRoom room, GameRoom.Question question) {
        if (!pluginConfig.isStatsEnabled()) return;
        if (question.getAnswerType() != GameRoom.AnswerType.YES) return;
        String askerName = question.getAskerName();
        if (room.isSinglePlayer()) {
            statsManager.getStats(askerName).recordSoloHit();
        } else {
            statsManager.getStats(askerName).recordMultiHit();
        }
        statsManager.markDirty();
    }
}
