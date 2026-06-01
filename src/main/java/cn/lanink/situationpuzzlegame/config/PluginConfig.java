package cn.lanink.situationpuzzlegame.config;

import cn.lanink.situationpuzzlegame.SituationPuzzleGame;
import cn.nukkit.utils.ConfigSection;

import java.util.ArrayList;
import java.util.List;

public class PluginConfig {

    private final SituationPuzzleGame plugin;

    public PluginConfig(SituationPuzzleGame plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
    }

    public List<String> getProviderNames() {
        ConfigSection section = plugin.getConfig().getSection("providers");
        if (section == null || section.isEmpty()) return List.of();
        return new ArrayList<>(section.getKeys(false));
    }

    public AiProviderConfig getProvider(String name) {
        if (name == null || name.isBlank()) return null;
        String basePath = "providers." + name;
        if (!plugin.getConfig().exists(basePath)) return null;
        return new AiProviderConfig(
                name,
                plugin.getConfig().getString(basePath + ".api-type", "openai"),
                plugin.getConfig().getString(basePath + ".api-url", "https://api.deepseek.com"),
                plugin.getConfig().getString(basePath + ".api-key", ""),
                plugin.getConfig().getString(basePath + ".model", "deepseek-v4-pro"),
                plugin.getConfig().getString(basePath + ".thinking-type", "disabled"),
                plugin.getConfig().getString(basePath + ".reasoning-effort", "high")
        );
    }

    public AiProviderConfig getGeneratorProvider() {
        return resolveSelectedProvider("generator.provider");
    }

    public AiProviderConfig getAnswererProvider() {
        return resolveSelectedProvider("answerer.provider");
    }

    private AiProviderConfig resolveSelectedProvider(String providerKeyPath) {
        String providerName = plugin.getConfig().getString(providerKeyPath, "");
        if (providerName != null && !providerName.isBlank()) {
            return getProvider(providerName);
        }
        return null;
    }

    // Generator
    public String getGeneratorSystemPrompt() {
        return plugin.getConfig().getString("generator.system-prompt", "");
    }

    public boolean isGeneratorEnabled() {
        AiProviderConfig provider = getGeneratorProvider();
        return provider != null && provider.hasApiKey();
    }

    // Difficulty
    public List<String> getDifficultyKeys() {
        ConfigSection section = plugin.getConfig().getSection("generator.difficulties");
        if (section == null || section.isEmpty()) return List.of();
        return new ArrayList<>(section.getKeys(false));
    }

    public String getDifficultyName(String key) {
        return plugin.getConfig().getString("generator.difficulties." + key + ".name", key);
    }

    public String getDifficultyStars(String key) {
        return plugin.getConfig().getString("generator.difficulties." + key + ".stars", "");
    }

    public String getDifficultyDescription(String key) {
        return plugin.getConfig().getString("generator.difficulties." + key + ".description", "");
    }

    public String getDifficultyPrompt(String key) {
        return plugin.getConfig().getString("generator.difficulties." + key + ".prompt", "");
    }

    // Answerer
    public String getAnswererSystemPrompt() {
        return plugin.getConfig().getString("answerer.system-prompt", "");
    }

    public boolean isAnswererEnabled() {
        AiProviderConfig provider = getAnswererProvider();
        return provider != null && provider.hasApiKey();
    }

    // Stats
    public boolean isStatsEnabled() {
        return plugin.getConfig().getBoolean("stats.enabled", true);
    }

    public int getLeaderboardSize() {
        return plugin.getConfig().getInt("stats.leaderboard-size", 10);
    }

    public int getStatsAutoSaveInterval() {
        return plugin.getConfig().getInt("stats.auto-save-interval", 300);
    }

    public int getMinQuestionsForHitRate() {
        return plugin.getConfig().getInt("stats.min-questions-for-hit-rate", 10);
    }

}
