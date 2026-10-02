package com.schematicannon.menu;

import com.schematicannon.ModRegistry;
import com.schematicannon.blockentity.SchematicannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SchematicannonMenu extends AbstractContainerMenu {

    public static final int BUTTON_START_STOP = 1;
    public static final int BUTTON_IGNORE_AIR = 2;
    public static final int BUTTON_REPLACE = 3;
    public static final int BUTTON_SPEED = 4;
    public static final int BUTTON_RESET = 5;
    public static final int BUTTON_SKIP_MISSING = 6;
    public static final int BUTTON_MATERIAL_LIST = 7;
    public static final int BUTTON_TARGET = 8;

    private final Container container;
    private final ContainerData data;

    public SchematicannonMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModRegistry.SCHEMATICANNON_MENU.get(), containerId);
        this.container = container;
        this.data = data;
        addDataSlots(data);

        addSlot(new FuelSlot(container, SchematicannonBlockEntity.SLOT_FUEL, 174, 24));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(container, SchematicannonBlockEntity.MATERIAL_START + row * 9 + col,
                        8 + col * 18, 74 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 264 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 322));
        }
    }

    /** Client side factory - the real content is synced by vanilla afterwards. */
    public static SchematicannonMenu createClientMenu(int containerId, Inventory playerInventory) {
        return new SchematicannonMenu(containerId, playerInventory,
                new SimpleContainer(SchematicannonBlockEntity.CONTAINER_SIZE),
                new SimpleContainerData(SchematicannonBlockEntity.DATA_COUNT));
    }

    public ContainerData getSyncedData() {
        return data;
    }

    public BlockPos blockPos() {
        return new BlockPos(data.get(SchematicannonBlockEntity.DATA_POS_X),
                data.get(SchematicannonBlockEntity.DATA_POS_Y),
                data.get(SchematicannonBlockEntity.DATA_POS_Z));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        BlockEntity be = player.level().getBlockEntity(blockPos());
        if (!(be instanceof SchematicannonBlockEntity cannon)) {
            return false;
        }
        switch (id) {
            case BUTTON_START_STOP -> cannon.setRunning(!cannon.isRunning(), player);
            case BUTTON_IGNORE_AIR -> cannon.setOption(SchematicannonBlockEntity.OPT_IGNORE_AIR, !cannon.isIgnoringAir());
            case BUTTON_REPLACE -> cannon.setOption(SchematicannonBlockEntity.OPT_REPLACE_BLOCKS, !cannon.isReplacingBlocks());
            case BUTTON_SPEED -> cannon.cycleInterval();
            case BUTTON_RESET -> cannon.resetProgress();
            case BUTTON_SKIP_MISSING ->
                    cannon.setOption(SchematicannonBlockEntity.OPT_SKIP_MISSING, !cannon.isSkippingMissing());
            case BUTTON_MATERIAL_LIST -> cannon.exportMaterialList(player);
            case BUTTON_TARGET -> {
                // Without a target there is nothing to clear, so explain the two step flow instead
                // of letting the click look broken.
                if (cannon.getPrintTarget() != null) {
                    cannon.setPrintTarget(null);
                    player.sendSystemMessage(Component.translatable("schematicannon.quill.target_cleared"));
                } else {
                    player.sendSystemMessage(Component.translatable("schematicannon.quill.target_hint"));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        int containerEnd = SchematicannonBlockEntity.CONTAINER_SIZE;
        int playerEnd = this.slots.size();

        if (index < containerEnd) {
            if (!this.moveItemStackTo(stack, containerEnd, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, containerEnd, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /** Only accepts items that actually burn (checked through the fuel registry at runtime). */
    private static class FuelSlot extends Slot {
        FuelSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty();
        }
    }
}
