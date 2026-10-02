package com.schematicannon.client;

import com.schematicannon.ModRegistry;
import com.schematicannon.item.SchematicAndQuillItem;
import com.schematicannon.menu.SchematicannonMenu;
import com.schematicannon.menu.SchematicTableMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

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

    public static void acceptPreview(BlockPos pos, long[] packed, int[] states) {
        SchematicPreview.set(pos, packed, states);
    }

    /** Draws the pending structure of every loaded cannon plus the live quill selection. */
    public static void onSubmitGeometry(final SubmitCustomGeometryEvent event) {
        SchematicPreview.render(event);
    }

    /**
     * The quill selection is local, cheap state, so it is rebuilt once per client tick from the
     * player's own hands instead of being synced by the server.
     */
    public static void onClientTick(final ClientTickEvent.Post event) {
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
        if (SchematicAndQuillItem.getMode(held) == SchematicAndQuillItem.Mode.TARGET) {
            color = COLOR_TARGET_MODE;
        }
        SchematicPreview.tickSelection(true, first, head, color);
    }

    public static void onLoggedOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        SchematicPreview.clearAll();
    }
}
