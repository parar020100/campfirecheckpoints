// src/main/java/com/campfirecheckpoints/manager/CheckpointManager.java
package com.campfirecheckpoints.manager;

import com.campfirecheckpoints.CampfireCheckpoints;
import com.campfirecheckpoints.model.Checkpoint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import com.campfirecheckpoints.util.MessageUtil;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public final class CheckpointManager {

    private final @NotNull CampfireCheckpoints plugin;
    private final @NotNull File dataFile;
    private final @NotNull Gson gson;

    private final @NotNull Map<UUID, List<Checkpoint>> playerCheckpoints;

    private final @NotNull Map<String, Checkpoint> locationIndex;

    private final @NotNull Map<UUID, PendingOverride> pendingOverrides;

    private final @NotNull ReentrantLock overrideLock;

    private final @NotNull AtomicBoolean dirty;
    private @Nullable BukkitTask saveTask;
    private static final long SAVE_DELAY_TICKS = 100L; // 5 seconds

    public CheckpointManager(@NotNull CampfireCheckpoints plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "checkpoints.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.playerCheckpoints = new ConcurrentHashMap<>();
        this.locationIndex = new ConcurrentHashMap<>();
        this.pendingOverrides = new ConcurrentHashMap<>();
        this.overrideLock = new ReentrantLock();
        this.dirty = new AtomicBoolean(false);
    }

    public void loadCheckpoints() {
        playerCheckpoints.clear();
        locationIndex.clear();

        if (!dataFile.exists()) {
            plugin.getLogger().info("No checkpoints file found. Starting fresh.");
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("checkpoints")) {
                JsonArray checkpointsArray = root.getAsJsonArray("checkpoints");
                int loaded = 0;
                int failed = 0;

                for (JsonElement element : checkpointsArray) {
                    if (element.isJsonObject()) {
                        Checkpoint checkpoint = Checkpoint.fromJson(element.getAsJsonObject());
                        if (checkpoint != null) {
                            playerCheckpoints
                                .computeIfAbsent(checkpoint.getOwnerUUID(), k -> 
                                    Collections.synchronizedList(new ArrayList<>()))
                                .add(checkpoint);
                            locationIndex.put(checkpoint.getLocationKey(), checkpoint);
                            loaded++;
                        } else {
                            failed++;
                        }
                    }
                }

                plugin.getLogger().info("Loaded " + loaded + " checkpoints" + 
                    (failed > 0 ? " (" + failed + " failed)" : ""));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load checkpoints!", e);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error parsing checkpoints file!", e);
        }
    }

    private void markDirty() {
        if (dirty.compareAndSet(false, true)) {
            if (saveTask != null && !saveTask.isCancelled()) {
                saveTask.cancel();
            }

            saveTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                if (dirty.compareAndSet(true, false)) {
                    saveCheckpointsInternal();
                }
            }, SAVE_DELAY_TICKS);
        }
    }

    public void shutdown() {
        if (saveTask != null && !saveTask.isCancelled()) {
            saveTask.cancel();
        }


        if (dirty.get()) {
            saveCheckpointsInternal();
        } else {
            saveCheckpointsInternal();
        }
    }

    /**
     * save method
     */
    private void saveCheckpointsInternal() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        JsonObject root = new JsonObject();
        JsonArray checkpointsArray = new JsonArray();

        Map<UUID, List<Checkpoint>> snapshot = new HashMap<>();
        for (Map.Entry<UUID, List<Checkpoint>> entry : playerCheckpoints.entrySet()) {
            synchronized (entry.getValue()) {
                snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        for (List<Checkpoint> checkpoints : snapshot.values()) {
            for (Checkpoint checkpoint : checkpoints) {
                checkpointsArray.add(checkpoint.toJson());
            }
        }

        root.add("checkpoints", checkpointsArray);
        root.addProperty("lastSaved", System.currentTimeMillis());

        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(root, writer);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save checkpoints!", e);
        }
    }

    public @NotNull List<Checkpoint> getPlayerCheckpoints(@NotNull UUID playerUUID) {
        List<Checkpoint> checkpoints = playerCheckpoints.get(playerUUID);
        if (checkpoints == null) {
            return new ArrayList<>();
        }
        synchronized (checkpoints) {
            return new ArrayList<>(checkpoints);
        }
    }

    public int getPlayerCheckpointCount(@NotNull UUID playerUUID) {
        List<Checkpoint> checkpoints = playerCheckpoints.get(playerUUID);
        if (checkpoints == null) {
            return 0;
        }
        synchronized (checkpoints) {
            return checkpoints.size();
        }
    }

    public boolean canCreateCheckpoint(@NotNull UUID playerUUID) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.hasCheckpointLimit()) {
            return true;
        }
        return getPlayerCheckpointCount(playerUUID) < config.getMaxCheckpointsPerPlayer();
    }

    public void addCheckpoint(@NotNull UUID playerUUID, @NotNull Checkpoint checkpoint) {
        List<Checkpoint> checkpoints = playerCheckpoints.computeIfAbsent(playerUUID, 
            k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (checkpoints) {
            checkpoints.add(checkpoint);
        }
        locationIndex.put(checkpoint.getLocationKey(), checkpoint);
        markDirty();
    }

    public boolean removeCheckpoint(@NotNull UUID playerUUID, int index) {
        List<Checkpoint> checkpoints = playerCheckpoints.get(playerUUID);
        if (checkpoints == null) {
            return false;
        }

        Checkpoint removed;
        synchronized (checkpoints) {
            if (index < 0 || index >= checkpoints.size()) {
                return false;
            }
            removed = checkpoints.remove(index);
            if (checkpoints.isEmpty()) {
                playerCheckpoints.remove(playerUUID);
            }
        }

        if (removed != null) {
            locationIndex.remove(removed.getLocationKey());
        }
        markDirty();
        return true;
    }

    public @Nullable Checkpoint removeCheckpointAt(@NotNull Location location) {
        String locationKey = createLocationKey(location);
        Checkpoint checkpoint = locationIndex.remove(locationKey);

        if (checkpoint != null) {
            List<Checkpoint> checkpoints = playerCheckpoints.get(checkpoint.getOwnerUUID());
            if (checkpoints != null) {
                synchronized (checkpoints) {
                    checkpoints.remove(checkpoint);
                    if (checkpoints.isEmpty()) {
                        playerCheckpoints.remove(checkpoint.getOwnerUUID());
                    }
                }
            }
            markDirty();
        }

        return checkpoint;
    }

    public @Nullable Checkpoint findCheckpointWithinRadius(@NotNull UUID playerUUID, 
                                                            @NotNull Location location, 
                                                            double radius) {
        List<Checkpoint> checkpoints = playerCheckpoints.get(playerUUID);
        if (checkpoints == null) {
            return null;
        }

        synchronized (checkpoints) {
            for (Checkpoint checkpoint : checkpoints) {
                if (checkpoint.isWithinRadius(location, radius)) {
                    return checkpoint;
                }
            }
        }
        return null;
    }

    public @Nullable Checkpoint findClosestCheckpoint(@NotNull UUID playerUUID, 
                                                       @NotNull Location deathLocation) {
        List<Checkpoint> checkpoints = playerCheckpoints.get(playerUUID);
        if (checkpoints == null) {
            return null;
        }

        double radius = plugin.getConfigManager().getRadius();
        double radiusSquared = radius * radius;

        double radiusSoul = plugin.getConfigManager().getSoulRadius();
        double radiusSoulSquared = radiusSoul * radiusSoul;

        synchronized (checkpoints) {
            return checkpoints.stream()
                .filter(Checkpoint::isLit)
                .filter(cp -> cp.isWithinRespawnRadius(deathLocation, radiusSquared, radiusSoulSquared))
                .min(Comparator.comparingDouble(cp -> cp.distanceSquared(deathLocation)))
                .orElse(null);
        }
    }

    public boolean hasCheckpointAt(@NotNull UUID playerUUID, @NotNull Location location) {
        String locationKey = createLocationKey(location);
        Checkpoint checkpoint = locationIndex.get(locationKey);
        return checkpoint != null && checkpoint.getOwnerUUID().equals(playerUUID);
    }

    public void setCheckpointLit(@NotNull Checkpoint checkpoint, boolean lit) {
        checkpoint.setLit(lit);
        markDirty();
    }

    public @Nullable Checkpoint getCheckpointAt(@NotNull Location location) {
        String locationKey = createLocationKey(location);
        return locationIndex.get(locationKey);
    }

    private @NotNull String createLocationKey(@NotNull Location location) {
        return location.getWorld().getName() + ":" + 
               location.getBlockX() + ":" + 
               location.getBlockY() + ":" + 
               location.getBlockZ();
    }


    public void setPendingOverride(@NotNull UUID playerUUID, @NotNull Location newLocation, double radius) {
        overrideLock.lock();
        try {
            pendingOverrides.put(playerUUID,
                                 new PendingOverride(newLocation, radius,
                                                     System.currentTimeMillis()));
        } finally {
            overrideLock.unlock();
        }
    }

    public boolean tryExecuteOverride(@NotNull UUID playerUUID, @NotNull Location clickedLocation) {
        overrideLock.lock();
        try {
            PendingOverride pending = pendingOverrides.get(playerUUID);
            if (pending == null) {
                return false;
            }

            // Check if expired
            long timeout = plugin.getConfigManager().getOverrideConfirmationTimeoutMillis();
            if (System.currentTimeMillis() - pending.timestamp > timeout) {
                pendingOverrides.remove(playerUUID);
                return false;
            }

            // Check if same location clicked
            if (!locationEquals(pending.newLocation, clickedLocation)) {
                pendingOverrides.remove(playerUUID);
                return false;
            }

            // Execute the override atomically
            executeOverrideInternal(playerUUID, pending);
            pendingOverrides.remove(playerUUID);
            return true;
        } finally {
            overrideLock.unlock();
        }
    }

    private void executeOverrideInternal(@NotNull UUID playerUUID, @NotNull PendingOverride pending) {
        // Remove old checkpoint
        List<Checkpoint> checkpoints = playerCheckpoints.get(playerUUID);
        if (checkpoints != null) {
            List<Checkpoint> toRemove = new ArrayList<>();
            List<String> keysToRemove = new ArrayList<>();

            synchronized (checkpoints) {
                for (Checkpoint checkpoint : checkpoints) {
                    if (checkpoint.isWithinRadius(pending.newLocation, pending.radius)) {
                        toRemove.add(checkpoint);
                        keysToRemove.add(checkpoint.getLocationKey());
                    }
                }
            }

            synchronized (checkpoints) {
                for (Checkpoint checkpoint : toRemove) {
                    checkpoints.remove(checkpoint);
                }

                if (checkpoints.isEmpty()) {
                    playerCheckpoints.remove(playerUUID);
                }
            }

            Player owner = Bukkit.getPlayer(playerUUID);

            synchronized(locationIndex) {
                for (String key : keysToRemove) {
                    locationIndex.remove(key);
                    if (owner != null && owner.isOnline()) {
                    MessageUtil.send(owner, "&fPrevious checkpoint removed: &e(" + key + ")!");
                    }
                }
            }
        }

        // Add new checkpoint
        Checkpoint newCheckpoint = new Checkpoint(playerUUID, pending.newLocation);
        addCheckpoint(playerUUID, newCheckpoint);
    }

    public boolean hasPendingOverride(@NotNull UUID playerUUID) {
        overrideLock.lock();
        try {
            PendingOverride pending = pendingOverrides.get(playerUUID);
            if (pending == null) {
                return false;
            }

            long timeout = plugin.getConfigManager().getOverrideConfirmationTimeoutMillis();
            if (System.currentTimeMillis() - pending.timestamp > timeout) {
                pendingOverrides.remove(playerUUID);
                return false;
            }
            return true;
        } finally {
            overrideLock.unlock();
        }
    }

    public @Nullable PendingOverride getPendingOverride(@NotNull UUID playerUUID) {
        overrideLock.lock();
        try {
            PendingOverride pending = pendingOverrides.get(playerUUID);
            if (pending == null) {
                return null;
            }

            long timeout = plugin.getConfigManager().getOverrideConfirmationTimeoutMillis();
            if (System.currentTimeMillis() - pending.timestamp > timeout) {
                pendingOverrides.remove(playerUUID);
                return null;
            }
            return pending;
        } finally {
            overrideLock.unlock();
        }
    }

    public void clearPendingOverride(@NotNull UUID playerUUID) {
        overrideLock.lock();
        try {
            pendingOverrides.remove(playerUUID);
        } finally {
            overrideLock.unlock();
        }
    }

    private boolean locationEquals(@NotNull Location a, @NotNull Location b) {
        return a.getBlockX() == b.getBlockX() &&
               a.getBlockY() == b.getBlockY() &&
               a.getBlockZ() == b.getBlockZ() &&
               Objects.equals(a.getWorld(), b.getWorld());
    }

    public static final class PendingOverride {
        public final @NotNull double radius;
        public final @NotNull Location newLocation;
        public final long timestamp;

        public PendingOverride(@NotNull Location newLocation, double radius,
                               long timestamp) {
            this.radius = radius;
            this.newLocation = newLocation;
            this.timestamp = timestamp;
        }
    }

    public int getTotalCheckpointCount() {
        return locationIndex.size();
    }

    public void validateAllCheckpoints(@Nullable UUID playerUUID) {
        List<Checkpoint> toRemove = new ArrayList<>();
        int extinguished = 0;
        if (playerUUID == null) {
            for (List<Checkpoint> checkpoints : playerCheckpoints.values()) {
                CheckpointValidationResult result = checkAndCollectInvalid(checkpoints);
                toRemove.addAll(result.toRemove);
                extinguished += result.extinguished;
            }
        } else {
            List<Checkpoint> checkpoints = playerCheckpoints.get(playerUUID);
            if (checkpoints != null) {
                CheckpointValidationResult result = checkAndCollectInvalid(checkpoints);
                toRemove.addAll(result.toRemove);
                extinguished += result.extinguished;
            }
        }
        for (Checkpoint checkpoint : toRemove) {
            removeCheckpointAt(checkpoint.getBlockLocation());
        }
        if (!toRemove.isEmpty() || extinguished > 0) {
            markDirty();
            String prefix = (playerUUID == null) ? "[All]" : "[Player]";
            plugin.getLogger().info(prefix + " Removed " + toRemove.size() + " invalid checkpoints, extinguished " + extinguished + ".");
        }
    }

    private static class CheckpointValidationResult {
        List<Checkpoint> toRemove = new ArrayList<>();
        int extinguished = 0;
    }

    private CheckpointValidationResult checkAndCollectInvalid(List<Checkpoint> checkpoints) {
        CheckpointValidationResult result = new CheckpointValidationResult();
        synchronized (checkpoints) {
            for (Checkpoint checkpoint : checkpoints) {
                Location loc = checkpoint.getBlockLocation();
                if (loc == null || loc.getWorld() == null) {
                    result.toRemove.add(checkpoint);
                    continue;
                }
                org.bukkit.block.Block block = loc.getWorld().getBlockAt(loc);
                org.bukkit.Material type = block.getType();
                boolean isCampfire = (type == org.bukkit.Material.CAMPFIRE);
                boolean isSoulCampfire = (type == org.bukkit.Material.SOUL_CAMPFIRE);
                if (!isCampfire && !isSoulCampfire) {
                    result.toRemove.add(checkpoint);
                    continue;
                }
                org.bukkit.block.data.BlockData data = block.getBlockData();
                if (data instanceof org.bukkit.block.data.Lightable lightable) {
                    if (!lightable.isLit()) {
                        if (checkpoint.isLit()) {
                            setCheckpointLit(checkpoint, false);
                            result.extinguished++;
                        }
                    } else {
                        if (!checkpoint.isLit()) {
                            setCheckpointLit(checkpoint, true);
                        }
                    }
                } else {
                    result.toRemove.add(checkpoint);
                }
            }
        }
        return result;
    }
}