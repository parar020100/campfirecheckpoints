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

    private boolean overworldEnableRegularCampfires;
    private boolean netherEnableRegularCampfires;
    private boolean endEnableRegularCampfires;
    private boolean overworldEnableSoulCampfires;
    private boolean netherEnableSoulCampfires;
    private boolean endEnableSoulCampfires;

    private int radius;
    private int soulRadius;
    private int minDistance;
    private int soulMinDistance;
    private boolean extinguishOnRespawn;
    private @NotNull Sound soundOnSet;
    private int overrideConfirmationTimeout;
    private int maxCheckpointsPerPlayer;
    private @NotNull RespawnPriority respawnPriority;
    private boolean emptyHandOrSneakRequired;
    private boolean deleteCommandAllowed;

    // Default values
    private static final boolean DEFAULT_DIMENTION_OVERWORLD = true;
    private static final boolean DEFAULT_DIMENTION_NETHER = false;
    private static final boolean DEFAULT_DIMENTION_END = false;
    private static final boolean DEFAULT_DIMENTION_OVERWORLD_SOUL = true;
    private static final boolean DEFAULT_DIMENTION_NETHER_SOUL = true;
    private static final boolean DEFAULT_DIMENTION_END_SOUL = false;

    private static final int DEFAULT_RADIUS = 500;
    private static final int DEFAULT_SOUL_RADIUS = 1000;
    private static final int DEFAULT_MIN_DISTANCE = 250;
    private static final int DEFAULT_SOUL_MIN_DISTANCE = 500;
    private static final boolean DEFAULT_EXTINGUISH = true;
    private static final Sound DEFAULT_SOUND = Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN;
    private static final int DEFAULT_OVERRIDE_TIMEOUT = 5;
    private static final int DEFAULT_MAX_CHECKPOINTS = 0; // 0 = unlimited
    private static final RespawnPriority DEFAULT_RESPAWN_PRIORITY = RespawnPriority.CHECKPOINT;
    private static final boolean DEFAULT_EMPTY_HAND_OR_SNEAK_REQUIRED = true;
    private static final boolean DEFAULT_DELETE_COMMAND_ALLOWED = true;

    public ConfigManager(@NotNull CampfireCheckpoints plugin) {
        this.plugin = plugin;
        this.soundOnSet = DEFAULT_SOUND;
        this.respawnPriority = DEFAULT_RESPAWN_PRIORITY;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();

        this.overworldEnableRegularCampfires = config.getBoolean("enable-overworld", DEFAULT_DIMENTION_OVERWORLD);
        this.netherEnableRegularCampfires = config.getBoolean("enable-nether", DEFAULT_DIMENTION_NETHER);
        this.endEnableRegularCampfires = config.getBoolean("enable-end", DEFAULT_DIMENTION_END);
        this.overworldEnableSoulCampfires = config.getBoolean("enable-soul-overworld", DEFAULT_DIMENTION_OVERWORLD_SOUL);
        this.netherEnableSoulCampfires = config.getBoolean("enable-soul-nether", DEFAULT_DIMENTION_NETHER_SOUL);
        this.endEnableSoulCampfires = config.getBoolean("enable-soul-end", DEFAULT_DIMENTION_END_SOUL);

        // Load radius
        this.radius = config.getInt("radius", DEFAULT_RADIUS);
        if (radius <= 0) {
            plugin.getLogger().warning("Invalid radius in config. Using default: " + DEFAULT_RADIUS);
            this.radius = DEFAULT_RADIUS;
        }

        this.soulRadius = config.getInt("soul-campfire-radius", DEFAULT_SOUL_RADIUS);
        if (radius <= 0) {
            plugin.getLogger().warning("Invalid soul campfire radius in config. Using default: " + DEFAULT_SOUL_RADIUS);
            this.soulRadius = DEFAULT_SOUL_RADIUS;
        }

        this.minDistance = config.getInt("min-distance", DEFAULT_MIN_DISTANCE);
        if (minDistance <= 0) {
            plugin.getLogger().warning("Invalid min distance in config. Using default: " + DEFAULT_MIN_DISTANCE);
            this.minDistance = DEFAULT_MIN_DISTANCE;
        }

        this.soulMinDistance = config.getInt("soul-campfire-min-distance", DEFAULT_SOUL_MIN_DISTANCE);
        if (minDistance <= 0) {
            plugin.getLogger().warning("Invalid min distance in config. Using default: " + DEFAULT_SOUL_MIN_DISTANCE);
            this.soulMinDistance = DEFAULT_SOUL_MIN_DISTANCE;
        }

        // Load extinguish-on-respawn
        this.extinguishOnRespawn = config.getBoolean("extinguish-on-respawn", DEFAULT_EXTINGUISH);

        // Load override confirmation timeout
        this.overrideConfirmationTimeout = config.getInt("override-confirmation-timeout", DEFAULT_OVERRIDE_TIMEOUT);
        if (overrideConfirmationTimeout < 0) {
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

        // Load empty-hand-or-sneak-required
        this.emptyHandOrSneakRequired = config.getBoolean("require-empty-hand-or-sneak",
                                                          DEFAULT_EMPTY_HAND_OR_SNEAK_REQUIRED);

        // Load delete-command-allowed
        this.deleteCommandAllowed = config.getBoolean("allow-delete-command", DEFAULT_DELETE_COMMAND_ALLOWED);

        plugin.getLogger().info("Configuration loaded " +
            "- Regular campfires enabled (overworld): " + overworldEnableRegularCampfires +
            ", Regular campfires enabled (nether): " + netherEnableRegularCampfires +
            ", Regular campfires enabled (end): " + endEnableRegularCampfires +
            ", Soul campfires enabled (overworld): " + overworldEnableSoulCampfires +
            ", Soul campfires enabled (nether): " + netherEnableSoulCampfires +
            ", Soul campfires enabled (end): " + endEnableSoulCampfires +
            ", Radius: " + radius +
            ", Radius (soul campfires): " + soulRadius +
            ", Min. distance: " + minDistance +
            ", Min. distance (soul campfires): " + soulMinDistance +
            ", Extinguish: " + extinguishOnRespawn +
            ", Timeout: " + overrideConfirmationTimeout + "s" +
            ", MaxCheckpoints: " + (maxCheckpointsPerPlayer == 0 ? "unlimited" : maxCheckpointsPerPlayer) +
            ", RespawnPriority: " + respawnPriority.getConfigValue() +
            ", Sound: " + soundOnSet.name());
    }

    public boolean isDimentionEnabledOverworld() {
        return overworldEnableRegularCampfires;
    }
    public boolean isDimentionEnabledOverworldSoul() {
        return overworldEnableSoulCampfires;
    }
    public boolean isDimentionEnabledNether() {
        return netherEnableRegularCampfires;
    }
    public boolean isDimentionEnabledNetherSoul() {
        return netherEnableSoulCampfires;
    }
    public boolean isDimentionEnabledEnd() {
        return endEnableRegularCampfires;
    }
    public boolean isDimentionEnabledEndSoul() {
        return endEnableSoulCampfires;
    }

    public int getRadius() {
        return radius;
    }

    public int getSoulRadius() {
        return soulRadius;
    }

    public int getMinDistance() {
        return minDistance;
    }

    public int getSoulMinDistance() {
        return soulMinDistance;
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

    public boolean isEmptyHandOrSneakRequired() {
        return emptyHandOrSneakRequired;
    }

    public boolean isDeleteCommandAllowed() {
        return deleteCommandAllowed;
    }
}