#!/usr/bin/env python3
"""
RuleGems HTTP API 客户端示例

这是一个完整的 Python 客户端示例，展示了如何调用 RuleGems 插件的 HTTP API。
"""

import requests
import json
import sys
from typing import Optional, Dict, Any


class RuleGemsAPIClient:
    """RuleGems HTTP API 客户端"""
    
    def __init__(self, base_url: str, token: Optional[str] = None):
        """
        初始化 API 客户端
        
        Args:
            base_url: API 基础地址，例如 "http://localhost:8080"
            token: 访问令牌（可选）
        """
        self.base_url = base_url.rstrip('/')
        self.token = token
        self.session = requests.Session()
        
        # 设置通用请求头
        self.session.headers.update({
            'User-Agent': 'RuleGemsAPIClient/1.0.0',
            'Accept': 'application/json'
        })
    
    def _build_params(self, **kwargs) -> Dict[str, str]:
        """构建请求参数"""
        params = {k: v for k, v in kwargs.items() if v is not None}
        if self.token:
            params['token'] = self.token
        return params
    
    def get_player_gems(self, player_name: Optional[str] = None, 
                       player_uuid: Optional[str] = None) -> Dict[str, Any]:
        """
        获取玩家宝石数据
        
        Args:
            player_name: 玩家名称
            player_uuid: 玩家 UUID
            
        Returns:
            API 响应数据
            
        Raises:
            requests.RequestException: 网络请求错误
        """
        if not player_name and not player_uuid:
            raise ValueError("必须提供 player_name 或 player_uuid 参数")
        
        params = self._build_params(player=player_name, uuid=player_uuid)
        
        try:
            response = self.session.get(f"{self.base_url}/api/gems", params=params)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            print(f"获取玩家宝石数据失败: {e}")
            raise
    
    def get_online_players(self) -> Dict[str, Any]:
        """
        获取在线玩家列表
        
        Returns:
            API 响应数据
        """
        params = self._build_params()
        
        try:
            response = self.session.get(f"{self.base_url}/api/players", params=params)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            print(f"获取在线玩家列表失败: {e}")
            raise
    
    def get_server_status(self) -> Dict[str, Any]:
        """
        获取服务器状态
        
        Returns:
            API 响应数据
        """
        params = self._build_params()
        
        try:
            response = self.session.get(f"{self.base_url}/api/status", params=params)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            print(f"获取服务器状态失败: {e}")
            raise
    
    def test_connection(self) -> bool:
        """
        测试 API 连接
        
        Returns:
            连接是否成功
        """
        try:
            response = self.get_server_status()
            return response.get('success', False)
        except:
            return False


def print_json(data: Dict[str, Any], title: str = ""):
    """格式化打印 JSON 数据"""
    if title:
        print(f"\n{'='*50}")
        print(f"{title}")
        print(f"{'='*50}")
    print(json.dumps(data, indent=2, ensure_ascii=False))


def main():
    """主函数 - 演示 API 客户端使用"""
    
    # 配置参数
    BASE_URL = "http://localhost:8080"
    TOKEN = "your_secret_token"  # 替换为实际的访问令牌
    
    # 创建 API 客户端
    api = RuleGemsAPIClient(BASE_URL, TOKEN)
    
    print("RuleGems API 客户端演示")
    print("=" * 50)
    
    # 测试连接
    print("1. 测试 API 连接...")
    if api.test_connection():
        print("✅ API 连接成功")
    else:
        print("❌ API 连接失败，请检查配置")
        sys.exit(1)
    
    # 获取服务器状态
    print("\n2. 获取服务器状态...")
    try:
        status = api.get_server_status()
        if status['success']:
            print_json(status['data'], "服务器状态")
        else:
            print(f"❌ 获取服务器状态失败: {status['message']}")
    except Exception as e:
        print(f"❌ 获取服务器状态异常: {e}")
    
    # 获取在线玩家列表
    print("\n3. 获取在线玩家列表...")
    try:
        players = api.get_online_players()
        if players['success']:
            print_json(players['data'], "在线玩家列表")
            
            # 如果有在线玩家，获取第一个玩家的宝石数据
            if players['data'].get('players'):
                first_player = players['data']['players'][0]
                player_name = first_player.get('playerName')
                player_uuid = first_player.get('playerId')
                
                print(f"\n4. 获取玩家 '{player_name}' 的宝石数据...")
                try:
                    gems = api.get_player_gems(player_name=player_name)
                    if gems['success']:
                        print_json(gems['data'], f"玩家 {player_name} 的宝石数据")
                    else:
                        print(f"❌ 获取玩家宝石数据失败: {gems['message']}")
                except Exception as e:
                    print(f"❌ 获取玩家宝石数据异常: {e}")
        else:
            print(f"❌ 获取在线玩家列表失败: {players['message']}")
    except Exception as e:
        print(f"❌ 获取在线玩家列表异常: {e}")
    
    # 演示错误处理
    print("\n5. 演示错误处理...")
    
    # 测试不存在的玩家
    try:
        invalid_player = api.get_player_gems(player_name="NonExistentPlayer")
        if not invalid_player['success']:
            print(f"✅ 预期错误处理: {invalid_player['message']}")
    except Exception as e:
        print(f"❌ 错误处理异常: {e}")
    
    print("\n" + "=" * 50)
    print("演示完成")


if __name__ == "__main__":
    # 检查依赖
    try:
        import requests
    except ImportError:
        print("❌ 缺少依赖: requests")
        print("请安装: pip install requests")
        sys.exit(1)
    
    main()