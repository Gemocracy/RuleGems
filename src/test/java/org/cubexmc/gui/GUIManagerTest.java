package org.cubexmc.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.cubexmc.RuleGems;
import org.cubexmc.features.FeatureManager;
import org.cubexmc.features.appoint.AppointFeature;
import org.cubexmc.manager.GemManager;
import org.cubexmc.manager.LanguageManager;
import org.cubexmc.model.AppointDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class GUIManagerTest {

    private RuleGems plugin;
    private GemManager gemManager;
    private LanguageManager languageManager;
    private FeatureManager featureManager;
    private AppointFeature appointFeature;
    private PluginManager pluginManager;
    private MockedStatic<Bukkit> mockedBukkit;

    @BeforeEach
    void setUp() {
        plugin = mock(RuleGems.class);
        gemManager = mock(GemManager.class);
        languageManager = mock(LanguageManager.class);
        featureManager = mock(FeatureManager.class);
        appointFeature = mock(AppointFeature.class);
        pluginManager = mock(PluginManager.class);

        when(plugin.getFeatureManager()).thenReturn(featureManager);
        when(plugin.getName()).thenReturn("RuleGems");
        when(featureManager.getAppointFeature()).thenReturn(appointFeature);
        when(appointFeature.getAppointDefinitions()).thenReturn(Map.of("guard", new AppointDefinition("guard")));

        mockedBukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
        mockedBukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
    }

    @AfterEach
    void tearDown() {
        if (mockedBukkit != null) {
            mockedBukkit.close();
        }
    }

    @Test
    void cabinetAccessibleForAdminPlayer() {
        Player player = mock(Player.class);
        when(appointFeature.isEnabled()).thenReturn(true);
        when(player.hasPermission("rulegems.admin")).thenReturn(true);

        GUIManager guiManager = new GUIManager(plugin, gemManager, languageManager);

        assertTrue(guiManager.canOpenCabinet(player));
    }

    @Test
    void cabinetAccessibleForPlayerWithAppointPermission() {
        Player player = mock(Player.class);
        when(appointFeature.isEnabled()).thenReturn(true);
        when(player.hasPermission("rulegems.admin")).thenReturn(false);
        when(player.hasPermission("rulegems.appoint.guard")).thenReturn(true);

        GUIManager guiManager = new GUIManager(plugin, gemManager, languageManager);

        assertTrue(guiManager.canOpenCabinet(player));
    }

    @Test
    void cabinetRejectedWithoutFeatureOrPermission() {
        Player player = mock(Player.class);
        when(appointFeature.isEnabled()).thenReturn(false);
        when(player.hasPermission("rulegems.admin")).thenReturn(false);
        when(player.hasPermission("rulegems.appoint.guard")).thenReturn(false);

        GUIManager guiManager = new GUIManager(plugin, gemManager, languageManager);

        assertFalse(guiManager.canOpenCabinet(player));
    }
}
