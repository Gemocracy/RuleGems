package org.cubexmc.manager;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.cubexmc.RuleGems;
import org.cubexmc.features.appoint.AppointFeature;
import org.cubexmc.model.GemDefinition;

public class RuleGemsDoctor {

    private final RuleGems plugin;

    public RuleGemsDoctor(RuleGems plugin) {
        this.plugin = plugin;
    }

    public void sendReport(CommandSender sender) {
        List<Entry> entries = inspect();
        sender.sendMessage(color(header()));
        if (entries.isEmpty()) {
            sender.sendMessage(color(okLine(localized("未发现明显配置问题。", "No obvious configuration issues were found."))));
        } else {
            for (Entry entry : entries) {
                sender.sendMessage(color(formatEntry(entry)));
            }
        }
        sender.sendMessage(color(footer(entries)));
    }

    public void logWarnings() {
        for (Entry entry : inspect()) {
            if (entry.severity == Severity.OK) {
                continue;
            }
            plugin.getLogger().warning(ChatColor.stripColor(color(formatEntry(entry))));
        }
    }

    private List<Entry> inspect() {
        List<Entry> entries = new ArrayList<>();
        GameplayConfig gameplayConfig = plugin.getGameplayConfig();
        ConfigManager configManager = plugin.getConfigManager();

        if (configManager == null || configManager.getConfig() == null) {
            entries.add(new Entry(Severity.ERROR, localized("主配置尚未加载。", "Main configuration is not loaded.")));
            return entries;
        }

        ConfigurationSection randomPlace = configManager.getConfig().getConfigurationSection("random_place_range");
        if (randomPlace == null) {
            entries.add(new Entry(Severity.ERROR,
                    localized("缺少 random_place_range，散落与补齐逻辑将不可用。", "Missing random_place_range; scatter and refill flows will fail.")));
        } else {
            String worldName = randomPlace.getString("world");
            if (worldName == null || worldName.isBlank()) {
                entries.add(new Entry(Severity.ERROR,
                        localized("random_place_range.world 未配置。", "random_place_range.world is not configured.")));
            } else {
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    entries.add(new Entry(Severity.ERROR,
                            localized("随机放置世界不存在: ", "Random placement world not found: ") + worldName));
                }
            }
        }

        List<GemDefinition> gemDefinitions = plugin.getGemParser().getGemDefinitions();
        if (gemDefinitions == null || gemDefinitions.isEmpty()) {
            entries.add(new Entry(Severity.ERROR,
                    localized("当前没有加载任何宝石定义。", "No gem definitions are currently loaded.")));
        } else {
            entries.add(new Entry(Severity.OK,
                    localized("已加载宝石定义数量: ", "Loaded gem definitions: ") + gemDefinitions.size()));
        }

        if (gameplayConfig != null && gameplayConfig.isPlaceRedeemEnabled() && gemDefinitions != null && !gemDefinitions.isEmpty()) {
            long missingAltars = gemDefinitions.stream().filter(def -> def.getAltarLocation() == null).count();
            if (missingAltars > 0) {
                entries.add(new Entry(Severity.WARNING,
                        localized("祭坛兑换已启用，但仍有宝石未设置祭坛数量: ", "Altar redeem is enabled, but gems are missing altar locations: ")
                                + missingAltars));
            }
        }

        AppointFeature appointFeature = plugin.getFeatureManager() != null ? plugin.getFeatureManager().getAppointFeature() : null;
        if (appointFeature != null && appointFeature.isEnabled()) {
            int appointCount = appointFeature.getAppointDefinitions().size();
            if (appointCount == 0) {
                entries.add(new Entry(Severity.WARNING,
                        localized("委任功能已启用，但没有任何职位定义。", "Appoint feature is enabled, but no roles are defined.")));
            } else {
                entries.add(new Entry(Severity.OK,
                        localized("可委任职位数量: ", "Available appoint roles: ") + appointCount));
            }
        }

        if (gameplayConfig != null && gameplayConfig.isOpEscalationAllowed()) {
            entries.add(new Entry(Severity.WARNING,
                    localized("allow_op_escalation 已开启，存在安全风险。", "allow_op_escalation is enabled and increases security risk.")));
        }

        return entries;
    }

    private String formatEntry(Entry entry) {
        return switch (entry.severity) {
            case OK -> "&a[OK] &f" + entry.message;
            case WARNING -> "&e[WARN] &f" + entry.message;
            case ERROR -> "&c[ERROR] &f" + entry.message;
        };
    }

    private String header() {
        return localized("&6===== RuleGems 诊断报告 =====", "&6===== RuleGems Doctor Report =====");
    }

    private String footer(List<Entry> entries) {
        long warnings = entries.stream().filter(entry -> entry.severity == Severity.WARNING).count();
        long errors = entries.stream().filter(entry -> entry.severity == Severity.ERROR).count();
        return localized("&7警告: &e", "&7Warnings: &e") + warnings
                + localized(" &7错误: &c", " &7Errors: &c") + errors;
    }

    private String okLine(String message) {
        return "&a" + message;
    }

    private String localized(String zh, String en) {
        return plugin.getLanguageManager() != null
                && plugin.getLanguageManager().getLanguage() != null
                && plugin.getLanguageManager().getLanguage().toLowerCase(java.util.Locale.ROOT).startsWith("zh")
                        ? zh
                        : en;
    }

    private String color(String input) {
        return org.cubexmc.utils.ColorUtils.translateColorCodes(input);
    }

    private record Entry(Severity severity, String message) {
    }

    private enum Severity {
        OK,
        WARNING,
        ERROR
    }
}
