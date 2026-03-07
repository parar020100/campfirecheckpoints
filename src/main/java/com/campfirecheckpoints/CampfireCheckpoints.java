// src/main/java/com/campfirecheckpoints/CampfireCheckpoints.java
package com.campfirecheckpoints;

import com.campfirecheckpoints.command.CheckpointCommand;
import com.campfirecheckpoints.listener.CheckpointListener;
import com.campfirecheckpoints.manager.CheckpointManager;
import com.campfirecheckpoints.manager.ConfigManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;

public final class CampfireCheckpoints extends JavaPlugin {

    private static @Nullable CampfireCheckpoints instance;
    private @Nullable CheckpointManager checkpointManager;
    private @Nullable ConfigManager configManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.configManager = new ConfigManager(this);

        this.checkpointManager = new CheckpointManager(this);
        this.checkpointManager.loadCheckpoints();

        getServer().getPluginManager().registerEvents(
            new CheckpointListener(this), 
            this
        );

        registerCommands();

        getLogger().info("CampfireCheckpoints has been enabled!");
    }

    @Override
    public void onDisable() {
        if (checkpointManager != null) {
            checkpointManager.shutdown();
            getLogger().info("Checkpoints saved successfully.");
        }

        instance = null;
        getLogger().info("CampfireCheckpoints has been disabled!");
    }

    private void registerCommands() {
        PluginCommand ccCommand = getCommand("cc");
        if (ccCommand != null) {
            CheckpointCommand commandExecutor = new CheckpointCommand(this);
            ccCommand.setExecutor(commandExecutor);
            ccCommand.setTabCompleter(commandExecutor);
        } else {
            getLogger().log(Level.SEVERE, "Failed to register /cc command!");
        }
    }

    public static @Nullable CampfireCheckpoints getInstance() {
        return instance;
    }

    public @NotNull CheckpointManager getCheckpointManager() {
        if (checkpointManager == null) {
            throw new IllegalStateException("CheckpointManager not initialized!");
        }
        return checkpointManager;
    }

    public @NotNull ConfigManager getConfigManager() {
        if (configManager == null) {
            throw new IllegalStateException("ConfigManager not initialized!");
        }
        return configManager;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        if (configManager != null) {
            configManager.reload();
        }
    }
}