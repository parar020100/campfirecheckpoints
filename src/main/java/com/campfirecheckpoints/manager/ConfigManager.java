// src/main/java/com/campfirecheckpoints/manager/ConfigManager.java
package com.campfirecheckpoints.manager;

import com.campfirecheckpoints.CampfireCheckpoints;
import com.campfirecheckpoints.model.RespawnPriority;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

public final class ConfigManager {

    private final @NotNull CampfireCheckpoints plugin;

    // Cached config values
    private boolean enableRegularCampfires;
    private boolean enableSoulCampfires;
    private int radius;
    private boolean extinguishOnRespawn;
    private @NotNull Sound soundOnSet;
    private int overrideConfirmationTimeout;
    private int maxCheckpointsPerPlayer;
    private @NotNull RespawnPriority respawnPriority;

    // Default values
    private static final boolean DEFAULT_ENABLE_REGULAR = true;
    private static final boolean DEFAULT_ENABLE_SOUL = true;
    private static final int DEFAULT_RADIUS = 500;
    private static final boolean DEFAULT_EXTINGUISH = true;
    private static final Sound DEFAULT_SOUND = Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN;
    private static final int DEFAULT_OVERRIDE_TIMEOUT = 5;
    private static final int DEFAULT_MAX_CHECKPOINTS = 0; // 0 = unlimited
    private static final RespawnPriority DEFAULT_RESPAWN_PRIORITY = RespawnPriority.CHECKPOINT;

    public ConfigManager(@NotNull CampfireCheckpoints plugin) {
        this.plugin = plugin;
        this.soundOnSet = DEFAULT_SOUND;
        this.respawnPriority = DEFAULT_RESPAWN_PRIORITY;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();

        this.enableRegularCampfires = config.GetBoolean("enable-regular-campfires", DEFAULT_ENABLE_REGULAR);
        this.enableSoulCampfires = config.GetBoolean("enable-soul-campfires", DEFAULT_ENABLE_SOUL);

        // Load radius
        this.radius = config.getInt("radius", DEFAULT_RADIUS);
        if (radius <= 0) {
            plugin.getLogger().warning("Invalid radius in config. Using default: " + DEFAULT_RADIUS);
            this.radius = DEFAULT_RADIUS;
        }

        // Load extinguish-on-respawn
        this.extinguishOnRespawn = config.getBoolean("extinguish-on-respawn", DEFAULT_EXTINGUISH);

        // Load override confirmation timeout
        this.overrideConfirmationTimeout = config.getInt("override-confirmation-timeout", DEFAULT_OVERRIDE_TIMEOUT);
        if (overrideConfirmationTimeout <= 0) {
            plugin.getLogger().warning("Invalid override-confirmation-timeout in config. Using default: " + DEFAULT_OVERRIDE_TIMEOUT);
            this.overrideConfirmationTimeout = DEFAULT_OVERRIDE_TIMEOUT;
        }

        // Load max checkpoints per player
        this.maxCheckpointsPerPlayer = config.getInt("max-checkpoints-per-player", DEFAULT_MAX_CHECKPOINTS);
        if (maxCheckpointsPerPlayer < 0) {
            plugin.getLogger().warning("Invalid max-checkpoints-per-player in config. Using default: " + DEFAULT_MAX_CHECKPOINTS);
            this.maxCheckpointsPerPlayer = DEFAULT_MAX_CHECKPOINTS;
        }

        // Load respawn priority
        String priorityValue = config.getString("respawn-priority", DEFAULT_RESPAWN_PRIORITY.getConfigValue());
        this.respawnPriority = RespawnPriority.fromConfig(priorityValue);

        // Load sound
        String soundName = config.getString("sound-on-set", DEFAULT_SOUND.name());
        try {
            this.soundOnSet = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING,
                "Invalid sound '" + soundName + "' in config. Using default.");
            this.soundOnSet = DEFAULT_SOUND;
        }

        plugin.getLogger().info("Configuration loaded " +
            "- Regular campfires: " + enableRegularCampfires +
            ", Soul campfires: " + enableSoulCampfires +
            ", Radius: " + radius +
            ", Extinguish: " + extinguishOnRespawn +
            ", Timeout: " + overrideConfirmationTimeout + "s" +
            ", MaxCheckpoints: " + (maxCheckpointsPerPlayer == 0 ? "unlimited" : maxCheckpointsPerPlayer) +
            ", RespawnPriority: " + respawnPriority.getConfigValue() +
            ", Sound: " + soundOnSet.name());
    }

    public boolean RegularCampfiresEnabled() {
        return enableRegularCampfires;
    }

    public boolean SoulCampfiresEnabled() {
        return enableSoulCampfires;
    }

    public int getRadius() {
        return radius;
    }

    public boolean isExtinguishOnRespawn() {
        return extinguishOnRespawn;
    }

    public @NotNull Sound getSoundOnSet() {
        return soundOnSet;
    }

    public int getOverrideConfirmationTimeout() {
        return overrideConfirmationTimeout;
    }

    public long getOverrideConfirmationTimeoutMillis() {
        return overrideConfirmationTimeout * 1000L;
    }

    public int getMaxCheckpointsPerPlayer() {
        return maxCheckpointsPerPlayer;
    }

    public boolean hasCheckpointLimit() {
        return maxCheckpointsPerPlayer > 0;
    }

    public @NotNull RespawnPriority getRespawnPriority() {
        return respawnPriority;
    }
}