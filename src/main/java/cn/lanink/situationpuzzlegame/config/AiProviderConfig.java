package cn.lanink.situationpuzzlegame.config;

public class AiProviderConfig {

    private final String name;
    private final String apiType;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final String thinkingType;
    private final String reasoningEffort;

    public AiProviderConfig(String name, String apiType, String apiUrl, String apiKey,
                            String model, String thinkingType, String reasoningEffort) {
        this.name = name;
        this.apiType = apiType;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.thinkingType = thinkingType;
        this.reasoningEffort = reasoningEffort;
    }

    public String getName() { return name; }
    public String getApiType() { return apiType; }
    public String getApiUrl() { return apiUrl; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getThinkingType() { return thinkingType; }
    public String getReasoningEffort() { return reasoningEffort; }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
