package com.schematicannon.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import javax.annotation.Nullable;

/**
 * Material supply for the cannon: the six blocks touching it are searched for a stack of the
 * requested item. Vanilla containers (chest, barrel, shulker box, hopper ...) are read directly,
 * everything else goes through the NeoForge item handler capability.
 *
 * <p>The resolution logic mirrors {@code HopperBlockEntity#getBlockContainer} so double chests are
 * treated as one inventory and side restricted blocks keep working.
 */
public final class NeighbourInventory {

    private NeighbourInventory() {
    }

    /** Removes one {@code item} from any touching inventory. Returns false when nothing was found. */
    public static boolean takeItem(Level level, BlockPos origin, Item item) {
        for (Direction dir : Direction.values()) {
            BlockPos pos = origin.relative(dir);
            BlockState state = level.getBlockState(pos);

            Container container = blockContainer(level, pos, state);
            if (container != null) {
                if (take(container, item)) {
                    return true;
                }
                continue;
            }
            ResourceHandler<ItemResource> handler = handler(level, pos, state, dir.getOpposite());
            if (handler != null && take(handler, item)) {
                return true;
            }
        }
        return false;
    }

    /** Counts how many {@code item} the touching inventories currently hold. */
    public static int countItem(Level level, BlockPos origin, Item item) {
        int total = 0;
        for (Direction dir : Direction.values()) {
            BlockPos pos = origin.relative(dir);
            BlockState state = level.getBlockState(pos);

            Container container = blockContainer(level, pos, state);
            if (container != null) {
                total += count(container, item);
                continue;
            }
            ResourceHandler<ItemResource> handler = handler(level, pos, state, dir.getOpposite());
            if (handler != null) {
                total += count(handler, item);
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ vanilla

    @Nullable
    private static Container blockContainer(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof WorldlyContainerHolder holder) {
            return holder.getContainer(state, level, pos);
        }
        if (!state.hasBlockEntity()) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof Container container) {
            if (container instanceof ChestBlockEntity && block instanceof ChestBlock chest) {
                return ChestBlock.getContainer(chest, state, level, pos, true);
            }
            return container;
        }
        return null;
    }

    private static boolean take(Container container, Item item) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }
            ItemStack removed = container.removeItem(i, 1);
            if (!removed.isEmpty()) {
                container.setChanged();
                return true;
            }
        }
        return false;
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ modded

    @Nullable
    private static ResourceHandler<ItemResource> handler(Level level, BlockPos pos, BlockState state, Direction side) {
        try {
            return level.getCapability(Capabilities.Item.BLOCK, pos, state, null, side);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean take(ResourceHandler<ItemResource> handler, Item item) {
        try {
            for (int i = 0; i < handler.size(); i++) {
                ItemResource resource = handler.getResource(i);
                if (resource.isEmpty() || !resource.is(item)) {
                    continue;
                }
                try (Transaction tx = Transaction.openRoot()) {
                    if (handler.extract(i, resource, 1, tx) > 0) {
                        tx.commit();
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    private static int count(ResourceHandler<ItemResource> handler, Item item) {
        int total = 0;
        try {
            for (int i = 0; i < handler.size(); i++) {
                ItemResource resource = handler.getResource(i);
                if (!resource.isEmpty() && resource.is(item)) {
                    total += handler.getAmountAsInt(i);
                }
            }
        } catch (Throwable ignored) {
            // A broken third party handler must never break the material list.
        }
        return total;
    }
}
