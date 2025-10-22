//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.springframework.ai.mcp.client.autoconfigure.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties("spring.ai.mcp.client.sse")
public class McpSseClientProperties {
    public static final String CONFIG_PREFIX = "spring.ai.mcp.client.sse";
    private final Map<String, SseParameters> connections = new HashMap();

    public Map<String, SseParameters> getConnections() {
        return this.connections;
    }

    public static record SseParameters(String url, String sseEndpoint, Map<String, String> headers) {
    }
}
