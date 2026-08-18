package com.bx.implatform.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "passport")
public class PassportProperties {
    private String nodeBaseUrl = "";
    private String appId = "";
    private String callbackUrl = "";
    private List<String> scopes = new ArrayList<>(List.of("identity.basic", "identity.email", "identity.wallet"));
    private int sessionTtlSeconds = 300;
}
