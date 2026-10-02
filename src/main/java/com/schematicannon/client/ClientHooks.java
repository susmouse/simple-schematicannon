package com.schematicannon.client;

import com.schematicannon.ModRegistry;
import com.schematicannon.item.SchematicAndQuillItem;
import com.schematicannon.menu.SchematicannonMenu;
import com.schematicannon.menu.SchematicTableMenu;
import com.schematicannon.schematic.SchematicTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import javax.annotation.Nullable;

/** Client only glue: screen registration, server sent data and the ghost preview renderer. */
public final class ClientHooks {

    /** Offhand selection marker uses a warm amber so the two quills never look alike. */
    private static final int COLOR_MAIN_HAND = 0x4A80FF;
    private static final int COLOR_OFF_HAND = 0xFFB03A;
    /** Target mode aims a cannon, so it reads green to stand apart from plain selection. */
    private static final int COLOR_TARGET_MODE = 0x5BE08A;
    /** The block under the crosshair in target mode, before the click commits it. */
    private static final int COLOR_TARGET_AIM = 0xFFD84A;

    private static SchematicannonScreen activeCannonScreen;

    private ClientHooks() {
    }

    public static void registerScreens(final RegisterMenuScreensEvent event) {
        event.register(ModRegistry.SCHEMATICANNON_MENU.get(),
                (MenuScreens.ScreenConstructor<SchematicannonMenu, SchematicannonScreen>) SchematicannonScreen::new);
        event.register(ModRegistry.SCHEMATIC_TABLE_MENU.get(),
                (MenuScreens.ScreenConstructor<SchematicTableMenu, SchematicTableScreen>) SchematicTableScreen::new);
        event.register(ModRegistry.QUILL_MENU.get(),
                (MenuScreens.ScreenConstructor<com.schematicannon.menu.QuillMenu, QuillScreen>) QuillScreen::new);
    }

    static void setActiveCannonScreen(SchematicannonScreen screen) {
        activeCannonScreen = screen;
    }

    public static void acceptFileList(String joined) {
        if (activeCannonScreen != null) {
            activeCannonScreen.setAvailableFiles(joined);
        }
    }

    public static void acceptCannonState(BlockPos pos, String name) {
        if (activeCannonScreen != null) {
            activeCannonScreen.setLoadedName(pos, name);
        }
    }

    public static void acceptPreview(BlockPos pos, long[] packed, int[] states, int transform) {
        SchematicPreview.set(pos, packed, states, transform);
    }

    /** Draws the pending structure of every loaded cannon plus the live quill selection. */
    public static void onSubmitGeometry(final SubmitCustomGeometryEvent event) {
        SchematicPreview.render(event);
    }

    public static void onClientTick(final ClientTickEvent.Post event) {
        refreshQuillPreview();
    }

    /**
     * The quill selection is local, cheap state, so it is rebuilt once per client tick from the
     * player's own hands instead of being synced by the server.
     */
    private static void refreshQuillPreview() {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) {
            return;
        }
        final ItemStack main = player.getMainHandItem();
        final ItemStack off = player.getOffhandItem();
        final boolean inMain = main.getItem() instanceof SchematicAndQuillItem;
        final boolean inOff = !inMain && off.getItem() instanceof SchematicAndQuillItem;
        if (!inMain && !inOff) {
            SchematicPreview.tickSelection(false, null, null, 0);
            SchematicPreview.tickTarget(null, null, 0, 0);
            return;
        }
        final ItemStack held = inMain ? main : off;

        // Target mode picks one single origin, not a region: show the committed point in green and
        // the block under the crosshair in yellow, so "one click = one corner" is obvious.
        if (SchematicAndQuillItem.getMode(held) == SchematicAndQuillItem.Mode.TARGET) {
            SchematicPreview.tickSelection(false, null, null, 0);
            SchematicPreview.tickTarget(SchematicAndQuillItem.getTargetPos(held),
                    SchematicAndQuillItem.aimedPos(player), COLOR_TARGET_MODE, COLOR_TARGET_AIM);
            return;
        }
        SchematicPreview.tickTarget(null, null, 0, 0);

