package com.danzzan.infra.octomo;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "octomo")
public class OctomoProperties {

    @NotBlank
    private String baseUrl = "https://api.octoverse.kr";

    @NotBlank
    private String messageExistsPath = "/octomo/v1/public/message/exists";

    @NotBlank
    private String apiKey;
}
