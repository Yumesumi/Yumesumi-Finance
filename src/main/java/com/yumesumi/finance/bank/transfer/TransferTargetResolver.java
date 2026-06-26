package com.yumesumi.finance.bank.transfer;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class TransferTargetResolver {
    private TransferTargetResolver() {
    }

    public static Optional<TransferTarget> resolve(MinecraftServer server, String rawTarget) {
        String target = rawTarget == null ? "" : rawTarget.trim();
        if (target.isEmpty()) {
            return Optional.empty();
        }

        try {
            UUID uuid = UUID.fromString(target);
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(uuid);
            String name = onlinePlayer == null ? uuid.toString() : onlinePlayer.getGameProfile().getName();
            return Optional.of(new TransferTarget(uuid, name));
        } catch (IllegalArgumentException ignored) {
            // Not a UUID; try player name below.
        }

        ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(target);
        if (onlinePlayer != null) {
            return Optional.of(new TransferTarget(onlinePlayer.getUUID(), onlinePlayer.getGameProfile().getName()));
        }

        return server.getProfileCache().get(target)
                .map(GameProfile::getId)
                .map(uuid -> new TransferTarget(uuid, target));
    }
}