        final BlockPos first = SchematicAndQuillItem.getFirstPos(held);
        final BlockPos second = SchematicAndQuillItem.getSecondPos(held);
        // Once the second corner is anchored the region is final: stop drawing the live box, it would
        // otherwise keep chasing the crosshair and pretend the selection is still open.
        if (second != null) {
            SchematicPreview.tickSelection(false, null, null, 0);
            return;
        }
        // Raw aim, no clamping: the region is the plain cuboid between the two corners, so the ghost
        // box is a real volume and matches exactly what the second click will store.
        final BlockPos head = SchematicAndQuillItem.aimedPos(player);
        int color = inMain ? COLOR_MAIN_HAND : COLOR_OFF_HAND;
        SchematicPreview.tickSelection(true, first, head, color);
    }

    public static void onLoggedOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        SchematicPreview.clearAll();
    }

    /**
     * Rotate and mirror the held quill's print orientation from the keyboard.
     *
     * <p>R / T turn (they sit next to each other by the movement keys and are not bound to anything
     * while no GUI is open), F mirrors, G resets. The keys only do something while a quill in target
     * mode is held, so they cost nothing the rest of the time.
     *
     * <p>This mirrors what the menu buttons do, and must stay in sync the same way they do: the NBT
     * is edited locally for an instant response, then pushed to the server with
     * {@link com.schematicannon.network.ModNetwork#sendQuillState}. Skipping that second step is what
     * made only the first change stick - the server kept using its own stale copy.
     */
    public static void onKeyInput(final InputEvent.Key event) {
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) {
            return;
        }
        final ItemStack held = heldQuill(player);
        if (held == null || SchematicAndQuillItem.getMode(held) != SchematicAndQuillItem.Mode.TARGET) {
            return;
        }
        boolean handled = switch (event.getKey()) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_R -> {
                announceOrientation(SchematicAndQuillItem.rotate(held, false));
                yield true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_T -> {
                announceOrientation(SchematicAndQuillItem.rotate(held, true));
                yield true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_F -> {
                announceOrientation(SchematicAndQuillItem.flip(held));
                yield true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_G -> {
                SchematicAndQuillItem.resetTransform(held);
                announceOrientation(SchematicAndQuillItem.getTransform(held));
                yield true;
            }
            default -> false;
        };
        if (handled) {
            syncQuill(player, held);
            // Repaint the target marker right away, so a tap is visible without waiting for the next
            // tick - the keys are meant to be pressed repeatedly while aiming.
            refreshQuillPreview();
        }
    }

    /** Hands the given quill's mode and orientation to the server, which owns the real copy. */
    public static void syncQuill(final LocalPlayer player, final ItemStack held) {
        final int hand = SchematicAndQuillItem.handOf(player);
        if (hand < 0) {
            return;
        }
        com.schematicannon.network.ModNetwork.sendQuillState(hand,
                SchematicAndQuillItem.getMode(held) == SchematicAndQuillItem.Mode.TARGET ? 1 : 0,
                SchematicAndQuillItem.getTransform(held).encode());
    }

    /** Overlay (action bar) feedback, so repeated taps do not fill up the chat. */
    private static void announceOrientation(final SchematicTransform transform) {
        Minecraft.getInstance().gui.setOverlayMessage(Component.translatable(
                "schematicannon.quill.orientation", transform.describe(), transform.compass()), false);
    }

    /** The quill in the main or off hand, or null when neither holds one. */
    @Nullable
    private static ItemStack heldQuill(final LocalPlayer player) {
        final ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof SchematicAndQuillItem) {
            return main;
        }
        final ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof SchematicAndQuillItem ? off : null;
    }
}
