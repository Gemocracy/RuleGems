package org.cubexmc.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.bukkit.parser.location.LocationParser;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.cubexmc.features.appoint.AppointFeature;

import org.cubexmc.RuleGems;
import org.cubexmc.commands.sub.AppointSubCommand;
import org.cubexmc.commands.sub.AppointeesSubCommand;
import org.cubexmc.commands.sub.DismissSubCommand;
import org.cubexmc.commands.sub.HistorySubCommand;
import org.cubexmc.commands.sub.PlaceSubCommand;
import org.cubexmc.commands.sub.RedeemSubCommand;
import org.cubexmc.commands.sub.RemoveAltarSubCommand;
import org.cubexmc.commands.sub.RevokeSubCommand;
import org.cubexmc.commands.sub.SetAltarSubCommand;
import org.cubexmc.commands.sub.TpSubCommand;
import org.cubexmc.gui.GUIManager;
import org.cubexmc.manager.GameplayConfig;
import org.cubexmc.manager.GemManager;
import org.cubexmc.manager.LanguageManager;
import org.cubexmc.manager.RuleGemsDoctor;
import org.cubexmc.model.GemDefinition;
import org.cubexmc.features.GemNavigator;

/**
 * Registers all /rulegems sub-commands via the Incendo Cloud 2.0 framework.
 */
public class CloudCommandManager {

    private final RuleGems plugin;
    private final GemManager gemManager;
    private final GameplayConfig gameplayConfig;
    private final LanguageManager languageManager;
    private final GUIManager guiManager;

    // Existing sub-command handler instances (reused from previous implementation)
    private final TpSubCommand tpSubCommand;
    private final PlaceSubCommand placeSubCommand;
    private final RevokeSubCommand revokeSubCommand;
    private final HistorySubCommand historySubCommand;
    private final SetAltarSubCommand setAltarSubCommand;
    private final RemoveAltarSubCommand removeAltarSubCommand;
    private final AppointSubCommand appointSubCommand;
    private final DismissSubCommand dismissSubCommand;
    private final AppointeesSubCommand appointeesSubCommand;

    public CloudCommandManager(RuleGems plugin, GemManager gemManager, GameplayConfig gameplayConfig,
            LanguageManager languageManager, GUIManager guiManager) {
        this.plugin = plugin;
        this.gemManager = gemManager;
        this.gameplayConfig = gameplayConfig;
        this.languageManager = languageManager;
        this.guiManager = guiManager;

        this.tpSubCommand = new TpSubCommand(plugin, gemManager, languageManager);
        this.placeSubCommand = new PlaceSubCommand(gemManager, languageManager);
        this.revokeSubCommand = new RevokeSubCommand(gemManager, languageManager);
        this.historySubCommand = new HistorySubCommand(plugin, languageManager);
        this.setAltarSubCommand = new SetAltarSubCommand(gemManager, languageManager);
        this.removeAltarSubCommand = new RemoveAltarSubCommand(gemManager, languageManager);
        this.appointSubCommand = new AppointSubCommand(plugin, gemManager, languageManager);
        this.dismissSubCommand = new DismissSubCommand(plugin, gemManager, languageManager);
        this.appointeesSubCommand = new AppointeesSubCommand(plugin, gemManager, languageManager);
    }

    /**
     * Creates the Cloud CommandManager and registers all commands.
     */
    public void registerAll() {
        CommandManager<RuleGemsCommandActor> modernManager = tryCreateModernManager();
        if (modernManager != null) {
            configureManager(modernManager);
            plugin.getLogger().info("Using modern Paper command manager for /rulegems.");
            return;
        }

        LegacyPaperCommandManager<RuleGemsCommandActor> legacyManager = tryCreateLegacyManager();
        if (legacyManager != null) {
            configureLegacyCapabilities(legacyManager);
            configureManager(legacyManager);
            plugin.getLogger().info("Using legacy Paper command manager for /rulegems.");
            return;
        }

        plugin.getLogger().warning("Cloud command bootstrap exhausted modern and legacy paths; using Bukkit fallback.");
        registerFallbackExecutor();
    }

    private CommandManager<RuleGemsCommandActor> tryCreateModernManager() {
        if (!isModernPaperApiAvailable()) {
            plugin.getLogger().info("Modern Paper command API not detected; skipping modern Cloud bootstrap.");
            return null;
        }

        try {
            SenderMapper<?, RuleGemsCommandActor> senderMapper = createModernSenderMapper();
            Class<?> managerClass = Class.forName("org.incendo.cloud.paper.PaperCommandManager");
            Method builderMethod = managerClass.getMethod("builder", SenderMapper.class);
            Object builder = builderMethod.invoke(null, senderMapper);
            Method executionCoordinator = builder.getClass()
                    .getMethod("executionCoordinator", ExecutionCoordinator.class);
            Object coordinatedBuilder = executionCoordinator.invoke(builder, ExecutionCoordinator.simpleCoordinator());
            Method buildOnEnable = coordinatedBuilder.getClass().getMethod("buildOnEnable", org.bukkit.plugin.Plugin.class);
            Object manager = buildOnEnable.invoke(coordinatedBuilder, plugin);
            @SuppressWarnings("unchecked")
            CommandManager<RuleGemsCommandActor> castManager = (CommandManager<RuleGemsCommandActor>) manager;
            return castManager;
        } catch (Throwable failure) {
            plugin.getLogger().warning("Modern Paper command manager initialization failed; trying legacy bootstrap next.");
            diagnoseBootstrapFailure("modern", failure);
            return null;
        }
    }

