// src/main/java/com/campfirecheckpoints/model/Checkpoint.java
package com.campfirecheckpoints.model;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class Checkpoint {

    private final @NotNull UUID ownerUUID;
    private final @NotNull String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final long createdAt;
    private volatile boolean lit;

    public Checkpoint(@NotNull UUID ownerUUID, @NotNull Location location) {
        Objects.requireNonNull(ownerUUID, "Owner UUID cannot be null");
        Objects.requireNonNull(location, "Location cannot be null");
        Objects.requireNonNull(location.getWorld(), "World cannot be null");

        this.ownerUUID = ownerUUID;
        this.worldName = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        this.createdAt = System.currentTimeMillis();
        this.lit = true;
    }

    private Checkpoint(@NotNull UUID ownerUUID, @NotNull String worldName, 
                       int x, int y, int z, long createdAt, boolean lit) {
        this.ownerUUID = ownerUUID;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.createdAt = createdAt;
        this.lit = lit;
    }

    public @NotNull UUID getOwnerUUID() {
        return ownerUUID;
    }

    public @NotNull String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isLit() {
        return lit;
    }

    public void setLit(boolean lit) {
        this.lit = lit;
    }


    public @Nullable Location getSpawnLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
    }

    public @Nullable Location getBlockLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }


    public double distanceSquared(@Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return Double.MAX_VALUE;
        }
        if (!location.getWorld().getName().equals(worldName)) {
            return Double.MAX_VALUE;
        }

        double dx = x - location.getX();
        double dy = y - location.getY();
        double dz = z - location.getZ();
        return dx * dx + dy * dy + dz * dz;
    }


    public boolean isWithinRadius(@Nullable Location location, double radius) {
        if (location == null) {
            return false;
        }
        return distanceSquared(location) <= radius * radius;
    }

    /**
     * Serializes checkpoint to JSON
     */
    public @NotNull JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("ownerUUID", ownerUUID.toString());
        json.addProperty("world", worldName);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        json.addProperty("createdAt", createdAt);
        json.addProperty("lit", lit);
        return json;
    }

    public static @Nullable Checkpoint fromJson(@Nullable JsonObject json) {
        if (json == null) {
            return null;
        }

        try {
            UUID ownerUUID = UUID.fromString(json.get("ownerUUID").getAsString());
            String world = json.get("world").getAsString();
            int x = json.get("x").getAsInt();
            int y = json.get("y").getAsInt();
            int z = json.get("z").getAsInt();
            long createdAt = json.has("createdAt") ? 
                json.get("createdAt").getAsLong() : System.currentTimeMillis();
            boolean lit = !json.has("lit") || json.get("lit").getAsBoolean();

            return new Checkpoint(ownerUUID, world, x, y, z, createdAt, lit);
        } catch (Exception e) {
            return null;
        }
    }


    public @NotNull String getLocationKey() {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Checkpoint other = (Checkpoint) obj;
        return x == other.x && 
               y == other.y && 
               z == other.z && 
               worldName.equals(other.worldName) &&
               ownerUUID.equals(other.ownerUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUUID, worldName, x, y, z);
    }

    @Override
    public String toString() {
        return String.format("Checkpoint{owner=%s, world=%s, pos=[%d, %d, %d], lit=%s}",
            ownerUUID, worldName, x, y, z, lit);
    }
}