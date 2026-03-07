// src/main/java/com/campfirecheckpoints/util/MessageUtil.java
package com.campfirecheckpoints.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class MessageUtil {

    private static final String PREFIX = "&8[&6Campfire&8] ";
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = 
        LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {
        // Utility class
    }

    /**
     * Sends a formatted message to a command sender
     */
    public static void send(@NotNull CommandSender sender, @NotNull String message) {
        Component component = LEGACY_SERIALIZER.deserialize(PREFIX + message)
            .decoration(TextDecoration.ITALIC, false);
        sender.sendMessage(component);
    }

    /**
     * Sends a message without prefix
     */
    public static void sendRaw(@NotNull CommandSender sender, @NotNull String message) {
        Component component = LEGACY_SERIALIZER.deserialize(message)
            .decoration(TextDecoration.ITALIC, false);
        sender.sendMessage(component);
    }

    /**
     * Converts legacy color codes to Component
     */
    public static @NotNull Component colorize(@NotNull String message) {
        return LEGACY_SERIALIZER.deserialize(message);
    }
}