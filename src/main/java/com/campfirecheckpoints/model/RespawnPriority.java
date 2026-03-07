// src/main/java/com/campfirecheckpoints/model/RespawnPriority.java
package com.campfirecheckpoints.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public enum RespawnPriority {
    
    /**
     * Campfire checkpoint always takes priority over bed
     */
    CHECKPOINT("checkpoint"),
    
    /**
     * Bed spawn always takes priority over checkpoint
     */
    BED("bed"),
    
    /**
     * Whichever is closer to the death location takes priority
     */
    CLOSEST("closest");

    private final @NotNull String configValue;

    RespawnPriority(@NotNull String configValue) {
        this.configValue = configValue;
    }

    public @NotNull String getConfigValue() {
        return configValue;
    }


    public static @NotNull RespawnPriority fromConfig(@Nullable String value) {
        if (value == null) {
            return CHECKPOINT;
        }
        
        String lowercaseValue = value.toLowerCase().trim();
        for (RespawnPriority priority : values()) {
            if (priority.configValue.equals(lowercaseValue)) {
                return priority;
            }
        }
        
        return CHECKPOINT;
    }


    public @NotNull String getDescription() {
        return switch (this) {
            case CHECKPOINT -> "Campfire checkpoint takes priority";
            case BED -> "Bed spawn takes priority";
            case CLOSEST -> "Closest to death location takes priority";
        };
    }
}