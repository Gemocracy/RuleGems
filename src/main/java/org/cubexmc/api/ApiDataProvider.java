package org.cubexmc.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.cubexmc.manager.GemManager;
import org.cubexmc.manager.GemStateManager;
import org.cubexmc.model.GemDefinition;

/**
 * API 数据提供器 - 负责查询和组装玩家宝石数据
 */
public class ApiDataProvider {
    
    /**
     * 获取玩家宝石数据（在线玩家）
     */
    public static GemDataResponse.PlayerGemData getPlayerGemData(Player player, GemManager gemManager) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        
        // 获取持有的宝石（仅在线玩家）
        List<GemDataResponse.HeldGem> heldGems = getHeldGems(player, gemManager);
        
        // 获取已兑换的宝石
        List<GemDataResponse.RedeemedGem> redeemedGems = getRedeemedGems(playerId, gemManager);
        
        // 获取宝石类型计数
        Map<String, Integer> gemTypeCounts = getGemTypeCounts(playerId, gemManager);
        
        // 检查是否为全套拥有者
        boolean isFullSetOwner = isFullSetOwner(playerId, gemManager);
        
        return new GemDataResponse.PlayerGemData(
            playerId, playerName, heldGems, redeemedGems, gemTypeCounts, isFullSetOwner
        );
    }
    
    /**
     * 获取离线玩家宝石数据（基础数据）
     */
    public static GemDataResponse.PlayerGemData getOfflinePlayerGemData(UUID playerId, String playerName, GemManager gemManager) {
        // 离线玩家无法获取持有的宝石（空列表）
        List<GemDataResponse.HeldGem> heldGems = new ArrayList<>();
        
        // 获取已兑换的宝石（可以从数据库获取）
        List<GemDataResponse.RedeemedGem> redeemedGems = getRedeemedGems(playerId, gemManager);
        
        // 获取宝石类型计数（可以从数据库获取）
        Map<String, Integer> gemTypeCounts = getGemTypeCounts(playerId, gemManager);
        
        // 检查是否为全套拥有者
        boolean isFullSetOwner = isFullSetOwner(playerId, gemManager);
        
        return new GemDataResponse.PlayerGemData(
            playerId, playerName, heldGems, redeemedGems, gemTypeCounts, isFullSetOwner
        );
    }
    
    /**
     * 获取玩家持有的宝石
     */
    private static List<GemDataResponse.HeldGem> getHeldGems(Player player, GemManager gemManager) {
        List<GemDataResponse.HeldGem> heldGems = new ArrayList<>();
        GemStateManager stateManager = gemManager.getStateManager();
        
        // 检查背包中的宝石
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && stateManager.isRuleGem(item)) {
                UUID gemId = stateManager.getGemUUID(item);
                if (gemId != null) {
                    String gemKey = stateManager.getGemUuidToKey().get(gemId);
                    GemDefinition gemDef = stateManager.findGemDefinition(gemKey);
                    if (gemDef != null) {
                        heldGems.add(new GemDataResponse.HeldGem(
                            gemId, gemKey, gemDef.getDisplayName(), "inventory"
                        ));
                    }
                }
            }
        }
        
        // 检查已放置的宝石（由该玩家持有）
        Map<UUID, Player> gemUuidToHolder = stateManager.getGemUuidToHolder();
        for (Map.Entry<UUID, Player> entry : gemUuidToHolder.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getUniqueId().equals(player.getUniqueId())) {
                UUID gemId = entry.getKey();
                String gemKey = stateManager.getGemUuidToKey().get(gemId);
                GemDefinition gemDef = stateManager.findGemDefinition(gemKey);
                if (gemDef != null) {
                    Location location = stateManager.getGemUuidToLocation().get(gemId);
                    String locationStr = location != null ? 
                        String.format("world:%s,x:%d,y:%d,z:%d", 
                            location.getWorld().getName(), 
                            location.getBlockX(), 
                            location.getBlockY(), 
                            location.getBlockZ()) : "unknown";
                    
                    heldGems.add(new GemDataResponse.HeldGem(
                        gemId, gemKey, gemDef.getDisplayName(), locationStr
                    ));
                }
            }
        }
        
        return heldGems;
    }
    
    /**
     * 获取玩家已兑换的宝石
     */
    private static List<GemDataResponse.RedeemedGem> getRedeemedGems(UUID playerId, GemManager gemManager) {
        List<GemDataResponse.RedeemedGem> redeemedGems = new ArrayList<>();
        GemStateManager stateManager = gemManager.getStateManager();
        
        // 获取玩家已兑换的宝石类型
        Map<UUID, Set<String>> playerRedeemedKeys = gemManager.getPermissionManager().getPlayerUuidToRedeemedKeys();
        Set<String> redeemedKeys = playerRedeemedKeys.get(playerId);
        
        if (redeemedKeys != null) {
            for (String gemKey : redeemedKeys) {
                GemDefinition gemDef = stateManager.findGemDefinition(gemKey);
                if (gemDef != null) {
                    // 获取剩余使用次数（这里简化处理，实际应该从 allowanceManager 获取）
                    int remainingUses = getRemainingUses(playerId, gemKey, gemManager);
                    
                    redeemedGems.add(new GemDataResponse.RedeemedGem(
                        gemKey, gemDef.getDisplayName(), System.currentTimeMillis(), remainingUses
                    ));
                }
            }
        }
        
        return redeemedGems;
    }
    
    /**
     * 获取宝石类型计数
     */
    private static Map<String, Integer> getGemTypeCounts(UUID playerId, GemManager gemManager) {
        Map<String, Integer> gemTypeCounts = new HashMap<>();
        
        // 获取玩家对每种宝石类型的持有计数
        Map<UUID, Map<String, Integer>> ownerKeyCount = gemManager.getPermissionManager().getOwnerKeyCount();
        Map<String, Integer> playerCounts = ownerKeyCount.get(playerId);
        
        if (playerCounts != null) {
            gemTypeCounts.putAll(playerCounts);
        }
        
        return gemTypeCounts;
    }
    
    /**
     * 检查是否为全套拥有者
     */
    private static boolean isFullSetOwner(UUID playerId, GemManager gemManager) {
        UUID fullSetOwner = gemManager.getPermissionManager().getFullSetOwner();
        return playerId.equals(fullSetOwner);
    }
    
    /**
     * 获取剩余使用次数（简化实现）
     */
    private static int getRemainingUses(UUID playerId, String gemKey, GemManager gemManager) {
        // 这里需要从 allowanceManager 获取实际的使用次数
        // 简化实现：返回一个默认值
        return -1; // -1 表示无限次
    }
}