    private LegacyPaperCommandManager<RuleGemsCommandActor> tryCreateLegacyManager() {
        try {
            return new LegacyPaperCommandManager<>(
                    plugin,
                    ExecutionCoordinator.simpleCoordinator(),
                    SenderMapper.create(RuleGemsCommandActor::legacy, RuleGemsCommandActor::sender));
        } catch (Throwable failure) {
            plugin.getLogger().warning("Legacy Paper command manager initialization failed; falling back to Bukkit bridge.");
            diagnoseLegacyInitializationFailure(failure);
            return null;
        }
    }

    private void configureLegacyCapabilities(LegacyPaperCommandManager<RuleGemsCommandActor> manager) {
        try {
            if (manager.hasCapability(org.incendo.cloud.bukkit.CloudBukkitCapabilities.BRIGADIER)) {
                manager.registerBrigadier();
            }
            if (manager.hasCapability(org.incendo.cloud.bukkit.CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
                manager.registerAsynchronousCompletions();
            }
        } catch (Exception capabilityFailure) {
            plugin.getLogger().warning("Legacy Paper command manager initialized, but optional capability setup failed: "
                    + capabilityFailure.getClass().getSimpleName() + " - " + capabilityFailure.getMessage());
        }
    }

    private void configureManager(CommandManager<RuleGemsCommandActor> manager) {
        manager.exceptionController()
                .registerHandler(NoPermissionException.class,
                        ctx -> languageManager.sendMessage(ctx.context().sender().sender(), "command.no_permission"))
                .registerHandler(InvalidSyntaxException.class,
                        ctx -> languageManager.sendMessage(ctx.context().sender().sender(), "command.invalid_syntax"));

        registerSharedCommands(manager);
    }

    private void registerSharedCommands(CommandManager<RuleGemsCommandActor> manager) {
        registerHelp(manager);
        registerReload(manager);
        registerGui(manager);
        registerProfile(manager);
        registerCabinet(manager);
        registerRulers(manager);
        registerGems(manager);
        registerTp(manager);
        registerScatter(manager);
        registerRedeem(manager);
        registerRedeemAll(manager);
        registerPlace(manager);
        registerRevoke(manager);
        registerHistory(manager);
        registerSetAltar(manager);
        registerRemoveAltar(manager);
        registerAppoint(manager);
        registerDismiss(manager);
        registerAppointees(manager);
        registerDoctor(manager);
    }

    private boolean isModernPaperApiAvailable() {
        try {
            Class.forName("io.papermc.paper.command.brigadier.CommandSourceStack");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            return false;
        }
    }

    private SenderMapper<?, RuleGemsCommandActor> createModernSenderMapper() {
        return SenderMapper.create(
                source -> RuleGemsCommandActor.modern(extractBukkitSender(source), source),
                actor -> ((RuleGemsCommandActor) actor).nativeSender());
    }

    private CommandSender extractBukkitSender(Object sourceStack) {
        try {
            Method getSender = sourceStack.getClass().getMethod("getSender");
            Object sender = getSender.invoke(sourceStack);
            if (sender instanceof CommandSender) {
                return (CommandSender) sender;
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to unwrap CommandSender from modern Paper source stack", e);
        }
        throw new IllegalStateException("Modern Paper source stack did not provide a Bukkit CommandSender");
    }

    private Player requirePlayer(RuleGemsCommandActor actor) {
        Player player = actor.player();
        if (player == null) {
            languageManager.sendMessage(actor.sender(), "command.player_only");
        }
        return player;
    }

    private void diagnoseBootstrapFailure(String path, Throwable original) {
        Throwable failure = unwrapInvocationTarget(original);
        plugin.getLogger().warning("Cloud " + path + " bootstrap failure on server "
                + Bukkit.getServer().getClass().getName());
        logThrowableChain(path, failure);
    }

    private Throwable unwrapInvocationTarget(Throwable original) {
        if (original instanceof InvocationTargetException && original.getCause() != null) {
            return original.getCause();
        }
        return original;
    }

    private void logThrowableChain(String label, Throwable original) {
        Throwable cursor = original;
        int depth = 0;
        while (cursor != null && depth < 5) {
            plugin.getLogger().warning("Cloud " + label + " cause[" + depth + "]: "
                    + cursor.getClass().getName() + " - " + cursor.getMessage());
            cursor = cursor.getCause();
            depth++;
        }
    }

    private void registerFallbackExecutor() {
        try {
            Object server = Bukkit.getServer();
            Method getCommandMap = server.getClass().getDeclaredMethod("getCommandMap");
            getCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) getCommandMap.invoke(server);

            org.bukkit.command.Command fallbackCmd = new org.bukkit.command.Command("rulegems") {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    return executeFallback(sender, args);
                }

                @Override
                public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
                    return completeFallback(sender, args);
                }
            };
            fallbackCmd.setAliases(java.util.Arrays.asList("rg"));
            fallbackCmd.setDescription("RuleGems fallback command bridge");

            commandMap.register(plugin.getName(), fallbackCmd);
            plugin.getLogger().warning("Registered Bukkit fallback command bridge for /rulegems dynamically.");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register dynamic Bukkit fallback command: " + e.getMessage());
        }
    }

    private boolean executeFallback(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player && guiManager != null) {
                Player player = (Player) sender;
                guiManager.openMainMenu(player, player.hasPermission("rulegems.admin"));
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] tail = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "help":
                sendHelp(sender);
                return true;
            case "gui":
            case "menu":
                if (!(sender instanceof Player)) {
                    languageManager.sendMessage(sender, "command.player_only");
                    return true;
                }
                if (guiManager != null) {
                    Player player = (Player) sender;
                    guiManager.openMainMenu(player, player.hasPermission("rulegems.admin"));
                }
                return true;
            case "profile":
            case "status":
                if (!requirePermission(sender, "rulegems.profile")) {
                    return true;
                }
                if (!(sender instanceof Player)) {
                    languageManager.sendMessage(sender, "command.player_only");
                    return true;
                }
                if (guiManager != null) {
                    guiManager.openProfileGUI((Player) sender);
                }
                return true;
            case "cabinet":
                if (!(sender instanceof Player)) {
                    languageManager.sendMessage(sender, "command.player_only");
                    return true;
                }
                openCabinetCompat((Player) sender);
                return true;
            case "rulers":
                if (!requirePermission(sender, "rulegems.rulers")) {
                    return true;
                }
                if (sender instanceof Player && guiManager != null) {
                    guiManager.openRulersGUI((Player) sender, sender.hasPermission("rulegems.admin"));
                } else {
                    Map<UUID, Set<String>> holders = gemManager.getCurrentRulers();
                    if (holders.isEmpty()) {
                        languageManager.sendMessage(sender, "command.no_rulers");
                        return true;
                    }
                    for (Map.Entry<UUID, Set<String>> e : holders.entrySet()) {
                        String name = gemManager.getCachedPlayerName(e.getKey());
                        String extra = e.getValue().contains("ALL") ? "ALL" : String.join(",", e.getValue());
                        Map<String, String> ph = new HashMap<>();
                        ph.put("player", name + " (" + extra + ")");
                        languageManager.sendMessage(sender, "command.rulers_status", ph);
                    }
                }
                return true;
            case "gems":
                if (!requirePermission(sender, "rulegems.gems")) {
                    return true;
                }
                if (sender instanceof Player && guiManager != null) {
                    guiManager.openGemsGUI((Player) sender, sender.hasPermission("rulegems.admin"));
                } else {
                    gemManager.gemStatus(sender);
                }
                return true;
            case "redeem":
                if (!requirePermission(sender, "rulegems.redeem")) {
                    return true;
                }
                if (!(sender instanceof Player)) {
                    languageManager.sendMessage(sender, "command.player_only");
                    return true;
                }
                if (!gameplayConfig.isRedeemEnabled()) {
                    languageManager.sendMessage(sender, "command.redeem.disabled");
                    return true;
                }
                return new RedeemSubCommand(gemManager, languageManager).execute(sender, tail);
            case "redeemall":
                if (!requirePermission(sender, "rulegems.redeemall")) {
                    return true;
                }
                if (!(sender instanceof Player)) {
                    languageManager.sendMessage(sender, "command.player_only");
                    return true;
                }
                if (!gameplayConfig.isFullSetGrantsAllEnabled()) {
                    languageManager.sendMessage(sender, "command.redeemall.disabled");
                    return true;
                }
                boolean ok = gemManager.redeemAll((Player) sender);
                languageManager.sendMessage(sender, ok ? "command.redeemall.success" : "command.redeemall.failed");
                return true;
            case "appoint":
                if (!(sender instanceof Player)) {
                    languageManager.sendMessage(sender, "command.player_only");
                    return true;
                }
                return appointSubCommand.execute(sender, tail);
            case "dismiss":
                if (!(sender instanceof Player)) {
                    languageManager.sendMessage(sender, "command.player_only");
                    return true;
                }
                return dismissSubCommand.execute(sender, tail);
            case "appointees":
                if (!requirePermission(sender, "rulegems.admin")) {
                    return true;
                }
                return appointeesSubCommand.execute(sender, tail);
            case "place":
                if (!requirePermission(sender, "rulegems.admin")) {
                    return true;
                }
                if (!(sender instanceof Player)) {
                    languageManager.sendMessage(sender, "command.player_only");
                    return true;
                }
                return placeSubCommand.execute(sender, tail);
            case "tp":
                if (!requirePermission(sender, "rulegems.admin")) {
                    return true;
                }
                if (!(sender instanceof Player)) {
                    languageManager.sendMessage(sender, "command.player_only");
                    return true;
                }
                return tpSubCommand.execute(sender, tail);
            case "revoke":
                if (!requirePermission(sender, "rulegems.admin")) {
                    return true;
                }
                return revokeSubCommand.execute(sender, tail);
            case "scatter":
                if (!requirePermission(sender, "rulegems.admin")) {
                    return true;
                }
                gemManager.scatterGems();
                languageManager.sendMessage(sender, "command.scatter_success");
                return true;
            case "history":
                if (!requirePermission(sender, "rulegems.admin")) {
                    return true;
                }
                return historySubCommand.execute(sender, tail);
            case "setaltar":
                if (!requirePermission(sender, "rulegems.admin")) {
                    return true;
                }
                if (!(sender instanceof Player)) {
                    languageManager.sendMessage(sender, "command.player_only");
                    return true;
                }
                return setAltarSubCommand.execute(sender, tail);
            case "removealtar":
                if (!requirePermission(sender, "rulegems.admin")) {
                    return true;
                }
                if (!(sender instanceof Player)) {
                    languageManager.sendMessage(sender, "command.player_only");
                    return true;
                }
                return removeAltarSubCommand.execute(sender, tail);
            case "doctor":
                if (!requirePermission(sender, "rulegems.admin")) {
                    return true;
                }
                new RuleGemsDoctor(plugin).sendReport(sender);
                return true;
            case "reload":
                if (!requirePermission(sender, "rulegems.admin")) {
                    return true;
                }
                gemManager.saveGems();
                plugin.loadPlugin();
                plugin.refreshAllowedCommandProxies();
                languageManager.sendMessage(sender, "command.reload_success");
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private List<String> completeFallback(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> roots = new ArrayList<>(List.of(
                    "help", "gui", "menu", "profile", "status", "cabinet", "gems", "rulers",
                    "redeem", "redeemall", "appoint", "dismiss", "appointees", "place", "tp",
                    "revoke", "scatter", "history", "setaltar", "removealtar", "doctor", "reload"));
            return roots.stream()
                    .filter(value -> value.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (("appoint".equals(sub) || "dismiss".equals(sub) || "appointees".equals(sub)) && args.length == 2) {
            AppointFeature feature = plugin.getFeatureManager() != null ? plugin.getFeatureManager().getAppointFeature() : null;
            if (feature == null) {
                return java.util.Collections.emptyList();
            }
            return feature.getAppointDefinitions().keySet().stream()
                    .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        if (("appoint".equals(sub) || "dismiss".equals(sub)) && args.length == 3) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        if (("setaltar".equals(sub) || "removealtar".equals(sub)) && args.length == 2) {
            return plugin.getGemParser().getGemDefinitions().stream()
                    .map(GemDefinition::getGemKey)
                    .filter(key -> key != null && key.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (permission == null || permission.isBlank() || sender.hasPermission(permission)) {
            return true;
        }
        languageManager.sendMessage(sender, "command.no_permission");
        return false;
    }

    private void openCabinetCompat(Player player) {
        AppointFeature appointFeature = plugin.getFeatureManager() != null
                ? plugin.getFeatureManager().getAppointFeature()
                : null;
        if (appointFeature == null || !appointFeature.isEnabled()) {
            languageManager.sendMessage(player, "command.appoint.disabled");
            return;
        }
        if (guiManager == null || !guiManager.canOpenCabinet(player)) {
            languageManager.sendMessage(player, "command.no_permission");
            return;
        }
        guiManager.openCabinetGUI(player);
    }

    private void diagnoseLegacyInitializationFailure(Throwable original) {
        Throwable failure = unwrapInvocationTarget(original);
        plugin.getLogger().severe("Cloud legacy bootstrap failure on server "
                + Bukkit.getServer().getClass().getName());
        logThrowableChain("legacy", failure);

        Object server = Bukkit.getServer();
        if (server == null) {
            plugin.getLogger().severe("Bukkit.getServer() returned null during Cloud initialization.");
            return;
        }

        Class<?> serverClass = server.getClass();
        try {
            Method method = serverClass.getDeclaredMethod("getCommandMap");
            method.setAccessible(true);
            Object commandMap = method.invoke(server);
            plugin.getLogger().info("Legacy Cloud preflight: server#getCommandMap() is declared on "
                    + serverClass.getName() + " and returned "
                    + (commandMap != null ? commandMap.getClass().getName() : "null"));
        } catch (Exception reflectiveFailure) {
            plugin.getLogger().severe("Legacy Cloud preflight failed to access server#getCommandMap() directly: "
                    + reflectiveFailure.getClass().getSimpleName() + " - " + reflectiveFailure.getMessage());
        }

        try {
            CommandMap commandMap = (CommandMap) Bukkit.class.getMethod("getCommandMap").invoke(null);
            plugin.getLogger().info("Bukkit static getCommandMap() succeeded with: "
                    + (commandMap != null ? commandMap.getClass().getName() : "null"));
        } catch (Exception staticFailure) {
            plugin.getLogger().warning("Bukkit static getCommandMap() also failed: "
                    + staticFailure.getClass().getSimpleName() + " - " + staticFailure.getMessage());
        }

        try {
            Field knownCommands = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommands.setAccessible(true);
            plugin.getLogger().info("SimpleCommandMap.knownCommands field is accessible for fallback diagnostics.");
        } catch (Exception fieldFailure) {
            plugin.getLogger().warning("SimpleCommandMap.knownCommands reflection failed: "
                    + fieldFailure.getClass().getSimpleName() + " - " + fieldFailure.getMessage());
        }
    }

    // ======================================================================
    // Individual command registrations
    // ======================================================================

    private void registerHelp(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .handler(ctx -> {
                    CommandSender sender = ctx.sender().sender();
                    Player player = ctx.sender().player();
                    if (player != null && guiManager != null) {
                        guiManager.openMainMenu(player, player.hasPermission("rulegems.admin"));
                        return;
                    }
                    sendHelp(sender);
                }));
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("help")
                .permission("rulegems.help")
                .handler(ctx -> sendHelp(ctx.sender().sender())));
    }

    private void registerReload(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("reload")
                .permission("rulegems.admin")
                .handler(ctx -> {
                    gemManager.saveGems();
                    plugin.loadPlugin();
                    plugin.refreshAllowedCommandProxies();
                    languageManager.sendMessage(ctx.sender().sender(), "command.reload_success");
                }));
    }

    private void registerGui(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("gui")
                .handler(ctx -> {
                    Player p = requirePlayer(ctx.sender());
                    if (p == null) {
                        return;
                    }
                    if (guiManager != null)
                        guiManager.openMainMenu(p, p.hasPermission("rulegems.admin"));
                }));
        // alias /rg menu
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("menu")
                .handler(ctx -> {
                    Player p = requirePlayer(ctx.sender());
                    if (p == null) {
                        return;
                    }
                    if (guiManager != null)
                        guiManager.openMainMenu(p, p.hasPermission("rulegems.admin"));
                }));
    }

    private void registerProfile(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("profile")
                .permission("rulegems.profile")
                .handler(ctx -> {
                    Player player = requirePlayer(ctx.sender());
                    if (player == null) {
                        return;
                    }
                    if (guiManager != null) {
                        guiManager.openProfileGUI(player);
                    }
                }));
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("status")
                .permission("rulegems.profile")
                .handler(ctx -> {
                    Player player = requirePlayer(ctx.sender());
                    if (player == null) {
                        return;
                    }
                    if (guiManager != null) {
                        guiManager.openProfileGUI(player);
                    }
                }));
    }

    private void registerCabinet(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("cabinet")
                .handler(ctx -> {
                    Player player = requirePlayer(ctx.sender());
                    if (player == null) {
                        return;
                    }
                    AppointFeature appointFeature = plugin.getFeatureManager() != null
                            ? plugin.getFeatureManager().getAppointFeature()
                            : null;
                    if (appointFeature == null || !appointFeature.isEnabled()) {
                        languageManager.sendMessage(player, "command.appoint.disabled");
                        return;
                    }
                    if (guiManager == null || !guiManager.canOpenCabinet(player)) {
                        languageManager.sendMessage(player, "command.no_permission");
                        return;
                    }
                    guiManager.openCabinetGUI(player);
                }));
    }

    private void registerRulers(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("rulers")
                .permission("rulegems.rulers")
                .handler(ctx -> {
                    CommandSender sender = ctx.sender().sender();
                    Player player = ctx.sender().player();
                    if (player != null && guiManager != null) {
                        guiManager.openRulersGUI(player, sender.hasPermission("rulegems.admin"));
                    } else {
                        Map<UUID, Set<String>> holders = gemManager.getCurrentRulers();
                        if (holders.isEmpty()) {
                            languageManager.sendMessage(sender, "command.no_rulers");
                            return;
                        }
                        for (Map.Entry<UUID, Set<String>> e : holders.entrySet()) {
                            String name = gemManager.getCachedPlayerName(e.getKey());
                            String extra = e.getValue().contains("ALL") ? "ALL" : String.join(",", e.getValue());
                            Map<String, String> ph = new HashMap<>();
                            ph.put("player", name + " (" + extra + ")");
                            languageManager.sendMessage(sender, "command.rulers_status", ph);
                        }
                    }
                }));
    }

    private void registerGems(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("gems")
                .permission("rulegems.gems")
                .handler(ctx -> {
                    CommandSender sender = ctx.sender().sender();
                    Player player = ctx.sender().player();
                    if (player != null && guiManager != null) {
                        guiManager.openGemsGUI(player, sender.hasPermission("rulegems.admin"));
                    } else {
                        gemManager.gemStatus(sender);
                    }
                }));
    }

    private void registerTp(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("tp")
                .permission("rulegems.admin")
                .required("gem_id", StringParser.stringParser(), getGemKeySuggestions())
                .handler(ctx -> {
                    Player player = requirePlayer(ctx.sender());
                    if (player != null) {
                        tpSubCommand.execute(player, new String[] { ctx.get("gem_id") });
                    }
                }));
    }

    private void registerScatter(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("scatter")
                .permission("rulegems.admin")
                .handler(ctx -> {
                    gemManager.scatterGems();
                    languageManager.sendMessage(ctx.sender().sender(), "command.scatter_success");
                }));
    }

    private void registerRedeem(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("redeem")
                .permission("rulegems.redeem")
                .handler(ctx -> {
                    Player player = requirePlayer(ctx.sender());
                    if (player == null) {
                        return;
                    }
                    if (!gameplayConfig.isRedeemEnabled()) {
                        languageManager.sendMessage(player, "command.redeem.disabled");
                        return;
                    }
                    new RedeemSubCommand(gemManager, languageManager).execute(player, new String[0]);
                }));
    }

    private void registerRedeemAll(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("redeemall")
                .permission("rulegems.redeemall")
                .handler(ctx -> {
                    Player player = requirePlayer(ctx.sender());
                    if (player == null) {
                        return;
                    }
                    if (!gameplayConfig.isFullSetGrantsAllEnabled()) {
                        languageManager.sendMessage(player, "command.redeemall.disabled");
                        return;
                    }
                    boolean ok = gemManager.redeemAll(player);
                    if (!ok) {
                        languageManager.sendMessage(player, "command.redeemall.failed");
                        return;
                    }
                    languageManager.sendMessage(player, "command.redeemall.success");
                }));
    }

    private void registerPlace(CommandManager<RuleGemsCommandActor> m) {
        // /rg place <gemId> <x> <y> <z>
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("place")
                .permission("rulegems.admin")
                .required("gem_id", StringParser.stringParser(), getGemKeySuggestions())
                .required("location", LocationParser.locationParser())
                .handler(ctx -> {
                    Player player = requirePlayer(ctx.sender());
                    if (player == null) {
                        return;
                    }
                    org.bukkit.Location loc = ctx.get("location");
                    String[] args = new String[] {
                            ctx.get("gem_id"),
                            String.valueOf(loc.getX()),
                            String.valueOf(loc.getY()),
                            String.valueOf(loc.getZ())
                    };
                    placeSubCommand.execute(player, args);
                }));
    }

    private void registerRevoke(CommandManager<RuleGemsCommandActor> m) {
        // /rg revoke <player> [gemKey]
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("revoke")
                .permission("rulegems.admin")
                .required("player_name", StringParser.stringParser(), getOnlinePlayerSuggestions())
                .optional("gem_key", StringParser.stringParser(), getGemKeySuggestions())
                .handler(ctx -> {
                    String playerName = ctx.get("player_name");
                    String gemKey = ctx.contains("gem_key") ? ctx.get("gem_key") : null;
                    String[] args = gemKey != null
                            ? new String[] { playerName, gemKey }
                            : new String[] { playerName };
                    revokeSubCommand.execute(ctx.sender().sender(), args);
                }));
    }

    private void registerHistory(CommandManager<RuleGemsCommandActor> m) {
        // /rg history [page] [player]
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("history")
                .permission("rulegems.admin")
                .optional("page", IntegerParser.integerParser(1))
                .optional("player_name", StringParser.stringParser(), getOnlinePlayerSuggestions())
                .handler(ctx -> {
                    int page = ctx.contains("page") ? (int) ctx.get("page") : 1;
                    String player = ctx.contains("player_name") ? ctx.get("player_name") : null;
                    String[] args = player != null
                            ? new String[] { String.valueOf(page), player }
                            : new String[] { String.valueOf(page) };
                    historySubCommand.execute(ctx.sender().sender(), args);
                }));
    }

    private void registerSetAltar(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("setaltar")
                .permission("rulegems.admin")
                .required("gem_key", StringParser.stringParser(), getGemKeySuggestions())
                .handler(ctx -> {
                    Player player = requirePlayer(ctx.sender());
                    if (player != null) {
                        setAltarSubCommand.execute(player, new String[] { ctx.get("gem_key") });
                    }
                }));
    }

    private void registerRemoveAltar(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("removealtar")
                .permission("rulegems.admin")
                .required("gem_key", StringParser.stringParser(), getGemKeySuggestions())
                .handler(ctx -> {
                    Player player = requirePlayer(ctx.sender());
                    if (player != null) {
                        removeAltarSubCommand.execute(player, new String[] { ctx.get("gem_key") });
                    }
                }));
    }

    private void registerDoctor(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("doctor")
                .permission("rulegems.admin")
                .handler(ctx -> new RuleGemsDoctor(plugin).sendReport(ctx.sender().sender())));
    }

    private SuggestionProvider<RuleGemsCommandActor> getPermSetSuggestions() {
        return SuggestionProvider.blocking((ctx, input) -> {
            AppointFeature feature = plugin.getFeatureManager() != null
                    ? plugin.getFeatureManager().getAppointFeature()
                    : null;
            if (feature == null) {
                return java.util.Collections.emptyList();
            }

            CommandSender sender = ctx.sender().sender();
            List<Suggestion> suggestions = new ArrayList<>();
            for (String key : feature.getAppointDefinitions().keySet()) {
                if (sender.hasPermission("rulegems.appoint." + key.toLowerCase(java.util.Locale.ROOT))
                        || sender.hasPermission("rulegems.appoint." + key)
                        || sender.hasPermission("rulegems.admin")) {
                    suggestions.add(Suggestion.suggestion(key));
                }
            }
            return suggestions;
        });
    }

    private SuggestionProvider<RuleGemsCommandActor> getGemKeySuggestions() {
        return SuggestionProvider.blocking((ctx, input) -> {
            Set<String> keys = new LinkedHashSet<>();
            for (GemDefinition definition : plugin.getGemParser().getGemDefinitions()) {
                if (definition == null || definition.getGemKey() == null || definition.getGemKey().isBlank()) {
                    continue;
                }
                keys.add(definition.getGemKey());
            }
            return keys.stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .map(Suggestion::suggestion)
                    .collect(Collectors.toList());
        });
    }

    private SuggestionProvider<RuleGemsCommandActor> getAllPermSetSuggestions() {
        return SuggestionProvider.blocking((ctx, input) -> {
            AppointFeature feature = plugin.getFeatureManager() != null
                    ? plugin.getFeatureManager().getAppointFeature()
                    : null;
            if (feature == null) {
                return java.util.Collections.emptyList();
            }

            return feature.getAppointDefinitions().keySet().stream()
                    .map(Suggestion::suggestion)
                    .collect(Collectors.toList());
        });
    }

    private SuggestionProvider<RuleGemsCommandActor> getOnlinePlayerSuggestions() {
        return SuggestionProvider.blocking((ctx, input) -> {
            return Bukkit.getOnlinePlayers().stream()
                    .map(p -> Suggestion.suggestion(p.getName()))
                    .collect(Collectors.toList());
        });
    }

    private void registerAppoint(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("appoint")
                .required("perm_set", StringParser.stringParser(), getPermSetSuggestions())
                .required("player", StringParser.stringParser(), getOnlinePlayerSuggestions())
                .handler(ctx -> {
                    Player player = requirePlayer(ctx.sender());
                    if (player == null) {
                        return;
                    }
                    String[] args = new String[] { ctx.get("perm_set"), ctx.get("player") };
                    appointSubCommand.execute(player, args);
                }));
    }

    private void registerDismiss(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("dismiss")
                .required("perm_set", StringParser.stringParser(), getPermSetSuggestions())
                .required("player", StringParser.stringParser(), getOnlinePlayerSuggestions())
                .handler(ctx -> {
                    Player player = requirePlayer(ctx.sender());
                    if (player == null) {
                        return;
                    }
                    String[] args = new String[] { ctx.get("perm_set"), ctx.get("player") };
                    dismissSubCommand.execute(player, args);
                }));
    }

    private void registerAppointees(CommandManager<RuleGemsCommandActor> m) {
        m.command(m.commandBuilder("rulegems", "rg")
                .literal("appointees")
                .permission("rulegems.admin")
                .optional("perm_set", StringParser.stringParser(), getAllPermSetSuggestions())
                .handler(ctx -> {
                    String[] args = ctx.contains("perm_set") ? new String[] { ctx.get("perm_set") } : new String[0];
                    appointeesSubCommand.execute(ctx.sender().sender(), args);
                }));
    }

    // ======================================================================
    // Help
    // ======================================================================

    private void sendHelp(CommandSender sender) {
        languageManager.sendMessage(sender, "command.help.header");
        languageManager.sendMessage(sender, "command.help.overview");

        boolean isPlayer = sender instanceof Player;
        boolean isAdmin = sender.hasPermission("rulegems.admin");
        boolean hasPlayerSection = false;

        if (isPlayer) {
            languageManager.sendMessage(sender, "command.help.section_player");
            hasPlayerSection = true;
            languageManager.sendMessage(sender, "command.help.gui");
        }
        if (sender.hasPermission("rulegems.gems")) {
            if (!hasPlayerSection) {
                languageManager.sendMessage(sender, "command.help.section_player");
                hasPlayerSection = true;
            }
            languageManager.sendMessage(sender, "command.help.gems");
        }
        if (sender.hasPermission("rulegems.rulers")) {
            if (!hasPlayerSection) {
                languageManager.sendMessage(sender, "command.help.section_player");
                hasPlayerSection = true;
            }
            languageManager.sendMessage(sender, "command.help.rulers");
        }
        if (isPlayer && sender.hasPermission("rulegems.profile")) {
            languageManager.sendMessage(sender, "command.help.profile");
        }
        if (gameplayConfig.isRedeemEnabled() && sender.hasPermission("rulegems.redeem")) {
            if (!hasPlayerSection) {
                languageManager.sendMessage(sender, "command.help.section_player");
                hasPlayerSection = true;
            }
            languageManager.sendMessage(sender, "command.help.redeem");
        }
        if (gameplayConfig.isFullSetGrantsAllEnabled() && sender.hasPermission("rulegems.redeemall")) {
            if (!hasPlayerSection) {
                languageManager.sendMessage(sender, "command.help.section_player");
                hasPlayerSection = true;
            }
            languageManager.sendMessage(sender, "command.help.redeemall");
        }
        if (gameplayConfig.isHoldToRedeemEnabled() && gameplayConfig.isRedeemEnabled() && sender.hasPermission("rulegems.redeem")) {
            if (!hasPlayerSection) {
                languageManager.sendMessage(sender, "command.help.section_player");
                hasPlayerSection = true;
            }
            languageManager.sendMessage(sender,
                    gameplayConfig.isSneakToRedeem() ? "command.help.hold_redeem_sneak" : "command.help.hold_redeem_normal");
        }
        if (gameplayConfig.isPlaceRedeemEnabled()) {
            if (!hasPlayerSection) {
                languageManager.sendMessage(sender, "command.help.section_player");
                hasPlayerSection = true;
            }
            languageManager.sendMessage(sender, "command.help.place_redeem");
        }

        GemNavigator navigator = plugin.getFeatureManager() != null ? plugin.getFeatureManager().getNavigator() : null;
        if (navigator != null && navigator.isEnabled() && sender.hasPermission("rulegems.navigate")) {
            if (!hasPlayerSection) {
                languageManager.sendMessage(sender, "command.help.section_player");
                hasPlayerSection = true;
            }
            languageManager.sendMessage(sender, "command.help.navigate");
        }

        AppointFeature appointFeature = plugin.getFeatureManager() != null
                ? plugin.getFeatureManager().getAppointFeature()
                : null;
        if (appointFeature != null && appointFeature.isEnabled() && hasAnyAppointPermission(sender, appointFeature)) {
            if (!hasPlayerSection) {
                languageManager.sendMessage(sender, "command.help.section_player");
                hasPlayerSection = true;
            }
            languageManager.sendMessage(sender, "command.help.cabinet");
            languageManager.sendMessage(sender, "command.help.appoint");
            languageManager.sendMessage(sender, "command.help.dismiss");
        }

        if (isAdmin) {
            languageManager.sendMessage(sender, "command.help.section_admin");
            languageManager.sendMessage(sender, "command.help.place");
            languageManager.sendMessage(sender, "command.help.tp");
            languageManager.sendMessage(sender, "command.help.revoke");
            languageManager.sendMessage(sender, "command.help.scatter");
            languageManager.sendMessage(sender, "command.help.history");
            languageManager.sendMessage(sender, "command.help.setaltar");
            languageManager.sendMessage(sender, "command.help.removealtar");
            languageManager.sendMessage(sender, "command.help.appointees");
            languageManager.sendMessage(sender, "command.help.doctor");
            languageManager.sendMessage(sender, "command.help.reload");
        }

        languageManager.sendMessage(sender, "command.help.help");
        languageManager.sendMessage(sender, "command.help.footer");
    }

    private boolean hasAnyAppointPermission(CommandSender sender, AppointFeature feature) {
        if (sender == null || feature == null) {
            return false;
        }
        if (sender.hasPermission("rulegems.admin")) {
            return true;
        }
        for (String key : feature.getAppointDefinitions().keySet()) {
            if (sender.hasPermission("rulegems.appoint." + key.toLowerCase(Locale.ROOT))
                    || sender.hasPermission("rulegems.appoint." + key)) {
                return true;
            }
        }
        return false;
    }
}
