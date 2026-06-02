package cn.lanink.situationpuzzlegame.config;

import lombok.Value;

@Value
public class AiProviderConfig {

    String name;
    String apiType;
    String apiUrl;
    String apiKey;
    String model;
    String thinkingType;
    String reasoningEffort;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
