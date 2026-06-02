package cn.lanink.situationpuzzlegame.config;

import cn.lanink.situationpuzzlegame.SituationPuzzleGame;
import cn.lanink.situationpuzzlegame.i18n.Texts;
import cn.nukkit.lang.LangCode;
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
                plugin.getConfig().getString(basePath + ".reasoning-effort", "high"));
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
    public String getGeneratorSystemPrompt(LangCode lang) {
        return prompt(lang, "ai.generator.system-prompt");
    }

    public String getGeneratorUserPrompt(LangCode lang) {
        return prompt(lang, "ai.generator.user-prompt");
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

    public String getDifficultyName(String key, LangCode lang) {
        String configured =
                plugin
                        .getConfig()
                        .getString("generator.difficulties." + key + ".name-" + lang.name(), null);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String translated = Texts.t(plugin, lang, "difficulty." + key + ".name");
        return translated.equals("difficulty." + key + ".name") ? getDifficultyName(key) : translated;
    }

    public String getDifficultyStars(String key) {
        return plugin.getConfig().getString("generator.difficulties." + key + ".stars", "");
    }

    public String getDifficultyDescription(String key) {
        return plugin.getConfig().getString("generator.difficulties." + key + ".description", "");
    }

    public String getDifficultyDescription(String key, LangCode lang) {
        String configured =
                plugin
                        .getConfig()
                        .getString("generator.difficulties." + key + ".description-" + lang.name(), null);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String translated = Texts.t(plugin, lang, "difficulty." + key + ".description");
        return translated.equals("difficulty." + key + ".description")
                ? getDifficultyDescription(key)
                : translated;
    }

    public String getDifficultyPrompt(String key, LangCode lang) {
        return prompt(lang, "ai.generator.difficulty." + key + ".prompt");
    }

    // Answerer
    public String getAnswererSystemPrompt(LangCode lang) {
        return prompt(lang, "ai.answerer.system-prompt");
    }

    public String getAnswererUserPrompt(LangCode lang, String truth, String question) {
        return prompt(lang, "ai.answerer.user-prompt", truth, question);
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

    // Cache
    public boolean isCacheEnabled() {
        return plugin.getConfig().getBoolean("cache.enabled", true);
    }

    public int getCacheMaxPerDifficulty() {
        return plugin.getConfig().getInt("cache.max-per-difficulty", 20);
    }

    private String prompt(LangCode lang, String key, Object... args) {
        String translated = Texts.t(plugin, lang, key, args);
        if (!translated.equals(key)) {
            return translated;
        }
        LangCode normalized = Texts.normalize(lang);
        if (normalized != LangCode.zh_CN) {
            translated = Texts.t(plugin, LangCode.zh_CN, key, args);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        throw new IllegalStateException("缺少语言文件提示词: " + key);
    }
}
