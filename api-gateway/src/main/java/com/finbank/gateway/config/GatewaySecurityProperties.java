package com.finbank.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "finbank.security")
@Getter
@Setter
public class GatewaySecurityProperties {

    /**
     * Patrones Ant/PathPattern que la {@code AuthenticationFilter} deja pasar sin exigir JWT.
     */
    private List<String> publicPaths = new ArrayList<>();
}
