package org.cubexmc.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API 响应数据模型
 */
public class GemDataResponse {
    private boolean success;
    private String message;
    private PlayerGemData data;
    
    public GemDataResponse(boolean success, String message, PlayerGemData data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
    
    // Getters and setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public PlayerGemData getData() { return data; }
    public void setData(PlayerGemData data) { this.data = data; }
    
    /**
     * 玩家宝石数据
     */
    public static class PlayerGemData {
        private UUID playerId;
        private String playerName;
        private List<HeldGem> heldGems;
        private List<RedeemedGem> redeemedGems;
        private Map<String, Integer> gemTypeCounts;
        private boolean isFullSetOwner;
        
        public PlayerGemData(UUID playerId, String playerName, List<HeldGem> heldGems, 
                           List<RedeemedGem> redeemedGems, Map<String, Integer> gemTypeCounts, 
                           boolean isFullSetOwner) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.heldGems = heldGems;
            this.redeemedGems = redeemedGems;
            this.gemTypeCounts = gemTypeCounts;
            this.isFullSetOwner = isFullSetOwner;
        }
        
        // Getters and setters
        public UUID getPlayerId() { return playerId; }
        public void setPlayerId(UUID playerId) { this.playerId = playerId; }
        
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        
        public List<HeldGem> getHeldGems() { return heldGems; }
        public void setHeldGems(List<HeldGem> heldGems) { this.heldGems = heldGems; }
        
        public List<RedeemedGem> getRedeemedGems() { return redeemedGems; }
        public void setRedeemedGems(List<RedeemedGem> redeemedGems) { this.redeemedGems = redeemedGems; }
        
        public Map<String, Integer> getGemTypeCounts() { return gemTypeCounts; }
        public void setGemTypeCounts(Map<String, Integer> gemTypeCounts) { this.gemTypeCounts = gemTypeCounts; }
        
        public boolean isFullSetOwner() { return isFullSetOwner; }
        public void setFullSetOwner(boolean fullSetOwner) { isFullSetOwner = fullSetOwner; }
    }
    
    /**
     * 持有的宝石
     */
    public static class HeldGem {
        private UUID gemId;
        private String gemKey;
        private String displayName;
        private String location; // "inventory" 或坐标字符串
        
        public HeldGem(UUID gemId, String gemKey, String displayName, String location) {
            this.gemId = gemId;
            this.gemKey = gemKey;
            this.displayName = displayName;
            this.location = location;
        }
        
        // Getters and setters
        public UUID getGemId() { return gemId; }
        public void setGemId(UUID gemId) { this.gemId = gemId; }
        
        public String getGemKey() { return gemKey; }
        public void setGemKey(String gemKey) { this.gemKey = gemKey; }
        
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
    }
    
    /**
     * 已兑换的宝石
     */
    public static class RedeemedGem {
        private String gemKey;
        private String displayName;
        private long redeemTime;
        private int remainingUses;
        
        public RedeemedGem(String gemKey, String displayName, long redeemTime, int remainingUses) {
            this.gemKey = gemKey;
            this.displayName = displayName;
            this.redeemTime = redeemTime;
            this.remainingUses = remainingUses;
        }
        
        // Getters and setters
        public String getGemKey() { return gemKey; }
        public void setGemKey(String gemKey) { this.gemKey = gemKey; }
        
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        
        public long getRedeemTime() { return redeemTime; }
        public void setRedeemTime(long redeemTime) { this.redeemTime = redeemTime; }
        
        public int getRemainingUses() { return remainingUses; }
        public void setRemainingUses(int remainingUses) { this.remainingUses = remainingUses; }
    }
}