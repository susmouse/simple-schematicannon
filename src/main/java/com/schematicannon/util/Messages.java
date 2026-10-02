package com.schematicannon.util;

import com.schematicannon.blockentity.SchematicTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Chat side feedback: everything that stops the cannon or the table is reported to the player. */
public final class Messages {

    /** Blocks - how far a cannon message travels when nobody opened the GUI recently. */
    public static final double DEFAULT_RADIUS = 24.0;

    private Messages() {
    }

    /**
     * Sends {@code message} to {@code direct} (if any) plus every player within {@code radius}.
     * Server side only - calling it on the client is a no-op.
     */
    public static void sendNear(@Nullable Level level, BlockPos pos, Component message, double radius,
                                @Nullable Player direct) {
        if (level == null || level.isClientSide()) {
            return;
        }
        Set<UUID> sent = new HashSet<>();
        if (direct != null) {
            direct.sendSystemMessage(message);
            sent.add(direct.getUUID());
        }
        double r2 = radius * radius;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        for (Player player : level.players()) {
            if (player == null || sent.contains(player.getUUID())) {
                continue;
            }
            if (player.distanceToSqr(cx, cy, cz) > r2) {
                continue;
            }
            player.sendSystemMessage(message);
            sent.add(player.getUUID());
        }
    }

    public static void sendNear(@Nullable Level level, BlockPos pos, Component message) {
        sendNear(level, pos, message, DEFAULT_RADIUS, null);
    }

    /** Human readable reason for a failed schematic load. */
    public static Component loadErrorReason(@Nullable String code) {
        if (code == null) {
            return Component.translatable("schematicannon.error.unknown");
        }
        return switch (code) {
            case "missing" -> Component.translatable("schematicannon.error.missing");
            case "invalid" -> Component.translatable("schematicannon.error.invalid");
            case "path" -> Component.translatable("schematicannon.error.path");
            case "empty" -> Component.translatable("schematicannon.error.empty");
            default -> Component.literal(code);
        };
    }

    /** Human readable result of a schematic table save attempt. */
    public static Component tableResult(int result, String fileName, int blocks, @Nullable String detail) {
        return switch (result) {
            case SchematicTableBlockEntity.RESULT_OK ->
                    Component.translatable("schematicannon.msg.saved", fileName, blocks);
            case SchematicTableBlockEntity.RESULT_NO_QUILL ->
                    Component.translatable("schematicannon.msg.save_failed",
                            Component.translatable("schematicannon.result.no_quill"));
            case SchematicTableBlockEntity.RESULT_NO_REGION ->
                    Component.translatable("schematicannon.msg.save_failed",
                            Component.translatable("schematicannon.result.no_region"));
            case SchematicTableBlockEntity.RESULT_NO_NAME ->
                    Component.translatable("schematicannon.msg.save_failed",
                            Component.translatable("schematicannon.result.no_name"));
            default -> Component.translatable("schematicannon.msg.save_failed",
                    detail == null || detail.isBlank()
                            ? Component.translatable("schematicannon.error.unknown")
                            : Component.literal(detail));
        };
    }
}
