package com.bx.implatform.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Boolean enabled = false;

    private String chatCompletionsUrl = "";

    private String apiKey = "";

    private String model = "gpt-4o-mini";

    private Integer timeoutMs = 10000;

    private Integer maxContextMessages = 20;
}
