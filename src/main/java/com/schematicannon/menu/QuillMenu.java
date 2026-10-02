package com.schematicannon.menu;

import com.schematicannon.ModRegistry;
import com.schematicannon.item.SchematicAndQuillItem;
import com.schematicannon.schematic.SchematicTransform;
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
import net.minecraft.world.InteractionHand;

/**
 * The quill's control panel: pick the mode, and aim the print orientation.
 *
 * <p>Opened by right clicking the air with the quill (see {@link SchematicAndQuillItem#use}).
 *
 * <p>There is no container here - the "slots" are the player's own inventory, kept so the panel looks
 * and behaves like every other screen in the game. All state lives in the held stack's item NBT, and
 * every button is handled on the <b>server</b> in {@link #clickMenuButton}: the menu's button packet
 * is a normal vehicle, which sidesteps the desync that plain client side key handling suffers from.
 *
 * <p>The orientation is shown live because the client mirrors the same NBT it just edited, so the
 * labels update on the same frame the button is pressed.
 */
public class QuillMenu extends AbstractContainerMenu {

    public static final int BUTTON_MODE_SELECT = 1;
    public static final int BUTTON_MODE_TARGET = 2;
    public static final int BUTTON_ROTATE_LEFT = 3;
    public static final int BUTTON_ROTATE_RIGHT = 4;
    public static final int BUTTON_FLIP = 5;
    public static final int BUTTON_RESET = 6;

    /**
     * Which hand the quill was in when the menu opened. The server cannot rely on the player still
     * holding it in that hand, so it re-checks before writing.
     */
    private final int handOrdinal;

    private final Container container;
    private final ContainerData data;

    public QuillMenu(int containerId, Inventory playerInventory, int handOrdinal) {
        super(ModRegistry.QUILL_MENU.get(), containerId);
        this.handOrdinal = handOrdinal;
        this.container = new SimpleContainer(0);
        this.data = new SimpleContainerData(0);

        // Inventory only, so the panel still feels like a normal screen. No container slots: the
        // quill itself is the state, and it is already in the player's hand.
        // Layout is mirrored in QuillScreen (SLOTS_TOP) - keep the two in step.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 118 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 176));
        }
    }

    /** Client side factory - the real state is the held stack, read on demand. */
    public static QuillMenu createClientMenu(int containerId, Inventory playerInventory) {
        return new QuillMenu(containerId, playerInventory, 0);
    }

    public ContainerData getSyncedData() {
        return data;
    }

    /** The hand the quill was in when the menu opened. */
    public int handOrdinal() {
        return handOrdinal;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        ItemStack held = heldQuill(player);
        if (held.isEmpty()) {
            return false;
        }
        switch (id) {
            case BUTTON_MODE_SELECT -> SchematicAndQuillItem.setMode(held, SchematicAndQuillItem.Mode.SELECT);
            case BUTTON_MODE_TARGET -> SchematicAndQuillItem.setMode(held, SchematicAndQuillItem.Mode.TARGET);
            case BUTTON_ROTATE_LEFT -> SchematicAndQuillItem.rotate(held, false);
            case BUTTON_ROTATE_RIGHT -> SchematicAndQuillItem.rotate(held, true);
            case BUTTON_FLIP -> SchematicAndQuillItem.flip(held);
            case BUTTON_RESET -> SchematicAndQuillItem.resetTransform(held);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The held quill on the server, or empty when the player swapped it away meanwhile. */
    private ItemStack heldQuill(Player player) {
        InteractionHand hand = handOrdinal == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        return stack.getItem() instanceof SchematicAndQuillItem ? stack : ItemStack.EMPTY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Nothing to move: the only slots are the player's own.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // Tied to the item, not to a block, so it only stays valid while the quill is still held.
        return !heldQuill(player).isEmpty();
    }
}
