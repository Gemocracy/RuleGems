/**
 * RuleGems HTTP API 客户端示例 (JavaScript)
 * 
 * 这是一个完整的 JavaScript 客户端示例，展示了如何调用 RuleGems 插件的 HTTP API。
 * 适用于浏览器环境和 Node.js 环境。
 */

class RuleGemsAPIClient {
    /**
     * 初始化 API 客户端
     * @param {string} baseUrl - API 基础地址，例如 "http://localhost:8080"
     * @param {string} [token] - 访问令牌（可选）
     */
    constructor(baseUrl, token = null) {
        this.baseUrl = baseUrl.replace(/\/$/, '');
        this.token = token;
        this.headers = {
            'User-Agent': 'RuleGemsAPIClient/1.0.0',
            'Accept': 'application/json'
        };
    }

    /**
     * 构建请求参数
     * @param {Object} params - 参数对象
     * @returns {URLSearchParams}
     */
    _buildParams(params = {}) {
        const searchParams = new URLSearchParams();
        
        // 添加参数
        Object.entries(params).forEach(([key, value]) => {
            if (value !== null && value !== undefined) {
                searchParams.append(key, value.toString());
            }
        });
        
        // 添加令牌
        if (this.token) {
            searchParams.append('token', this.token);
        }
        
        return searchParams;
    }

    /**
     * 发送 HTTP 请求
     * @param {string} url - 请求 URL
     * @param {Object} [params] - 请求参数
     * @returns {Promise<Object>}
     */
    async _request(url, params = {}) {
        const queryString = this._buildParams(params).toString();
        const fullUrl = queryString ? `${url}?${queryString}` : url;
        
        try {
            const response = await fetch(fullUrl, {
                method: 'GET',
                headers: this.headers
            });
            
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error(`API 请求失败: ${error.message}`);
            throw error;
        }
    }

    /**
     * 获取玩家宝石数据
     * @param {string} [playerName] - 玩家名称
     * @param {string} [playerUuid] - 玩家 UUID
     * @returns {Promise<Object>}
     */
    async getPlayerGems(playerName = null, playerUuid = null) {
        if (!playerName && !playerUuid) {
            throw new Error('必须提供 playerName 或 playerUuid 参数');
        }
        
        const params = {
            player: playerName,
            uuid: playerUuid
        };
        
        return await this._request(`${this.baseUrl}/api/gems`, params);
    }

    /**
     * 获取在线玩家列表
     * @returns {Promise<Object>}
     */
    async getOnlinePlayers() {
        return await this._request(`${this.baseUrl}/api/players`);
    }

    /**
     * 获取服务器状态
     * @returns {Promise<Object>}
     */
    async getServerStatus() {
        return await this._request(`${this.baseUrl}/api/status`);
    }

    /**
     * 测试 API 连接
     * @returns {Promise<boolean>}
     */
    async testConnection() {
        try {
            const status = await this.getServerStatus();
            return status.success === true;
        } catch {
            return false;
        }
    }
}

/**
 * 格式化打印 JSON 数据
 * @param {Object} data - 要打印的数据
 * @param {string} title - 标题
 */
function printJson(data, title = '') {
    if (title) {
        console.log('='.repeat(50));
        console.log(title);
        console.log('='.repeat(50));
    }
    console.log(JSON.stringify(data, null, 2));
}

/**
 * 主函数 - 演示 API 客户端使用
 */
async function main() {
    // 配置参数
    const BASE_URL = 'http://localhost:8080';
    const TOKEN = 'your_secret_token'; // 替换为实际的访问令牌
    
    // 创建 API 客户端
    const api = new RuleGemsAPIClient(BASE_URL, TOKEN);
    
    console.log('RuleGems API 客户端演示 (JavaScript)');
    console.log('='.repeat(50));
    
    // 测试连接
    console.log('1. 测试 API 连接...');
    const isConnected = await api.testConnection();
    if (isConnected) {
        console.log('✅ API 连接成功');
    } else {
        console.log('❌ API 连接失败，请检查配置');
        return;
    }
    
    // 获取服务器状态
    console.log('\n2. 获取服务器状态...');
    try {
        const status = await api.getServerStatus();
        if (status.success) {
            printJson(status.data, '服务器状态');
        } else {
            console.log(`❌ 获取服务器状态失败: ${status.message}`);
        }
    } catch (error) {
        console.log(`❌ 获取服务器状态异常: ${error.message}`);
    }
    
    // 获取在线玩家列表
    console.log('\n3. 获取在线玩家列表...');
    try {
        const players = await api.getOnlinePlayers();
        if (players.success) {
            printJson(players.data, '在线玩家列表');
            
            // 如果有在线玩家，获取第一个玩家的宝石数据
            if (players.data.players && players.data.players.length > 0) {
                const firstPlayer = players.data.players[0];
                const playerName = firstPlayer.playerName;
                const playerUuid = firstPlayer.playerId;
                
                console.log(`\n4. 获取玩家 '${playerName}' 的宝石数据...`);
                try {
                    const gems = await api.getPlayerGems(playerName);
                    if (gems.success) {
                        printJson(gems.data, `玩家 ${playerName} 的宝石数据`);
                    } else {
                        console.log(`❌ 获取玩家宝石数据失败: ${gems.message}`);
                    }
                } catch (error) {
                    console.log(`❌ 获取玩家宝石数据异常: ${error.message}`);
                }
            }
        } else {
            console.log(`❌ 获取在线玩家列表失败: ${players.message}`);
        }
    } catch (error) {
        console.log(`❌ 获取在线玩家列表异常: ${error.message}`);
    }
    
    // 演示错误处理
    console.log('\n5. 演示错误处理...');
    
    // 测试不存在的玩家
    try {
        const invalidPlayer = await api.getPlayerGems('NonExistentPlayer');
        if (!invalidPlayer.success) {
            console.log(`✅ 预期错误处理: ${invalidPlayer.message}`);
        }
    } catch (error) {
        console.log(`❌ 错误处理异常: ${error.message}`);
    }
    
    console.log('\n' + '='.repeat(50));
    console.log('演示完成');
}

// 浏览器环境使用示例
if (typeof window !== 'undefined') {
    // 在浏览器中运行时，将函数暴露给全局作用域
    window.RuleGemsAPIClient = RuleGemsAPIClient;
    window.demoRuleGemsAPI = main;
    
    console.log('RuleGems API 客户端已加载到浏览器环境');
    console.log('使用方法: await demoRuleGemsAPI()');
}

// Node.js 环境使用示例
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        RuleGemsAPIClient,
        main
    };
    
    // 如果直接运行此文件，则执行演示
    if (require.main === module) {
        main().catch(error => {
            console.error('演示失败:', error);
            process.exit(1);
        });
    }
}

// 浏览器环境自动运行演示（可选）
if (typeof window !== 'undefined' && window.location.search.includes('demo')) {
    console.log('自动运行演示...');
    main().catch(error => console.error('演示失败:', error));
}