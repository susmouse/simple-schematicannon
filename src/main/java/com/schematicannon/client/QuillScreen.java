package com.schematicannon.client;

import com.schematicannon.item.SchematicAndQuillItem;
import com.schematicannon.menu.QuillMenu;
import com.schematicannon.schematic.SchematicTransform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The quill control panel.
 *
 * <p>Two rows of controls: the mode on top, the print orientation below. Every button is a menu
 * button, so the server performs the change - the client only reads back the same NBT to draw the
 * labels, which means the panel can never disagree with what the cannon will actually use.
 */
public class QuillScreen extends AbstractContainerScreen<QuillMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 200;
    /**
     * Where the player's inventory starts, and where its label goes. These two are tied together:
     * {@code inventoryLabelY} uses the vanilla {@code imageHeight - 94} convention, and with
     * {@code HEIGHT = 200} that lands exactly one line above the first row at 118.
     */
    private static final int SLOTS_TOP = 118;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT_BORDER = 0xFF8B8B8B;
    private static final int SLOT_INNER = 0xFF373737;
    private static final int TEXT = 0xFF404040;
    private static final int TEXT_DIM = 0xFF6B6B6B;
    private static final int ACTIVE = 0xFF2E7D32;
    private static final int COMPASS = 0xFF1A4E8A;

    private Button selectButton;
    private Button targetButton;
    private Button flipButton;

    public QuillScreen(QuillMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        this.selectButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("schematicannon.quill.menu.mode_select"),
                        b -> send(QuillMenu.BUTTON_MODE_SELECT))
                .bounds(x + 8, y + 30, 76, 20).build());

        this.targetButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("schematicannon.quill.menu.mode_target"),
                        b -> send(QuillMenu.BUTTON_MODE_TARGET))
                .bounds(x + 92, y + 30, 76, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("schematicannon.quill.menu.rotate_left"),
                        b -> send(QuillMenu.BUTTON_ROTATE_LEFT))
                .bounds(x + 8, y + 62, 50, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("schematicannon.quill.menu.rotate_right"),
                        b -> send(QuillMenu.BUTTON_ROTATE_RIGHT))
                .bounds(x + 62, y + 62, 50, 20).build());

        this.flipButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("schematicannon.quill.menu.flip"),
                        b -> send(QuillMenu.BUTTON_FLIP))
                .bounds(x + 116, y + 62, 52, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("schematicannon.quill.menu.reset"),
                        b -> send(QuillMenu.BUTTON_RESET))
                .bounds(x + 8, y + 86, 160, 16).build());
    }

    private void send(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshLabels();
    }

    /** The client holds the same NBT the server writes, so the labels are simply read back. */
    private void refreshLabels() {
        SchematicTransform transform = currentTransform();
        if (this.flipButton != null) {
            this.flipButton.setMessage(Component.translatable(
                    transform.mirrored() ? "schematicannon.quill.menu.flip_on"
                            : "schematicannon.quill.menu.flip_off"));
        }
        if (this.selectButton != null) {
            this.selectButton.active = currentMode() != SchematicAndQuillItem.Mode.SELECT;
        }
        if (this.targetButton != null) {
            this.targetButton.active = currentMode() != SchematicAndQuillItem.Mode.TARGET;
        }
    }

    private SchematicAndQuillItem.Mode currentMode() {
        return SchematicAndQuillItem.getMode(heldQuill());
    }

    private SchematicTransform currentTransform() {
        return SchematicAndQuillItem.getTransform(heldQuill());
    }

    private net.minecraft.world.item.ItemStack heldQuill() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        net.minecraft.world.item.ItemStack main = this.minecraft.player.getMainHandItem();
        if (main.getItem() instanceof SchematicAndQuillItem) {
            return main;
        }
        net.minecraft.world.item.ItemStack off = this.minecraft.player.getOffhandItem();
        return off.getItem() instanceof SchematicAndQuillItem ? off : net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        graphics.fill(x, y, x + this.imageWidth, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, 0xFF555555);

        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BORDER);
            graphics.fill(sx, sy, sx + 16, sy + 16, SLOT_INNER);
        }

        // Section captions. The mode row and the orientation row are visually separate so it is
        // obvious which buttons belong together.
        graphics.text(this.font, Component.translatable("schematicannon.quill.menu.mode_title"),
                x + 8, y + 19, TEXT, false);
        graphics.text(this.font, Component.translatable("schematicannon.quill.menu.orient_title"),
                x + 8, y + 51, TEXT, false);

        SchematicTransform transform = currentTransform();
        Component value = Component.translatable("schematicannon.quill.menu.orient_value",
                transform.describe(), transform.compass());
        int w = this.font.width(value);
        graphics.text(this.font, value, x + this.imageWidth - 8 - w, y + 51, COMPASS, false);

        // Every action below takes effect on the cannon the moment the target is applied, so spell
        // out the one rule that matters: changing orientation restarts a running print.
        graphics.text(this.font, Component.translatable("schematicannon.quill.menu.note"),
                x + 8, y + 104, TEXT_DIM, false);
    }
}
