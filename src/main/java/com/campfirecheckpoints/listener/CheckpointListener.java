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
import org.bukkit.block.data.type.RespawnAnchor;
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

        // If player interacts with a bed, clear their respawn-anchor checkpoint(s),
        // but do not cancel the bed interaction.
        if (clickedBlock.getBlockData() instanceof org.bukkit.block.data.type.Bed) {
            plugin.getCheckpointManager().removeAnchorCheckpoints(event.getPlayer().getUniqueId(), true);
            return;
        }

        Material type = clickedBlock.getType();
        boolean isRegularCampfire = (type == Material.CAMPFIRE);
        boolean isSoulCampfire = (type == Material.SOUL_CAMPFIRE);
        boolean isRespawnAnchor = (type == Material.RESPAWN_ANCHOR);

        ConfigManager configManager = plugin.getConfigManager();
        World.Environment env = event.getPlayer().getWorld().getEnvironment();
        Player player = event.getPlayer();

        // Deny respawn anchor spawnpoint in Nether unless enabled, but allow charging with glowstone
        if (isRespawnAnchor && configManager.RespawnAnchorsEnabled()) {
            Material itemInHand = player.getInventory().getItemInMainHand().getType();

            if (itemInHand == Material.GLOWSTONE) {
                return;
            }

            if (env == World.Environment.NORMAL && !configManager.isDimentionEnabledOverworldAnchor()) {
                MessageUtil.send(player, "&cSetting respawn anchors as checkpoints is not allowed in the Overworld.");
                //event.setCancelled(true);
                return;
            }

            if (env == World.Environment.NETHER && !configManager.isDimentionEnabledNetherAnchor()) {
                MessageUtil.send(player, "&cSetting respawn anchors as checkpoints is not allowed in the Nether.");
                event.setCancelled(true);
                return;
            }

            if (env == World.Environment.THE_END && !configManager.isDimentionEnabledEndAnchor()) {
                MessageUtil.send(player, "&cSetting respawn anchors as checkpoints is not allowed in the End.");
                //event.setCancelled(true);
                return;
            }

            // Create checkpoint on respawn anchor
            UUID playerUUID = player.getUniqueId();
            Location blockLocation = clickedBlock.getLocation();
            CheckpointManager checkpointManager = plugin.getCheckpointManager();

            checkpointManager.validateAllCheckpoints(playerUUID);

            if (checkpointManager.hasCheckpointAt(playerUUID, blockLocation)) {
                MessageUtil.send(player, "&eYou already have a checkpoint at this respawn anchor!");
                event.setCancelled(true);
                return;
            }

            // Check for charge state and prevent setting checkpoint on uncharged anchor
            BlockData blockData = clickedBlock.getBlockData();
            if (blockData instanceof RespawnAnchor anchorData) {
                if (anchorData.getCharges() <= 0) {
                    MessageUtil.send(player, "&cThis respawn anchor is uncharged!" + 
                                     " Charge it with glowstone to set a checkpoint.");
                    event.setCancelled(true);
                    return;
                }
            }

            // Remove all other anchor checkpoints, there can be only one per player
            checkpointManager.removeAnchorCheckpoints(playerUUID, false);

            Checkpoint newCheckpoint = new Checkpoint(playerUUID, blockLocation);
            checkpointManager.addCheckpoint(playerUUID, newCheckpoint);

            String worldName = blockLocation.getWorld() != null ?
                                    blockLocation.getWorld().getName().replace("world_", "") : "unknown";

            MessageUtil.send(player, "&aCheckpoint set at respawn anchor &f("
                             + blockLocation.getBlockX() + ", "
                             + blockLocation.getBlockY() + ", "
                             + blockLocation.getBlockZ() + ", "
                             + worldName + ")&a!");

            player.playSound(blockLocation, Sound.valueOf("BLOCK_RESPAWN_ANCHOR_SET_SPAWN"), SoundCategory.BLOCKS, 1.0f, 1.0f);

            event.setCancelled(true);

        }

        if (!isRegularCampfire && !isSoulCampfire) {
            return;
        }


        switch (env) {
            case NORMAL:
                if (isRegularCampfire && !configManager.isDimentionEnabledOverworld()) return;
                if (isSoulCampfire && !configManager.isDimentionEnabledOverworldSoul()) return;
                break;
            case NETHER:
                if (isRegularCampfire && !configManager.isDimentionEnabledNether()) return;
                if (isSoulCampfire && !configManager.isDimentionEnabledNetherSoul()) return;
                break;
            case THE_END:
                if (isRegularCampfire && !configManager.isDimentionEnabledEnd()) return;
                if (isSoulCampfire && !configManager.isDimentionEnabledEndSoul()) return;
                break;
        }



        Material itemInHand = player.getInventory().getItemInMainHand().getType();

        // Only allow checkpoint creation if player is sneaking (crouching) or has an empty hand
        // This is required to fix food cooking on campfire and extinguishing it with a splash water bottle
        if (configManager.isEmptyHandOrSneakRequired()) {
            if (!player.isSneaking() && itemInHand != Material.AIR) {
                return;
            }
        } else if (isInteractItem(itemInHand)) {
            return;
        }

        if (!player.hasPermission("campfirecheckpoints.use")) {
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

        checkpointManager.validateAllCheckpoints(playerUUID);

        if (checkpointManager.hasCheckpointAt(playerUUID, blockLocation)) {
            MessageUtil.send(player, "&eYou already have a checkpoint at this campfire!");
            event.setCancelled(true);
            return;
        }

        if (checkpointManager.tryExecuteOverride(playerUUID, blockLocation)) {
            playCheckpointEffects(player, blockLocation);
            MessageUtil.send(player, "&aCheckpoint override confirmed!");
            event.setCancelled(true);
            return;
        }

        int min_distance = configManager.getMinDistance();

        if (type == Material.SOUL_CAMPFIRE) {
            min_distance = configManager.getSoulMinDistance();
        };

        Checkpoint existingNearby = checkpointManager.findCheckpointWithinRadius(
            playerUUID, blockLocation, min_distance
        );

        if (existingNearby != null) {
            int timeout = configManager.getOverrideConfirmationTimeout();

            if (timeout > 0) {
                checkpointManager.setPendingOverride(playerUUID, blockLocation, min_distance);
            }

            Location existingLoc = existingNearby.getBlockLocation();
            String existingCoords = existingLoc != null ? 
                String.format("(%d, %d, %d)", existingLoc.getBlockX(), 
                    existingLoc.getBlockY(), existingLoc.getBlockZ()) : 
                "(unknown)";

            if (timeout > 0) {
                MessageUtil.send(player, "&cWarning: &eYou have a checkpoint at " + existingCoords + 
                " within " + min_distance + " blocks!");
                MessageUtil.send(player, "&eRight-click again within " + timeout + " seconds to override it.");
            } else {
                MessageUtil.send(player, "&cYou already have a checkpoint nearby within " + min_distance + " blocks!");
            }
            event.setCancelled(true);
            return;
        }

        if (!checkpointManager.canCreateCheckpoint(playerUUID)) {
            int max = configManager.getMaxCheckpointsPerPlayer();
            MessageUtil.send(player, "&cYou have reached the maximum number of checkpoints (" + max + ")!");

            if (configManager.isDeleteCommandAllowed()) {
                MessageUtil.send(player, "&7Use &e/cc delete <index> &7to remove an old checkpoint first.");
            } else {
                MessageUtil.send(player, "&7You need to break an old checkpoint before creating a new one.");
            }
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

        checkpointManager.validateAllCheckpoints(playerUUID);

        Checkpoint closestCheckpoint = checkpointManager.findClosestCheckpoint(playerUUID, deathLocation);
        final RespawnOption spawnAtCheckpoint = new RespawnOption(closestCheckpoint, configManager);

        final RespawnOption spawnAtBed = new RespawnOption(player.getRespawnLocation(),
                                                           null, false, true, event.isBedSpawn(), configManager);

        // Find anchor checkpoint for this player, if any
        Checkpoint anchorCheckpoint = checkpointManager.findAnchorCheckpoint(playerUUID);
        final RespawnOption spawnAtAnchor = new RespawnOption(anchorCheckpoint, configManager);

        // Select best option
        RespawnOption respawnResult = determineRespawnLocation(
            spawnAtCheckpoint, spawnAtAnchor, spawnAtBed, deathLocation,
            configManager.getRespawnPriority()
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
        final boolean usedAnchor = respawnResult.isAnchor;
        final boolean extinguished = respawnResult.isCheckpoint && configManager.isExtinguishOnRespawn();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                if (usedCheckpoint) {
                    if (usedAnchor) {
                        MessageUtil.send(player, "&aYou respawned at your respawn anchor!");
                    } else {
                        MessageUtil.send(player, "&aYou respawned at your campfire checkpoint!");
                        if (extinguished) {
                            MessageUtil.send(player, "&7The campfire has been extinguished." +
                                             " Use Flint & Steel to relight it.");
                        }
                    }
                } else if (usedBed) {
                }
            }
        }, 5L);
    }

    /**
     * Determines the respawn location based on priority settings
     */
    private @Nullable RespawnOption determineRespawnLocation(
            final @NotNull RespawnOption checkpointSpawn,
            final @NotNull RespawnOption anchorSpawn,
            final @NotNull RespawnOption bedSpawn,
            @NotNull Location deathLocation,
            @NotNull RespawnPriority priority) {

        boolean hasBedSpawn = bedSpawn.isValidWithinRadius(deathLocation);
        boolean hasAnchorSpawn = anchorSpawn.isValidWithinRadius(deathLocation);
        boolean hasValidCheckpoint = checkpointSpawn.isValidWithinRadius(deathLocation);

        boolean hasBedOrAnchorSpawn = hasBedSpawn || hasAnchorSpawn;
        RespawnOption bedOrAnchorSpawn = hasAnchorSpawn ? anchorSpawn : bedSpawn;

        if (!hasBedOrAnchorSpawn) {
            return hasValidCheckpoint ? checkpointSpawn : null;
        }

        if (!hasValidCheckpoint) {
            return bedOrAnchorSpawn;
        }

        // several options available - use priority setting
        switch (priority) {
            case CHECKPOINT:
                return checkpointSpawn;

            case BED:
                return bedOrAnchorSpawn;

            case CLOSEST:
                double checkpointDistSq = checkpointSpawn.distanceSquared(deathLocation);
                double bedOrAnchorDistSq = bedOrAnchorSpawn.distanceSquared(deathLocation);

                return bedOrAnchorDistSq < checkpointDistSq ? bedOrAnchorSpawn : checkpointSpawn;

            default:
                return checkpointSpawn;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnchorChargeOrDischarge(org.bukkit.event.block.BlockPhysicsEvent event) {
        Block block = event.getBlock();

        if (block.getType() != Material.RESPAWN_ANCHOR) {
            return;
        }

        Location blockLoc = block.getLocation();
        Checkpoint checkpoint = plugin.getCheckpointManager().getCheckpointAt(blockLoc);
        if (checkpoint == null) return;

        BlockData data = block.getBlockData();
        int charge = -1;
        if (data instanceof org.bukkit.block.data.type.RespawnAnchor anchorData) {
            charge = anchorData.getCharges();
        }

        boolean wasLit = checkpoint.isLit();
        boolean isNowLit = charge > 0;

        if (wasLit != isNowLit) {
            checkpoint.setLit(isNowLit);
            plugin.getCheckpointManager().setCheckpointLit(checkpoint, isNowLit);
            Player owner = Bukkit.getPlayer(checkpoint.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                if (isNowLit) {
                    MessageUtil.send(owner, "&aYour respawn anchor at &f(" +
                            blockLoc.getBlockX() + ", " +
                            blockLoc.getBlockY() + ", " +
                            blockLoc.getBlockZ() + ") &ahas been charged!");
                } else {
                    MessageUtil.send(owner, "&eYour respawn anchor at &f(" +
                            blockLoc.getBlockX() + ", " +
                            blockLoc.getBlockY() + ", " +
                            blockLoc.getBlockZ() + ") &ehas no charge left!");
                }
            }
        }
    }

    private void handleCheckpointRespawn(
            @NotNull Checkpoint checkpoint,
            @NotNull CheckpointManager checkpointManager,
            @NotNull ConfigManager configManager) {

        boolean anchor = checkpoint.isAnchor();

        if (configManager.isExtinguishOnRespawn() || anchor) {
            Location blockLoc = checkpoint.getBlockLocation();
            if (blockLoc != null && blockLoc.getWorld() != null) {
                Block campfireBlock = blockLoc.getBlock();
                BlockData blockData = campfireBlock.getBlockData();

                if (blockData instanceof Lightable lightable) {
                    lightable.setLit(false);
                    campfireBlock.setBlockData(lightable);
                    checkpointManager.setCheckpointLit(checkpoint, false);

                } else if (anchor && blockData instanceof RespawnAnchor anchorData) {
                    int charges = anchorData.getCharges() - 1;
                    anchorData.setCharges(charges);
                    campfireBlock.setBlockData(anchorData);

                    if (blockLoc.getWorld() != null) {
                        blockLoc.getWorld().playSound(blockLoc, Sound.valueOf("BLOCK_RESPAWN_ANCHOR_DEPLETE"),
                                                      SoundCategory.BLOCKS, 1.0f, 1.0f);
                    }

                    if (charges <= 0) {
                        checkpoint.setLit(false);
                        checkpointManager.setCheckpointLit(checkpoint, false);
                    }
                }
            }
        }
    }

    private static final class RespawnOption {
        final @Nullable Location location;
        final @Nullable Checkpoint checkpoint;
        final boolean isCheckpoint;
        final boolean isBed;
        final boolean isAnchor;
        final boolean valid;
        final double radiusSq;
        private final ConfigManager configManager;

        RespawnOption(@Nullable Location location, @Nullable Checkpoint checkpoint,
                      boolean isCheckpoint, boolean isBed, boolean valid, @NotNull ConfigManager configManager) {
            this.location = location;
            this.checkpoint = checkpoint;
            this.isCheckpoint = isCheckpoint;
            this.isBed = isBed;
            this.valid = valid;
            this.configManager = configManager;

            this.isAnchor = isCheckpoint && checkpoint != null && checkpoint.isAnchor();
            this.radiusSq = calcRadiusSq();
        }

        RespawnOption(@Nullable Checkpoint checkpoint, @NotNull ConfigManager configManager) {
            this.configManager = configManager;
            if (checkpoint == null || !checkpoint.isLit()) {
                this.location = null;
                this.checkpoint = checkpoint;
                this.isCheckpoint = true;
                this.isBed = false;
                this.valid = false;
                this.isAnchor = false;
                this.radiusSq = 0;
                return;
            }

            this.location = checkpoint.getSpawnLocation();
            this.checkpoint = checkpoint;
            this.isCheckpoint = true;
            this.isBed = false;
            this.valid = location != null && location.getWorld() != null;

            this.isAnchor = checkpoint.isAnchor();
            this.radiusSq = calcRadiusSq();
        }

        private double calcRadiusSq() {
            if (isBed || isAnchor) {
                return Double.MAX_VALUE;
            }
            if (!isCheckpoint) {
                // neither a bed, an anchor nor a checkpoint, return 0 radius just in case
                return 0;
            }

            double radius;
            if (checkpoint != null && checkpoint.isSoul()) {
                radius = configManager.getSoulRadius();
            } else {
                radius = configManager.getRadius();
            }
            return radius * radius;
        }

        public boolean isValid() {
            if (!valid) {
                return false;
            }
            if (location == null || location.getWorld() == null) {
                return false;
            }
            if (isCheckpoint && checkpoint == null) {
                return false;
            }
            return true;
        }

        public boolean isValidWithinRadius(@NotNull Location other) {
            if (!this.isValid()) {
                return false;
            }
            if (radiusSq == Double.MAX_VALUE) {
                // if it is an anchor or a bed, radiusSq is Double.MAX_VALUE
                return true;
            }
            // if the dimensions differ, distanceSquared returns Double.MAX_VALUE
            return distanceSquared(other) <= radiusSq;
        }

        public double distanceSquared(@NotNull Location other) {
            if (!this.isValid() || other.getWorld() == null) {
                return Double.MAX_VALUE;
            }
            if (!location.getWorld().equals(other.getWorld())) {
                return Double.MAX_VALUE;
            }

            double dx = location.getX() - other.getX();
            double dy = location.getY() - other.getY();
            double dz = location.getZ() - other.getZ();
            return dx * dx + dy * dy + dz * dz;
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

        // Handle campfire and respawn anchor checkpoints
        if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE && type != Material.RESPAWN_ANCHOR) {
            return;
        }

        Location blockLocation = block.getLocation();
        CheckpointManager checkpointManager = plugin.getCheckpointManager();

        Checkpoint checkpoint = checkpointManager.removeCheckpointAt(blockLocation);

        if (checkpoint != null) {
            Player owner = Bukkit.getPlayer(checkpoint.getOwnerUUID());
            String checkpointType = checkpoint.isAnchor() ? "respawn anchor" : "campfire checkpoint";
            if (owner != null && owner.isOnline()) {
                MessageUtil.send(owner, "&cYour " + checkpointType + " at &f(" +
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

        boolean isRegularCampfire = (type == Material.CAMPFIRE);
        boolean isSoulCampfire = (type == Material.SOUL_CAMPFIRE);

        if (!isRegularCampfire && !isSoulCampfire) {
            return;
        }

        ConfigManager configManager = plugin.getConfigManager();
        World.Environment env = event.getPlayer().getWorld().getEnvironment();

        switch (env) {
            case NORMAL:
                if (isRegularCampfire && !configManager.isDimentionEnabledOverworld()) return;
                if (isSoulCampfire && !configManager.isDimentionEnabledOverworldSoul()) return;
                break;
            case NETHER:
                if (isRegularCampfire && !configManager.isDimentionEnabledNether()) return;
                if (isSoulCampfire && !configManager.isDimentionEnabledNetherSoul()) return;
                break;
            case THE_END:
                if (isRegularCampfire && !configManager.isDimentionEnabledEnd()) return;
                if (isSoulCampfire && !configManager.isDimentionEnabledEndSoul()) return;
                break;
        }

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