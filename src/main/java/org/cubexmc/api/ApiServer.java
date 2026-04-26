package org.cubexmc.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.cubexmc.RuleGems;
import org.cubexmc.manager.GemManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * HTTP API 服务器
 */
public class ApiServer {
    private final RuleGems plugin;
    private final GemManager gemManager;
    private final ApiConfig config;
    private HttpServer server;
    private final ObjectMapper objectMapper;
    
    public ApiServer(RuleGems plugin, GemManager gemManager, ApiConfig config) {
        this.plugin = plugin;
        this.gemManager = gemManager;
        this.config = config;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 启动 API 服务器
     */
    public boolean start() {
        if (!config.isEnabled()) {
            plugin.getLogger().info("API server is disabled in configuration.");
            return false;
        }
        
        try {
            server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
            server.createContext("/api/gems", new GemsHandler());
            server.createContext("/api/players", new PlayersHandler());
            server.createContext("/api/status", new StatusHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            
            plugin.getLogger().info("API server started on port " + config.getPort());
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 停止 API 服务器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("API server stopped.");
        }
    }
    
    /**
     * 发送 HTTP 响应
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    /**
     * 检查请求权限
     */
    private boolean checkAccess(HttpExchange exchange) {
        // 检查 IP 地址
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (!config.isIpAllowed(clientIp)) {
            plugin.getLogger().warning("API access denied for IP: " + clientIp);
            return false;
        }
        
        // 检查令牌（支持 Bearer Token 和 URL 查询参数）
        String token = null;
        
        // 1. 检查 HTTP Authorization Header
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        
        // 2. 检查 URL 查询参数（如果 Authorization Header 不存在）
        if (token == null) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("token=")) {
                Map<String, String> params = parseQueryParams(query);
                token = params.get("token");
            }
        }
        
        // 验证令牌
        if (token != null) {
            if (!config.isTokenValid(token)) {
                plugin.getLogger().warning("API access denied: invalid token from " + clientIp);
                return false;
            }
        } else if (!config.getToken().isEmpty()) {
            // 需要令牌但未提供
            plugin.getLogger().warning("API access denied: missing token from " + clientIp);
            return false;
        }
        
        return true;
    }
    
    /**
     * 宝石数据处理器
     */
    private class GemsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAccess(exchange)) {
                sendResponse(exchange, 403, objectMapper.writeValueAsString(
                    new GemDataResponse(false, "Access denied", null)));
                return;
            }
            
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, objectMapper.writeValueAsString(
                    new GemDataResponse(false, "Method not allowed", null)));
                return;
            }
            
            try {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQueryParams(query);
                
                String playerName = params.get("player");
                String playerUuid = params.get("uuid");
                
                if (playerName == null && playerUuid == null) {
                    sendResponse(exchange, 400, objectMapper.writeValueAsString(
                        new GemDataResponse(false, "Missing player parameter", null)));
                    return;
                }
                
                Player player = null;
                UUID playerUuidObj = null;
                String resolvedPlayerName = null;
                
                if (playerUuid != null) {
                    try {
                        playerUuidObj = UUID.fromString(playerUuid);
                        player = Bukkit.getPlayer(playerUuidObj);
                        if (player != null) {
                            resolvedPlayerName = player.getName();
                        } else {
                            // 离线玩家：尝试从缓存获取玩家名称
                            resolvedPlayerName = gemManager.getCachedPlayerName(playerUuidObj);
                        }
                    } catch (IllegalArgumentException e) {
                        sendResponse(exchange, 400, objectMapper.writeValueAsString(
                            new GemDataResponse(false, "Invalid UUID format", null)));
                        return;
                    }
                } else {
                    player = Bukkit.getPlayer(playerName);
                    if (player != null) {
                        playerUuidObj = player.getUniqueId();
                        resolvedPlayerName = player.getName();
                    } else {
                        // 离线玩家：尝试通过名称查找UUID
                        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                        if (offlinePlayer.hasPlayedBefore()) {
                            playerUuidObj = offlinePlayer.getUniqueId();
                            resolvedPlayerName = offlinePlayer.getName();
                        }
                    }
                }
                
                if (playerUuidObj == null) {
                    sendResponse(exchange, 404, objectMapper.writeValueAsString(
                        new GemDataResponse(false, "Player not found", null)));
                    return;
                }
                
                // 获取玩家宝石数据
                GemDataResponse.PlayerGemData gemData;
                if (player != null) {
                    // 在线玩家：获取完整数据
                    gemData = ApiDataProvider.getPlayerGemData(player, gemManager);
                } else {
                    // 离线玩家：获取基础数据
                    gemData = ApiDataProvider.getOfflinePlayerGemData(playerUuidObj, resolvedPlayerName, gemManager);
                }
                
                String message = player != null ? "Success" : "Offline player data retrieved";
                GemDataResponse response = new GemDataResponse(true, message, gemData);
                
                sendResponse(exchange, 200, objectMapper.writeValueAsString(response));
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error handling API request: " + e.getMessage());
                sendResponse(exchange, 500, objectMapper.writeValueAsString(
                    new GemDataResponse(false, "Internal server error", null)));
            }
        }
    }
    
    /**
     * 在线玩家列表处理器
     */
    private class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAccess(exchange)) {
                sendResponse(exchange, 403, "{\"error\":\"Access denied\"}");
                return;
            }
            
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            
            try {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
                
                Map<String, String> players = new HashMap<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    players.put(player.getUniqueId().toString(), player.getName());
                }
                response.put("players", players);
                
                sendResponse(exchange, 200, objectMapper.writeValueAsString(response));
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error handling players API request: " + e.getMessage());
                sendResponse(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }
    
    /**
     * 服务器状态处理器
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAccess(exchange)) {
                sendResponse(exchange, 403, "{\"error\":\"Access denied\"}");
                return;
            }
            
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            
            try {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("pluginVersion", plugin.getDescription().getVersion());
                response.put("serverVersion", Bukkit.getVersion());
                response.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
                response.put("maxPlayers", Bukkit.getMaxPlayers());
                
                sendResponse(exchange, 200, objectMapper.writeValueAsString(response));
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error handling status API request: " + e.getMessage());
                sendResponse(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }
    
    /**
     * 解析查询参数
     */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2) {
                params.put(pair[0], pair[1]);
            }
        }
        return params;
    }
}