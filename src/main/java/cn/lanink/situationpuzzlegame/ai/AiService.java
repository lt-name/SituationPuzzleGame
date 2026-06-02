package cn.lanink.situationpuzzlegame.ai;

import cn.lanink.situationpuzzlegame.config.AiProviderConfig;
import cn.lanink.situationpuzzlegame.config.PluginConfig;
import cn.lanink.situationpuzzlegame.game.GameRoom;
import cn.nukkit.lang.LangCode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AiService {

    private static final String API_TYPE_OPENAI = "openai";
    private static final String API_TYPE_ANTHROPIC = "anthropic";
    private static final String OPENCODE_GO_MODEL_PREFIX = "opencode-go/";
    private static final String OPENCODE_GO_BASE_URL = "https://opencode.ai/zen/go/v1";
    private static final Set<String> OPENCODE_GO_OPENAI_MODELS =
            Set.of(
                    "glm-5.1",
                    "glm-5",
                    "kimi-k2.5",
                    "kimi-k2.6",
                    "deepseek-v4-pro",
                    "deepseek-v4-flash",
                    "mimo-v2.5",
                    "mimo-v2.5-pro");
    private static final Set<String> OPENCODE_GO_ANTHROPIC_MODELS =
            Set.of("minimax-m3", "minimax-m2.7", "minimax-m2.5", "qwen3.7-max", "qwen3.6-plus");

    private final PluginConfig config;
    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    public CompletableFuture<GenerateResult> generatePuzzle(String difficultyPrompt) {
        return generatePuzzle(difficultyPrompt, LangCode.zh_CN);
    }

    public CompletableFuture<GenerateResult> generatePuzzle(String difficultyPrompt, LangCode lang) {
        try {
            AiProviderConfig provider = requireProvider(config.getGeneratorProvider(), "generator");
            String apiUrl = provider.getApiUrl();
            String configuredModel = provider.getModel();
            String model = normalizeModel(configuredModel);
            boolean openCodeGoProvider = isOpenCodeGoConfig(configuredModel, apiUrl);
            String requestApiType =
                    resolveRequestApiType(provider.getApiType(), model, apiUrl, openCodeGoProvider);
            JsonObject body = new JsonObject();
            body.addProperty("model", model);

            String systemPrompt = config.getGeneratorSystemPrompt(lang);
            if (difficultyPrompt != null && !difficultyPrompt.isBlank()) {
                systemPrompt += "\n\n" + difficultyPrompt;
            }
            applySystemAndMessages(
                    body, systemPrompt, config.getGeneratorUserPrompt(lang), requestApiType);
            applyThinking(
                    body,
                    provider.getThinkingType(),
                    provider.getReasoningEffort(),
                    requestApiType,
                    openCodeGoProvider);
            applyMaxTokens(body, requestApiType);

            return callApi(apiUrl, provider.getApiKey(), body, requestApiType, openCodeGoProvider)
                    .orTimeout(120, TimeUnit.SECONDS)
                    .thenApply(response -> parseGenerateResponse(response, requestApiType))
                    .exceptionally(ex -> GenerateResult.fail("API调用失败: " + ex.getMessage()));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(GenerateResult.fail("请求构建失败: " + e.getMessage()));
        }
    }

    public CompletableFuture<GameRoom.AnswerType> answerQuestion(String truth, String question) {
        return answerQuestion(truth, question, LangCode.zh_CN);
    }

    public CompletableFuture<GameRoom.AnswerType> answerQuestion(
            String truth, String question, LangCode lang) {
        try {
            AiProviderConfig provider = requireProvider(config.getAnswererProvider(), "answerer");
            String apiUrl = provider.getApiUrl();
            String configuredModel = provider.getModel();
            String model = normalizeModel(configuredModel);
            boolean openCodeGoProvider = isOpenCodeGoConfig(configuredModel, apiUrl);
            String requestApiType =
                    resolveRequestApiType(provider.getApiType(), model, apiUrl, openCodeGoProvider);
            JsonObject body = new JsonObject();
            body.addProperty("model", model);

            applySystemAndMessages(
                    body,
                    config.getAnswererSystemPrompt(lang),
                    config.getAnswererUserPrompt(lang, truth, question),
                    requestApiType);
            applyThinking(
                    body,
                    provider.getThinkingType(),
                    provider.getReasoningEffort(),
                    requestApiType,
                    openCodeGoProvider);
            applyMaxTokens(body, requestApiType);

            return callApi(apiUrl, provider.getApiKey(), body, requestApiType, openCodeGoProvider)
                    .orTimeout(60, TimeUnit.SECONDS)
                    .thenApply(response -> parseAnswerResponse(response, requestApiType));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<SoloAnswerResult> answerSoloQuestion(
            String truth, String question, LangCode lang) {
        try {
            AiProviderConfig provider = requireProvider(config.getAnswererProvider(), "answerer");
            String apiUrl = provider.getApiUrl();
            String configuredModel = provider.getModel();
            String model = normalizeModel(configuredModel);
            boolean openCodeGoProvider = isOpenCodeGoConfig(configuredModel, apiUrl);
            String requestApiType =
                    resolveRequestApiType(provider.getApiType(), model, apiUrl, openCodeGoProvider);
            JsonObject body = new JsonObject();
            body.addProperty("model", model);

            applySystemAndMessages(
                    body,
                    config.getSoloAnswererSystemPrompt(lang),
                    config.getSoloAnswererUserPrompt(lang, truth, question),
                    requestApiType);
            applyThinking(
                    body,
                    provider.getThinkingType(),
                    provider.getReasoningEffort(),
                    requestApiType,
                    openCodeGoProvider);
            applyMaxTokens(body, requestApiType);

            return callApi(apiUrl, provider.getApiKey(), body, requestApiType, openCodeGoProvider)
                    .orTimeout(60, TimeUnit.SECONDS)
                    .thenApply(response -> parseSoloAnswerResponse(response, requestApiType));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void applySystemAndMessages(
            JsonObject body, String systemPrompt, String userPrompt, String apiType) {
        if (API_TYPE_ANTHROPIC.equals(apiType)) {
            body.addProperty("system", systemPrompt);
            JsonArray messages = new JsonArray();
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", userPrompt);
            messages.add(user);
            body.add("messages", messages);
        } else {
            JsonArray messages = new JsonArray();
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", systemPrompt);
            messages.add(system);
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", userPrompt);
            messages.add(user);
            body.add("messages", messages);
        }
    }

    private void applyMaxTokens(JsonObject body, String apiType) {
        if (API_TYPE_ANTHROPIC.equals(apiType) && !body.has("max_tokens")) {
            body.addProperty("max_tokens", 4096);
        }
    }

    private void applyThinking(
            JsonObject body,
            String thinkingType,
            String reasoningEffort,
            String requestApiType,
            boolean openCodeGoProvider) {
        if (thinkingType == null || "disabled".equals(thinkingType)) return;
        if (openCodeGoProvider) return;

        if (API_TYPE_ANTHROPIC.equals(requestApiType)) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", thinkingType);
            thinking.addProperty("budget_tokens", 10000);
            body.add("thinking", thinking);
            body.remove("max_tokens");
            body.addProperty("max_tokens", 16000);
        } else {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", thinkingType);
            body.add("thinking", thinking);
            if (reasoningEffort != null && !reasoningEffort.isBlank()) {
                body.addProperty("reasoning_effort", reasoningEffort);
            }
        }
    }

    private CompletableFuture<String> callApi(
            String url,
            String apiKey,
            JsonObject body,
            String requestApiType,
            boolean openCodeGoProvider) {
        url = normalizeApiUrl(url, requestApiType, openCodeGoProvider);
        HttpRequest.Builder requestBuilder =
                HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json");

        if (API_TYPE_ANTHROPIC.equals(requestApiType)) {
            requestBuilder.header("x-api-key", apiKey);
            requestBuilder.header("anthropic-version", "2023-06-01");
        } else {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request =
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body))).build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(
                        response -> {
                            if (response.statusCode() >= 400) {
                                throw new RuntimeException(
                                        "HTTP " + response.statusCode() + ": " + response.body());
                            }
                            return response.body();
                        });
    }

    private GenerateResult parseGenerateResponse(String responseBody, String apiType) {
        try {
            String content = extractContent(responseBody, apiType);
            String jsonStr = extractJson(content);
            JsonObject parsed = JsonParser.parseString(jsonStr).getAsJsonObject();
            String title = parsed.get("title").getAsString();
            String truth = parsed.get("truth").getAsString();
            return GenerateResult.success(title, truth);
        } catch (Exception e) {
            return GenerateResult.fail("解析AI响应失败: " + e.getMessage());
        }
    }

    private GameRoom.AnswerType parseAnswerResponse(String responseBody, String apiType) {
        try {
            String content = extractContent(responseBody, apiType).trim();
            return parseAnswerType(content);
        } catch (Exception e) {
            throw new RuntimeException("解析回答失败: " + e.getMessage(), e);
        }
    }

    private SoloAnswerResult parseSoloAnswerResponse(String responseBody, String apiType) {
        try {
            String content = extractContent(responseBody, apiType).trim();
            String jsonStr = extractJson(content);
            JsonObject parsed = JsonParser.parseString(jsonStr).getAsJsonObject();
            GameRoom.AnswerType answerType = parseAnswerType(parsed.get("answer").getAsString());
            boolean solved = parsed.has("solved") && parsed.get("solved").getAsBoolean();
            return new SoloAnswerResult(answerType, solved);
        } catch (Exception e) {
            throw new RuntimeException("解析单人回答失败: " + e.getMessage(), e);
        }
    }

    private GameRoom.AnswerType parseAnswerType(String content) {
        String normalized = content.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}§]+", "");
        if (normalized.contains("irrelevant")
                || normalized.contains("unrelated")
                || content.contains("无关")
                || content.contains("不相关")) {
            return GameRoom.AnswerType.IRRELEVANT;
        } else if (normalized.contains("no") || content.contains("不是") || content.contains("否")) {
            return GameRoom.AnswerType.NO;
        } else if (normalized.contains("yes") || content.contains("是")) {
            return GameRoom.AnswerType.YES;
        }
        return GameRoom.AnswerType.IRRELEVANT;
    }

    private String extractContent(String responseBody, String apiType) {
        JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
        if (API_TYPE_ANTHROPIC.equals(apiType)) {
            JsonArray content = response.getAsJsonArray("content");
            for (var element : content) {
                JsonObject block = element.getAsJsonObject();
                if ("text".equals(block.get("type").getAsString())) {
                    return block.get("text").getAsString();
                }
            }
            throw new RuntimeException("No text content in response");
        } else {
            return response
                    .getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content")
                    .getAsString();
        }
    }

    private String normalizeApiType(String apiType) {
        if (apiType == null || apiType.isBlank()) return API_TYPE_OPENAI;
        apiType = apiType.trim().toLowerCase(Locale.ROOT);
        if (API_TYPE_OPENAI.equals(apiType) || API_TYPE_ANTHROPIC.equals(apiType)) {
            return apiType;
        }
        throw new IllegalArgumentException("不支持的 api-type: " + apiType + "，仅支持 openai 或 anthropic");
    }

    private String normalizeModel(String model) {
        if (model == null) return "";
        model = model.trim();
        if (model.startsWith(OPENCODE_GO_MODEL_PREFIX)) {
            return model.substring(OPENCODE_GO_MODEL_PREFIX.length());
        }
        return model;
    }

    private String resolveRequestApiType(
            String apiType, String model, String url, boolean openCodeGoProvider) {
        if (isMessagesEndpoint(url)) return API_TYPE_ANTHROPIC;
        if (isChatCompletionsEndpoint(url)) return API_TYPE_OPENAI;
        if (openCodeGoProvider) {
            String modelKey = model.toLowerCase(Locale.ROOT);
            if (OPENCODE_GO_ANTHROPIC_MODELS.contains(modelKey)) return API_TYPE_ANTHROPIC;
            if (OPENCODE_GO_OPENAI_MODELS.contains(modelKey)) return API_TYPE_OPENAI;
        }
        return normalizeApiType(apiType);
    }

    private boolean isMessagesEndpoint(String url) {
        return trimTrailingSlash(url).endsWith("/messages");
    }

    private boolean isChatCompletionsEndpoint(String url) {
        return trimTrailingSlash(url).endsWith("/chat/completions");
    }

    private boolean isOpenCodeGoConfig(String model, String url) {
        return (model != null && model.trim().startsWith(OPENCODE_GO_MODEL_PREFIX))
                || (url != null && url.contains("opencode.ai/zen/go/v1"));
    }

    private String normalizeApiUrl(String url, String requestApiType, boolean openCodeGoProvider) {
        if (openCodeGoProvider) {
            return normalizeOpenCodeGoApiUrl(url, requestApiType);
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("API 地址不能为空");
        }
        url = trimTrailingSlash(url);
        if (API_TYPE_ANTHROPIC.equals(requestApiType)) {
            if (url.endsWith("/messages")) return url;
            if (url.endsWith("/v1")) return url + "/messages";
            return url + "/v1/messages";
        }
        if (url.endsWith("/chat/completions")) return url;
        return url + "/chat/completions";
    }

    private String normalizeOpenCodeGoApiUrl(String url, String requestApiType) {
        String suffix = API_TYPE_ANTHROPIC.equals(requestApiType) ? "/messages" : "/chat/completions";
        if (url == null || url.isBlank()) {
            url = OPENCODE_GO_BASE_URL;
        }
        url = trimTrailingSlash(url);
        if (url.endsWith("/chat/completions")) {
            return API_TYPE_OPENAI.equals(requestApiType)
                    ? url
                    : url.substring(0, url.length() - "/chat/completions".length()) + suffix;
        }
        if (url.endsWith("/messages")) {
            return API_TYPE_ANTHROPIC.equals(requestApiType)
                    ? url
                    : url.substring(0, url.length() - "/messages".length()) + suffix;
        }
        return url + suffix;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return "";
        value = value.trim();
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String extractJson(String content) {
        if (content.contains("```json")) {
            int start = content.indexOf("```json") + 7;
            int end = content.indexOf("```", start);
            if (end > start) return content.substring(start, end).trim();
        }
        if (content.contains("```")) {
            int start = content.indexOf("```") + 3;
            int end = content.indexOf("```", start);
            if (end > start) return content.substring(start, end).trim();
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) return content.substring(start, end + 1);
        return content;
    }

    private AiProviderConfig requireProvider(AiProviderConfig provider, String role) {
        if (provider == null) {
            throw new IllegalStateException("未找到 " + role + " 选择的模型提供商");
        }
        if (!provider.hasApiKey()) {
            throw new IllegalStateException(role + " 提供商未配置 API key: " + provider.getName());
        }
        return provider;
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GenerateResult {
        private final boolean success;
        private final String title;
        private final String truth;
        private final String error;

        public static GenerateResult success(String title, String truth) {
            return new GenerateResult(true, title, truth, null);
        }

        public static GenerateResult fail(String error) {
            return new GenerateResult(false, null, null, error);
        }
    }

    public record SoloAnswerResult(GameRoom.AnswerType answerType, boolean solved) {}
}
