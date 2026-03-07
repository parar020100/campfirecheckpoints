// src/main/java/com/campfirecheckpoints/command/CheckpointCommand.java
package com.campfirecheckpoints.command;

import com.campfirecheckpoints.CampfireCheckpoints;
import com.campfirecheckpoints.manager.CheckpointManager;
import com.campfirecheckpoints.manager.ConfigManager;
import com.campfirecheckpoints.model.Checkpoint;
import com.campfirecheckpoints.model.RespawnPriority;
import com.campfirecheckpoints.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class CheckpointCommand implements CommandExecutor, TabCompleter {

    private final @NotNull CampfireCheckpoints plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public CheckpointCommand(@NotNull CampfireCheckpoints plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                             @NotNull String label, @NotNull String[] args) {
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> handleList(player);
            case "delete" -> handleDelete(player, args);
            case "reload" -> handleReload(player);
            case "info" -> handleInfo(player);
            default -> showHelp(player);
        }

        return true;
    }

    private void showHelp(@NotNull Player player) {
        MessageUtil.send(player, "&6&l=== Campfire Checkpoints ===");
        MessageUtil.send(player, "&e/cc list &7- List your checkpoints");
        MessageUtil.send(player, "&e/cc delete <index> &7- Delete a checkpoint");
        MessageUtil.send(player, "&e/cc info &7- Show plugin info");
        if (player.hasPermission("campfirecheckpoints.reload")) {
            MessageUtil.send(player, "&e/cc reload &7- Reload configuration");
        }
        MessageUtil.send(player, "");
        MessageUtil.send(player, "&7Right-click a lit campfire to set a checkpoint!");
    }

    private void handleList(@NotNull Player player) {
        if (!player.hasPermission("campfirecheckpoints.use")) {
            MessageUtil.send(player, "&cYou don't have permission to use checkpoints.");
            return;
        }

        CheckpointManager manager = plugin.getCheckpointManager();
        List<Checkpoint> checkpoints = manager.getPlayerCheckpoints(player.getUniqueId());

        if (checkpoints.isEmpty()) {
            MessageUtil.send(player, "&eYou have no checkpoints set.");
            MessageUtil.send(player, "&7Right-click a lit campfire to set one!");
            return;
        }

        ConfigManager configManager = plugin.getConfigManager();
        int max = configManager.getMaxCheckpointsPerPlayer();
        String limitDisplay = max > 0 ? " &7(" + checkpoints.size() + "/" + max + ")" : "";

        MessageUtil.send(player, "&6&l=== Your Checkpoints ===" + limitDisplay);
        
        for (int i = 0; i < checkpoints.size(); i++) {
            Checkpoint cp = checkpoints.get(i);
            Location loc = cp.getBlockLocation();
            
            String status = cp.isLit() ? "&a✓ Lit" : "&c✗ Extinguished";
            String coords = loc != null ? 
                String.format("&f%d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()) :
                "&cUnknown";
            String world = cp.getWorldName();
            String date = dateFormat.format(new Date(cp.getCreatedAt()));

            MessageUtil.send(player, "&e[" + i + "] " + status + " &7| " + coords + 
                " &7(" + world + ")");
            MessageUtil.send(player, "    &7Created: " + date);
        }

        MessageUtil.send(player, "&7Use &e/cc delete <index> &7to remove a checkpoint.");
    }

    private void handleDelete(@NotNull Player player, @NotNull String[] args) {
        if (!player.hasPermission("campfirecheckpoints.use")) {
            MessageUtil.send(player, "&cYou don't have permission to use checkpoints.");
            return;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "&cUsage: /cc delete <index>");
            MessageUtil.send(player, "&7Use /cc list to see checkpoint indices.");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.send(player, "&cInvalid index. Please provide a number.");
            return;
        }

        CheckpointManager manager = plugin.getCheckpointManager();
        List<Checkpoint> checkpoints = manager.getPlayerCheckpoints(player.getUniqueId());

        if (index < 0 || index >= checkpoints.size()) {
            if (checkpoints.isEmpty()) {
                MessageUtil.send(player, "&cYou have no checkpoints to delete.");
            } else {
                MessageUtil.send(player, "&cInvalid index. You have " + checkpoints.size() + 
                    " checkpoint(s) (indices 0-" + (checkpoints.size() - 1) + ").");
            }
            return;
        }

        Checkpoint toDelete = checkpoints.get(index);
        Location loc = toDelete.getBlockLocation();
        String coords = loc != null ?
            String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()) :
            "(unknown)";

        if (manager.removeCheckpoint(player.getUniqueId(), index)) {
            MessageUtil.send(player, "&aCheckpoint at " + coords + " has been deleted.");
        } else {
            MessageUtil.send(player, "&cFailed to delete checkpoint.");
        }
    }

    private void handleReload(@NotNull Player player) {
        if (!player.hasPermission("campfirecheckpoints.reload")) {
            MessageUtil.send(player, "&cYou don't have permission to reload the config.");
            return;
        }

        plugin.reloadPluginConfig();
        MessageUtil.send(player, "&aConfiguration reloaded successfully!");
    }

    private void handleInfo(@NotNull Player player) {
        CheckpointManager manager = plugin.getCheckpointManager();
        ConfigManager configManager = plugin.getConfigManager();
        
        int playerCount = manager.getPlayerCheckpoints(player.getUniqueId()).size();
        int totalCount = manager.getTotalCheckpointCount();
        int max = configManager.getMaxCheckpointsPerPlayer();
        RespawnPriority priority = configManager.getRespawnPriority();

        MessageUtil.send(player, "&6&l=== Campfire Checkpoints Info ===");
        MessageUtil.send(player, "&eVersion: &f" + plugin.getDescription().getVersion());
        MessageUtil.send(player, "&eYour Checkpoints: &f" + playerCount + 
            (max > 0 ? " / " + max : ""));
        MessageUtil.send(player, "&eTotal Checkpoints: &f" + totalCount);
        MessageUtil.send(player, "&eRadius: &f" + configManager.getRadius() + " blocks");
        MessageUtil.send(player, "&eExtinguish on Respawn: &f" + configManager.isExtinguishOnRespawn());
        MessageUtil.send(player, "&eConfirmation Timeout: &f" + 
            configManager.getOverrideConfirmationTimeout() + "s");
        MessageUtil.send(player, "&eRespawn Priority: &f" + priority.getConfigValue());
        MessageUtil.send(player, "  &7(" + priority.getDescription() + ")");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, 
                                                 @NotNull Command command, 
                                                 @NotNull String alias, 
                                                 @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return null;
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("list", "delete", "info"));
            if (player.hasPermission("campfirecheckpoints.reload")) {
                completions.add("reload");
            }
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            int checkpointCount = plugin.getCheckpointManager()
                .getPlayerCheckpoints(player.getUniqueId()).size();
            
            return IntStream.range(0, checkpointCount)
                .mapToObj(String::valueOf)
                .filter(s -> s.startsWith(args[1]))
                .collect(Collectors.toList());
        }

        return List.of();
    }
}