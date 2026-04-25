package org.cubexmc.api;

import java.util.List;

/**
 * API 配置类
 */
public class ApiConfig {
    private boolean enabled;
    private int port;
    private String token;
    private List<String> allowedIps;
    private String responseFormat;
    
    public ApiConfig(boolean enabled, int port, String token, List<String> allowedIps, String responseFormat) {
        this.enabled = enabled;
        this.port = port;
        this.token = token;
        this.allowedIps = allowedIps;
        this.responseFormat = responseFormat;
    }
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    
    public List<String> getAllowedIps() { return allowedIps; }
    public void setAllowedIps(List<String> allowedIps) { this.allowedIps = allowedIps; }
    
    public String getResponseFormat() { return responseFormat; }
    public void setResponseFormat(String responseFormat) { this.responseFormat = responseFormat; }
    
    /**
     * 检查 IP 地址是否被允许访问
     */
    public boolean isIpAllowed(String ip) {
        if (allowedIps == null || allowedIps.isEmpty()) {
            return true; // 空列表表示允许所有 IP
        }
        return allowedIps.contains(ip);
    }
    
    /**
     * 检查令牌是否有效
     */
    public boolean isTokenValid(String providedToken) {
        if (token == null || token.isEmpty()) {
            return true; // 未设置令牌，允许访问
        }
        return token.equals(providedToken);
    }
}