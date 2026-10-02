package com.schematicannon.client;

import com.schematicannon.blockentity.SchematicTableBlockEntity;
import com.schematicannon.menu.SchematicTableMenu;
import com.schematicannon.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class SchematicTableScreen extends AbstractContainerScreen<SchematicTableMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT_BORDER = 0xFF8B8B8B;
    private static final int SLOT_INNER = 0xFF373737;
    private static final int TEXT = 0xFF404040;

    private EditBox nameField;
    private String pendingName = "";

    public SchematicTableScreen(SchematicTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 200);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        this.nameField = new EditBox(this.font, x + 20, y + 56, 136, 16, Component.translatable("schematicannon.field.name"));
        this.nameField.setMaxLength(64);
        this.nameField.setHint(Component.translatable("schematicannon.field.name"));
        this.nameField.setValue(pendingName);
        this.addRenderableWidget(this.nameField);

        this.addRenderableWidget(Button.builder(Component.translatable("schematicannon.save"),
                b -> save()).bounds(x + 20, y + 78, 136, 20).build());
    }

    private void save() {
        String raw = this.nameField == null ? "" : this.nameField.getValue().trim();
        if (raw.isEmpty()) {
            return;
        }
        pendingName = com.schematicannon.schematic.Schematic.sanitizeFileName(raw);
        this.nameField.setValue(pendingName);
        ModNetwork.sendSave(this.menu.blockPos(), pendingName);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.nameField != null) {
            pendingName = this.nameField.getValue();
        }
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

        graphics.text(this.font, Component.translatable("schematicannon.table.quill"), x + 78, y + 20, TEXT, false);
        graphics.text(this.font, statusComponent(), x + 20, y + 44, TEXT, false);
        graphics.text(this.font, Component.translatable("schematicannon.table.note"), x + 20, y + 104, TEXT, false);
    }

    private Component statusComponent() {
        int status = this.menu.getSyncedData().get(SchematicTableBlockEntity.DATA_STATUS);
        return switch (status) {
            case SchematicTableBlockEntity.RESULT_NO_QUILL -> Component.translatable("schematicannon.result.no_quill");
            case SchematicTableBlockEntity.RESULT_NO_REGION -> Component.translatable("schematicannon.result.no_region");
            case SchematicTableBlockEntity.RESULT_ERROR -> Component.translatable("schematicannon.result.error");
            case SchematicTableBlockEntity.RESULT_NO_NAME -> Component.translatable("schematicannon.result.no_name");
            case SchematicTableBlockEntity.RESULT_OK -> Component.translatable("schematicannon.result.ok");
            default -> Component.translatable("schematicannon.result.hint");
        };
    }
}
