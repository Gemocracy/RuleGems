# RuleGems HTTP API 调用文档

## 📋 概述

RuleGems 插件提供了一个 HTTP API 接口，允许外部系统获取玩家的宝石数据。API 支持 JSON 格式响应，包含安全验证机制。

## ⚙️ 配置说明

### 启用 API

在 `config.yml` 中启用 API 功能：

```yaml
# ==========================================
# HTTP API 接口配置
# ==========================================
api:
  # 是否启用 API 接口
  enabled: true
  # API 服务监听的端口号
  port: 8080
  # API 接口访问令牌（可选，用于安全验证）
  token: "your_secret_token"
  # 允许访问的 IP 地址列表（留空表示允许所有 IP）
  allowed_ips: []
  # API 响应格式：json 或 xml
  response_format: "json"
```

### 安全配置

- **IP 白名单**：通过 `allowed_ips` 限制访问来源
- **访问令牌**：通过 `token` 参数进行身份验证
- **CORS 支持**：支持跨域请求

## 📡 API 端点

### 1. 获取玩家宝石数据

**端点：** `GET /api/gems`

**参数：**
- `player` (可选)：玩家名称
- `uuid` (可选)：玩家 UUID
- `token` (可选)：访问令牌（如果配置了令牌验证）

**示例请求：**
```bash
# 通过玩家名称查询
curl "http://localhost:8080/api/gems?player=Steve&token=your_secret_token"

# 通过玩家 UUID 查询
curl "http://localhost:8080/api/gems?uuid=123e4567-e89b-12d3-a456-426614174000&token=your_secret_token"
```

**成功响应：**
```json
{
  "success": true,
  "message": "数据获取成功",
  "data": {
    "playerId": "123e4567-e89b-12d3-a456-426614174000",
    "playerName": "Steve",
    "heldGems": [
      {
        "gemId": "123e4567-e89b-12d3-a456-426614174001",
        "gemKey": "power_gem",
        "displayName": "力量宝石",
        "location": "inventory"
      }
    ],
    "redeemedGems": [
      {
        "gemKey": "speed_gem",
        "displayName": "速度宝石",
        "redeemTime": 1714060800000,
        "remainingUses": 5
      }
    ],
    "gemTypeCounts": {
      "power_gem": 1,
      "speed_gem": 0,
      "health_gem": 0
    },
    "isFullSetOwner": false
  }
}
```

**错误响应：**
```json
{
  "success": false,
  "message": "玩家不存在",
  "data": null
}
```

### 2. 获取在线玩家列表

**端点：** `GET /api/players`

**参数：**
- `token` (可选)：访问令牌

**示例请求：**
```bash
curl "http://localhost:8080/api/players?token=your_secret_token"
```

**响应：**
```json
{
  "success": true,
  "message": "在线玩家列表",
  "data": {
    "players": [
      {
        "playerId": "123e4567-e89b-12d3-a456-426614174000",
        "playerName": "Steve"
      },
      {
        "playerId": "123e4567-e89b-12d3-a456-426614174001",
        "playerName": "Alex"
      }
    ],
    "total": 2
  }
}
```

### 3. 获取服务器状态

**端点：** `GET /api/status`

**参数：**
- `token` (可选)：访问令牌

**示例请求：**
```bash
curl "http://localhost:8080/api/status?token=your_secret_token"
```

**响应：**
```json
{
  "success": true,
  "message": "服务器状态信息",
  "data": {
    "serverName": "RuleGems Server",
    "onlinePlayers": 2,
    "maxPlayers": 20,
    "apiVersion": "1.0.0",
    "uptime": 3600,
    "totalGems": 15,
    "redeemedGems": 8
  }
}
```

## 🔒 错误代码

| HTTP 状态码 | 错误信息 | 说明 |
|------------|---------|------|
| 200 | OK | 请求成功 |
| 400 | Bad Request | 参数错误或缺失 |
| 403 | Forbidden | IP 地址被拒绝或令牌无效 |
| 404 | Not Found | 玩家不存在 |
| 405 | Method Not Allowed | 请求方法不支持 |
| 500 | Internal Server Error | 服务器内部错误 |

## 💻 客户端示例代码

### Python 示例

```python
import requests
import json

class RuleGemsAPI:
    def __init__(self, base_url, token=None):
        self.base_url = base_url
        self.token = token
    
    def get_player_gems(self, player_name=None, player_uuid=None):
        """获取玩家宝石数据"""
        params = {}
        if player_name:
            params['player'] = player_name
        elif player_uuid:
            params['uuid'] = player_uuid
        
        if self.token:
            params['token'] = self.token
            
        response = requests.get(f"{self.base_url}/api/gems", params=params)
        return response.json()
    
    def get_online_players(self):
        """获取在线玩家列表"""
        params = {}
        if self.token:
            params['token'] = self.token
            
        response = requests.get(f"{self.base_url}/api/players", params=params)
        return response.json()
    
    def get_server_status(self):
        """获取服务器状态"""
        params = {}
        if self.token:
            params['token'] = self.token
            
        response = requests.get(f"{self.base_url}/api/status", params=params)
        return response.json()

# 使用示例
api = RuleGemsAPI("http://localhost:8080", "your_secret_token")

# 获取玩家宝石数据
player_data = api.get_player_gems(player_name="Steve")
print(json.dumps(player_data, indent=2, ensure_ascii=False))

# 获取在线玩家列表
players = api.get_online_players()
print(json.dumps(players, indent=2, ensure_ascii=False))

# 获取服务器状态
status = api.get_server_status()
print(json.dumps(status, indent=2, ensure_ascii=False))
```

### JavaScript 示例

