/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ServerData
 *  net.minecraft.client.server.IntegratedServer
 *  net.minecraft.world.level.storage.LevelResource
 */
package dev.selestor1462.cannonencoder.client;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

public record ClientSessionContext(String environmentKey, String dimensionId) {
    public static Optional<ClientSessionContext> resolve(Minecraft client) {
        if (client == null || client.level == null) {
            return Optional.empty();
        }
        Optional<String> environmentKey = ClientSessionContext.resolveEnvironmentKey(client);
        if (environmentKey.isEmpty()) {
            return Optional.empty();
        }
        String dimensionId = client.level.dimension().location().toString();
        return Optional.of(new ClientSessionContext(environmentKey.get(), dimensionId));
    }

    private static Optional<String> resolveEnvironmentKey(Minecraft client) {
        if (client.hasSingleplayerServer()) {
            IntegratedServer server = client.getSingleplayerServer();
            if (server != null) {
                String worldName = null;
                try {
                    if (server.getWorldData() != null) {
                        worldName = server.getWorldData().getLevelName();
                    }
                } catch (Throwable ignored) {
                }
                if (worldName == null || worldName.isBlank()) {
                    try {
                        Path savePath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                        Path fileName = savePath.getFileName();
                        worldName = (fileName != null) ? fileName.toString() : "world";
                    } catch (Throwable ignored) {
                        worldName = "world";
                    }
                }
                return Optional.of("singleplayer:" + worldName);
            }
        }

        ServerData serverInfo = client.getCurrentServer();
        if (serverInfo != null && serverInfo.ip != null && !serverInfo.ip.isBlank()) {
            return Optional.of("multiplayer:" + serverInfo.ip.trim().toLowerCase(Locale.ROOT));
        }

        if (client.getConnection() != null && client.getConnection().getConnection() != null) {
            java.net.SocketAddress address = client.getConnection().getConnection().getRemoteAddress();
            if (address != null) {
                String addrStr = address.toString().trim().toLowerCase(Locale.ROOT);
                if (addrStr.startsWith("/")) {
                    addrStr = addrStr.substring(1);
                }
                return Optional.of("multiplayer:" + addrStr);
            }
        }

        if (client.level != null) {
            return Optional.of("session:default");
        }

        return Optional.empty();
    }
}

