package com.schematicannon.menu;

import com.schematicannon.ModRegistry;
import com.schematicannon.blockentity.SchematicTableBlockEntity;
import com.schematicannon.item.SchematicAndQuillItem;
import net.minecraft.core.BlockPos;
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

public class SchematicTableMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;

    public SchematicTableMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModRegistry.SCHEMATIC_TABLE_MENU.get(), containerId);
        this.container = container;
        this.data = data;
        addDataSlots(data);

        addSlot(new QuillSlot(container, SchematicTableBlockEntity.SLOT_QUILL, 80, 30));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 118 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 176));
        }
    }

    public static SchematicTableMenu createClientMenu(int containerId, Inventory playerInventory) {
        return new SchematicTableMenu(containerId, playerInventory,
                new SimpleContainer(SchematicTableBlockEntity.CONTAINER_SIZE),
                new SimpleContainerData(SchematicTableBlockEntity.DATA_COUNT));
    }

    public ContainerData getSyncedData() {
        return data;
    }

    public BlockPos blockPos() {
        return new BlockPos(data.get(SchematicTableBlockEntity.DATA_POS_X),
                data.get(SchematicTableBlockEntity.DATA_POS_Y),
                data.get(SchematicTableBlockEntity.DATA_POS_Z));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        int containerEnd = SchematicTableBlockEntity.CONTAINER_SIZE;
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

    private static class QuillSlot extends Slot {
        QuillSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof SchematicAndQuillItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

}