```javascript
class RuleGemsAPI {
    constructor(baseUrl, token = null) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    async getPlayerGems(playerName = null, playerUuid = null) {
        const params = new URLSearchParams();
        
        if (playerName) params.append('player', playerName);
        if (playerUuid) params.append('uuid', playerUuid);
        if (this.token) params.append('token', this.token);
        
        const response = await fetch(`${this.baseUrl}/api/gems?${params}`);
        return await response.json();
    }

    async getOnlinePlayers() {
        const params = new URLSearchParams();
        if (this.token) params.append('token', this.token);
        
        const response = await fetch(`${this.baseUrl}/api/players?${params}`);
        return await response.json();
    }

    async getServerStatus() {
        const params = new URLSearchParams();
        if (this.token) params.append('token', this.token);
        
        const response = await fetch(`${this.baseUrl}/api/status?${params}`);
        return await response.json();
    }
}

// 使用示例
const api = new RuleGemsAPI('http://localhost:8080', 'your_secret_token');

// 获取玩家宝石数据
api.getPlayerGems('Steve')
    .then(data => console.log(JSON.stringify(data, null, 2)))
    .catch(error => console.error('Error:', error));

// 获取在线玩家列表
api.getOnlinePlayers()
    .then(data => console.log(JSON.stringify(data, null, 2)))
    .catch(error => console.error('Error:', error));

// 获取服务器状态
api.getServerStatus()
    .then(data => console.log(JSON.stringify(data, null, 2)))
    .catch(error => console.error('Error:', error));
```

### Java 示例

```java
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import com.google.gson.Gson;

public class RuleGemsAPI {
    private String baseUrl;
    private String token;
    private Gson gson = new Gson();
    
    public RuleGemsAPI(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }
    
    public ApiResponse getPlayerGems(String playerName, String playerUuid) {
        StringBuilder urlBuilder = new StringBuilder(baseUrl).append("/api/gems?");
        
        if (playerName != null) {
            urlBuilder.append("player=").append(playerName);
        } else if (playerUuid != null) {
            urlBuilder.append("uuid=").append(playerUuid);
        }
        
        if (token != null) {
            urlBuilder.append("&token=").append(token);
        }
        
        return sendGetRequest(urlBuilder.toString());
    }
    
    public ApiResponse getOnlinePlayers() {
        String url = baseUrl + "/api/players";
        if (token != null) {
            url += "?token=" + token;
        }
        return sendGetRequest(url);
    }
    
    public ApiResponse getServerStatus() {
        String url = baseUrl + "/api/status";
        if (token != null) {
            url += "?token=" + token;
        }
        return sendGetRequest(url);
    }
    
    private ApiResponse sendGetRequest(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            
            in.close();
            conn.disconnect();
            
            return gson.fromJson(content.toString(), ApiResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static class ApiResponse {
        public boolean success;
        public String message;
        public Object data;
    }
}

// 使用示例
RuleGemsAPI api = new RuleGemsAPI("http://localhost:8080", "your_secret_token");
RuleGemsAPI.ApiResponse response = api.getPlayerGems("Steve", null);
System.out.println(new Gson().toJson(response));
```

## 🔧 故障排除

### 常见问题

1. **API 无法访问**
   - 检查 `config.yml` 中 `api.enabled` 是否为 `true`
   - 确认服务器防火墙是否开放了指定端口
   - 检查 `allowed_ips` 配置是否限制了访问

2. **返回 403 Forbidden**
   - 检查访问令牌是否正确
   - 确认客户端 IP 地址是否在白名单中

3. **返回 404 Not Found**
   - 确认玩家名称或 UUID 是否正确
   - 检查玩家是否在线

4. **返回 400 Bad Request**
   - 检查请求参数是否完整
   - 确认 UUID 格式是否正确

### 调试技巧

1. **启用调试日志**：在服务器控制台查看 API 访问日志
2. **测试连接**：使用 `curl` 或 `Postman` 进行手动测试
3. **检查网络**：确认客户端与服务器网络连通性

## 📊 数据字段说明

### 玩家宝石数据结构

| 字段 | 类型 | 说明 |
|------|------|------|
| playerId | UUID | 玩家唯一标识符 |
| playerName | String | 玩家名称 |
| heldGems | Array | 持有的宝石列表 |
| redeemedGems | Array | 已兑换的宝石列表 |
| gemTypeCounts | Object | 各类宝石数量统计 |
| isFullSetOwner | Boolean | 是否拥有全套宝石 |

### 持有宝石字段

| 字段 | 类型 | 说明 |
|------|------|------|
| gemId | UUID | 宝石唯一标识符 |
| gemKey | String | 宝石配置键名 |
| displayName | String | 宝石显示名称 |
| location | String | 宝石位置（"inventory" 或坐标） |

### 已兑换宝石字段

| 字段 | 类型 | 说明 |
|------|------|------|
| gemKey | String | 宝石配置键名 |
| displayName | String | 宝石显示名称 |
| redeemTime | Long | 兑换时间戳（毫秒） |
| remainingUses | Integer | 剩余使用次数 |

## 📝 更新日志

- **v1.0.9** (2026-04-26)
  - 支持查询离线玩家的宝石数据（混合方案）
  - 增强 API 智能处理：在线玩家返回完整数据，离线玩家返回基础数据
  - 优化玩家名称缓存机制

- **v1.0.7** (2026-04-25)
  - 初始版本发布
  - 支持玩家宝石数据查询
  - 支持在线玩家列表查询
  - 支持服务器状态查询
  - 添加安全验证机制

---

**注意：** 本文档基于 RuleGems 插件当前版本编写，具体实现可能随版本更新而变化。