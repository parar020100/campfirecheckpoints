// src/main/java/com/campfirecheckpoints/listener/CheckpointListener.java
package com.campfirecheckpoints.listener;

import com.campfirecheckpoints.CampfireCheckpoints;
import com.campfirecheckpoints.manager.CheckpointManager;
import com.campfirecheckpoints.manager.ConfigManager;
import com.campfirecheckpoints.model.Checkpoint;
import com.campfirecheckpoints.model.RespawnPriority;
import com.campfirecheckpoints.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CheckpointListener implements Listener {

    private final @NotNull CampfireCheckpoints plugin;
    private final @NotNull Map<UUID, Location> deathLocations;

    public CheckpointListener(@NotNull CampfireCheckpoints plugin) {
        this.plugin = plugin;
        this.deathLocations = new ConcurrentHashMap<>();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Material type = clickedBlock.getType();
        if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE) {
            return;
        }

        ConfigManager configManager = plugin.getConfigManager();
        if (type == Material.CAMPFIRE && !configManager.RegularCampfiresEnabled()) {
            return;
        };
        if (type == Material.SOUL_CAMPFIRE && !configManager.SoulCampfiresEnabled()) {
            return;
        };

        Player player = event.getPlayer();

        if (!player.hasPermission("campfirecheckpoints.use")) {
            return;
        }

        Material itemInHand = player.getInventory().getItemInMainHand().getType();
        if (isInteractItem(itemInHand)) {
            return;
        }

        BlockData blockData = clickedBlock.getBlockData();
        if (!(blockData instanceof Lightable lightable)) {
            return;
        }
        if (!lightable.isLit()) {
            MessageUtil.send(player, "&cThis campfire is not lit! Light it first to set a checkpoint.");
            return;
        }

        UUID playerUUID = player.getUniqueId();
        Location blockLocation = clickedBlock.getLocation();

        CheckpointManager checkpointManager = plugin.getCheckpointManager();

        if (checkpointManager.hasCheckpointAt(playerUUID, blockLocation)) {
            MessageUtil.send(player, "&eYou already have a checkpoint at this campfire!");
            event.setCancelled(true);
            return;
        }

        if (checkpointManager.tryExecuteOverride(playerUUID, blockLocation)) {
            playCheckpointEffects(player, blockLocation);
            MessageUtil.send(player, "&aCheckpoint override confirmed! Previous checkpoint removed.");
            event.setCancelled(true);
            return;
        }

        int min_distance = configManager.getMinDistance();
        Checkpoint existingNearby = checkpointManager.findCheckpointWithinRadius(
            playerUUID, blockLocation, min_distance
        );

        if (existingNearby != null) {
            checkpointManager.setPendingOverride(playerUUID, existingNearby, blockLocation);
            Location existingLoc = existingNearby.getBlockLocation();
            String existingCoords = existingLoc != null ? 
                String.format("(%d, %d, %d)", existingLoc.getBlockX(), 
                    existingLoc.getBlockY(), existingLoc.getBlockZ()) : 
                "(unknown)";

            int timeout = configManager.getOverrideConfirmationTimeout();
            MessageUtil.send(player, "&cWarning: &eYou have a checkpoint at " + existingCoords + 
                " within " + min_distance + " blocks!");
            MessageUtil.send(player, "&eRight-click again within " + timeout + " seconds to override it.");
            event.setCancelled(true);
            return;
        }

        if (!checkpointManager.canCreateCheckpoint(playerUUID)) {
            int max = configManager.getMaxCheckpointsPerPlayer();
            MessageUtil.send(player, "&cYou have reached the maximum number of checkpoints (" + max + ")!");
            MessageUtil.send(player, "&7Use &e/cc delete <index> &7to remove an old checkpoint first.");
            event.setCancelled(true);
            return;
        }

        Checkpoint newCheckpoint = new Checkpoint(playerUUID, blockLocation);
        checkpointManager.addCheckpoint(playerUUID, newCheckpoint);
        playCheckpointEffects(player, blockLocation);
        MessageUtil.send(player, "&aCheckpoint set at &f(" + blockLocation.getBlockX() + ", " + 
            blockLocation.getBlockY() + ", " + blockLocation.getBlockZ() + ")&a!");

        event.setCancelled(true);
    }

    private boolean isInteractItem(@NotNull Material material) {
        return switch (material) {
            case WATER_BUCKET,
                 FLINT_AND_STEEL,
                 FIRE_CHARGE,
                 WOODEN_SHOVEL,
                 STONE_SHOVEL,
                 IRON_SHOVEL,
                 GOLDEN_SHOVEL,
                 DIAMOND_SHOVEL,
                 NETHERITE_SHOVEL -> true;
            default -> false;
        };
    }

    private void playCheckpointEffects(@NotNull Player player, @NotNull Location location) {
        ConfigManager configManager = plugin.getConfigManager();
        World world = location.getWorld();

        if (world == null) {
            return;
        }

        Sound sound = configManager.getSoundOnSet();
        player.playSound(location, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);

        // Spawn particles around the campfire
        Location particleLoc = location.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(
            Particle.HAPPY_VILLAGER,
            particleLoc,
            30,      // count
            0.5,     // offsetX
            0.5,     // offsetY
            0.5,     // offsetZ
            0.1      // speed
        );

        world.spawnParticle(
            Particle.FLAME,
            particleLoc.clone().add(0, 0.5, 0),
            15,
            0.3,
            0.2,
            0.3,
            0.02
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLoc = player.getLocation();

        if (deathLoc.getWorld() != null) {
            deathLocations.put(player.getUniqueId(), deathLoc.clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        Location deathLocation = deathLocations.remove(playerUUID);
        if (deathLocation == null) {
            return;
        }

        CheckpointManager checkpointManager = plugin.getCheckpointManager();
        ConfigManager configManager = plugin.getConfigManager();

        Checkpoint closestCheckpoint = checkpointManager.findClosestCheckpoint(playerUUID, deathLocation);

        Location bedSpawn = player.getRespawnLocation();
        boolean hasBedSpawn = bedSpawn != null && event.isBedSpawn();

        RespawnResult respawnResult = determineRespawnLocation(
            closestCheckpoint, 
            bedSpawn, 
            hasBedSpawn, 
            deathLocation, 
            configManager.getRespawnPriority(),
            configManager.getRadius()
        );

        if (respawnResult == null || respawnResult.location == null) {
            // No valid respawn location, let vanilla handle it
            return;
        }

        event.setRespawnLocation(respawnResult.location);

        if (respawnResult.isCheckpoint && respawnResult.checkpoint != null) {
            handleCheckpointRespawn(respawnResult.checkpoint, checkpointManager, configManager);
        }

        final boolean usedCheckpoint = respawnResult.isCheckpoint;
        final boolean usedBed = respawnResult.isBed;
        final boolean extinguished = respawnResult.isCheckpoint && configManager.isExtinguishOnRespawn();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                if (usedCheckpoint) {
                    MessageUtil.send(player, "&aYou respawned at your campfire checkpoint!");
                    if (extinguished) {
                        MessageUtil.send(player, "&7The campfire has been extinguished. Use Flint & Steel to relight it.");
                    }
                } else if (usedBed) {
                }
            }
        }, 5L);
    }

    /**
     * Determines the respawn location based on priority settings
     */
    private @Nullable RespawnResult determineRespawnLocation(
            @Nullable Checkpoint checkpoint,
            @Nullable Location bedSpawn,
            boolean hasBedSpawn,
            @NotNull Location deathLocation,
            @NotNull RespawnPriority priority,
            double radius) {

        // Get checkpoint spawn location if available
        Location checkpointSpawn = null;
        boolean hasValidCheckpoint = false;

        if (checkpoint != null) {
            checkpointSpawn = checkpoint.getSpawnLocation();
            hasValidCheckpoint = checkpointSpawn != null && checkpointSpawn.getWorld() != null;
        }

        // If neither is available, return null
        if (!hasValidCheckpoint && !hasBedSpawn) {
            return null;
        }

        // If only checkpoint is available
        if (hasValidCheckpoint && !hasBedSpawn) {
            return new RespawnResult(checkpointSpawn, true, false, checkpoint);
        }

        // If only bed is available
        if (!hasValidCheckpoint && hasBedSpawn) {
            return new RespawnResult(bedSpawn, false, true, null);
        }

        // Both are available - use priority setting
        switch (priority) {
            case CHECKPOINT:
                return new RespawnResult(checkpointSpawn, true, false, checkpoint);

            case BED:
                return new RespawnResult(bedSpawn, false, true, null);

            case CLOSEST:
                return determineClosestRespawn(
                    checkpoint, checkpointSpawn, 
                    bedSpawn, 
                    deathLocation, 
                    radius
                );

            default:
                return new RespawnResult(checkpointSpawn, true, false, checkpoint);
        }
    }

    /**
     * Determines which respawn point is closer to death location
     */
    private @NotNull RespawnResult determineClosestRespawn(
            @NotNull Checkpoint checkpoint,
            @NotNull Location checkpointSpawn,
            @NotNull Location bedSpawn,
            @NotNull Location deathLocation,
            double radius) {

        double checkpointDistSq = checkpoint.distanceSquared(deathLocation);
        double bedDistSq = calculateDistanceSquared(bedSpawn, deathLocation);

        // Check if bed is in a different world
        if (bedSpawn.getWorld() == null || !bedSpawn.getWorld().equals(deathLocation.getWorld())) {
            // Bed is in different world, use checkpoint
            return new RespawnResult(checkpointSpawn, true, false, checkpoint);
        }

        // Check if bed is within radius
        double radiusSquared = radius * radius;
        boolean bedWithinRadius = bedDistSq <= radiusSquared;

        // If bed is not within radius, use checkpoint
        if (!bedWithinRadius) {
            return new RespawnResult(checkpointSpawn, true, false, checkpoint);
        }

        // Both are within radius, use the closer one
        if (checkpointDistSq <= bedDistSq) {
            return new RespawnResult(checkpointSpawn, true, false, checkpoint);
        } else {
            return new RespawnResult(bedSpawn, false, true, null);
        }
    }

    private double calculateDistanceSquared(@NotNull Location a, @NotNull Location b) {
        if (a.getWorld() == null || b.getWorld() == null) {
            return Double.MAX_VALUE;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }

        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void handleCheckpointRespawn(
            @NotNull Checkpoint checkpoint,
            @NotNull CheckpointManager checkpointManager,
            @NotNull ConfigManager configManager) {

        if (configManager.isExtinguishOnRespawn()) {
            Location blockLoc = checkpoint.getBlockLocation();
            if (blockLoc != null && blockLoc.getWorld() != null) {
                Block campfireBlock = blockLoc.getBlock();
                BlockData blockData = campfireBlock.getBlockData();

                if (blockData instanceof Lightable lightable) {
                    lightable.setLit(false);
                    campfireBlock.setBlockData(lightable);
                    checkpointManager.setCheckpointLit(checkpoint, false);
                }
            }
        }
    }

    private static final class RespawnResult {
        final @Nullable Location location;
        final boolean isCheckpoint;
        final boolean isBed;
        final @Nullable Checkpoint checkpoint;

        RespawnResult(@Nullable Location location, boolean isCheckpoint, boolean isBed, 
                      @Nullable Checkpoint checkpoint) {
            this.location = location;
            this.isCheckpoint = isCheckpoint;
            this.isBed = isBed;
            this.checkpoint = checkpoint;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        deathLocations.remove(playerUUID);
        plugin.getCheckpointManager().clearPendingOverride(playerUUID);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE) {
            return;
        }

        Location blockLocation = block.getLocation();
        CheckpointManager checkpointManager = plugin.getCheckpointManager();

        Checkpoint checkpoint = checkpointManager.removeCheckpointAt(blockLocation);

        if (checkpoint != null) {
            Player owner = Bukkit.getPlayer(checkpoint.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                MessageUtil.send(owner, "&cYour campfire checkpoint at &f(" + 
                    blockLocation.getBlockX() + ", " + 
                    blockLocation.getBlockY() + ", " + 
                    blockLocation.getBlockZ() + ") &chas been destroyed!");
            }

            Player breaker = event.getPlayer();
            if (!breaker.getUniqueId().equals(checkpoint.getOwnerUUID())) {
                MessageUtil.send(breaker, "&7You destroyed a checkpoint belonging to another player.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCampfireExtinguish(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Material type = clickedBlock.getType();
        if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE) {
            return;
        }

        Player player = event.getPlayer();
        Material itemInHand = player.getInventory().getItemInMainHand().getType();

        if (!isShovel(itemInHand) && itemInHand != Material.WATER_BUCKET) {
            return;
        }

        BlockData blockData = clickedBlock.getBlockData();
        if (!(blockData instanceof Lightable lightable) || !lightable.isLit()) {
            return;
        }

        Location blockLoc = clickedBlock.getLocation();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block block = blockLoc.getBlock();
            BlockData newBlockData = block.getBlockData();

            if (newBlockData instanceof Lightable newLightable && !newLightable.isLit()) {
                Checkpoint checkpoint = plugin.getCheckpointManager().getCheckpointAt(blockLoc);
                if (checkpoint != null && checkpoint.isLit()) {
                    checkpoint.setLit(false);
                    plugin.getCheckpointManager().setCheckpointLit(checkpoint, false);

                    Player owner = Bukkit.getPlayer(checkpoint.getOwnerUUID());
                    if (owner != null && owner.isOnline()) {
                        MessageUtil.send(owner, "&eYour campfire checkpoint at &f(" + 
                            blockLoc.getBlockX() + ", " + 
                            blockLoc.getBlockY() + ", " + 
                            blockLoc.getBlockZ() + ") &ehas been extinguished!");
                    }
                }
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCampfireLight(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Material type = clickedBlock.getType();
        if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE) {
            return;
        }

        ConfigManager configManager = plugin.getConfigManager();
        if (type == Material.CAMPFIRE && !configManager.RegularCampfiresEnabled()) {
            return;
        };
        if (type == Material.SOUL_CAMPFIRE && !configManager.SoulCampfiresEnabled()) {
            return;
        };

        Player player = event.getPlayer();
        Material itemInHand = player.getInventory().getItemInMainHand().getType();

        if (itemInHand != Material.FLINT_AND_STEEL && itemInHand != Material.FIRE_CHARGE) {
            return;
        }

        BlockData blockData = clickedBlock.getBlockData();
        if (!(blockData instanceof Lightable lightable) || lightable.isLit()) {
            return;
        }

        Location blockLoc = clickedBlock.getLocation();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block block = blockLoc.getBlock();
            BlockData newBlockData = block.getBlockData();

            if (newBlockData instanceof Lightable newLightable && newLightable.isLit()) {
                Checkpoint checkpoint = plugin.getCheckpointManager().getCheckpointAt(blockLoc);
                if (checkpoint != null && !checkpoint.isLit()) {
                    checkpoint.setLit(true);
                    plugin.getCheckpointManager().setCheckpointLit(checkpoint, true);

                    Player owner = Bukkit.getPlayer(checkpoint.getOwnerUUID());
                    if (owner != null && owner.isOnline()) {
                        MessageUtil.send(owner, "&aYour campfire checkpoint at &f(" + 
                            blockLoc.getBlockX() + ", " + 
                            blockLoc.getBlockY() + ", " + 
                            blockLoc.getBlockZ() + ") &ahas been relit!");
                    }
                }
            }
        }, 1L);
    }

    private boolean isShovel(@NotNull Material material) {
        return switch (material) {
            case WOODEN_SHOVEL,
                 STONE_SHOVEL,
                 IRON_SHOVEL,
                 GOLDEN_SHOVEL,
                 DIAMOND_SHOVEL,
                 NETHERITE_SHOVEL -> true;
            default -> false;
        };
    }
}