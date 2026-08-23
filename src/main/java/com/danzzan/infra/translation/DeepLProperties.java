package com.danzzan.infra.translation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "deepl")
public class DeepLProperties {

    private static final String FREE_KEY_SUFFIX = ":fx";
    private static final String FREE_API_URL = "https://api-free.deepl.com/v2";
    private static final String PAID_API_URL = "https://api.deepl.com/v2";

    /**
     * 비어 있어도 앱은 기동한다. 키가 없으면 번역이 비활성화될 뿐이다.
     */
    private String apiKey;

    /**
     * 비워두면 키 접미사로 자동 판별한다.
     */
    private String apiUrl;

    private String glossaryName = "danzzan-festival-ko-en";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String resolveApiUrl() {
        if (apiUrl != null && !apiUrl.isBlank()) {
            return apiUrl;
        }
        if (isConfigured() && apiKey.trim().endsWith(FREE_KEY_SUFFIX)) {
            return FREE_API_URL;
        }
        return PAID_API_URL;
    }
}
