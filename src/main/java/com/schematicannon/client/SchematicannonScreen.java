package com.schematicannon.client;

import com.schematicannon.blockentity.SchematicannonBlockEntity;
import com.schematicannon.menu.SchematicannonMenu;
import com.schematicannon.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

public class SchematicannonScreen extends AbstractContainerScreen<SchematicannonMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT_BORDER = 0xFF8B8B8B;
    private static final int SLOT_INNER = 0xFF373737;
    private static final int BAR_FILL = 0xFF3FB840;
    private static final int FUEL_FILL = 0xFFFF9C1A;
    private static final int TEXT = 0xFF404040;

    private EditBox nameField;
    private Button startStopButton;
    private Button ignoreAirButton;
    private Button replaceButton;
    private Button speedButton;
    private Button skipMissingButton;
    private Button previewButton;
    private Button targetButton;

    private final List<String> files = new ArrayList<>();
    private int fileIndex = 0;
    private String pendingName = "";
    private String loadedName = "";

    public SchematicannonScreen(SchematicannonMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 200, 346);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    // -------------------------------------------------------------------- init

    @Override
    protected void init() {
        super.init();
        ClientHooks.setActiveCannonScreen(this);

        int x = this.leftPos;
        int y = this.topPos;

        this.nameField = new EditBox(this.font, x + 8, y + 50, 100, 16, Component.translatable("schematicannon.field.name"));
        this.nameField.setMaxLength(64);
        this.nameField.setHint(Component.translatable("schematicannon.field.name"));
        this.nameField.setValue(pendingName);
        this.addRenderableWidget(this.nameField);

        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> cycleFile(-1)).bounds(x + 110, y + 48, 18, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleFile(1)).bounds(x + 130, y + 48, 18, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("schematicannon.load"),
                b -> loadTypedName()).bounds(x + 152, y + 48, 40, 20).build());

        this.startStopButton = this.addRenderableWidget(Button.builder(Component.translatable("schematicannon.start"),
                b -> press(SchematicannonMenu.BUTTON_START_STOP)).bounds(x + 8, y + 150, 62, 20).build());
        this.ignoreAirButton = this.addRenderableWidget(Button.builder(Component.empty(),
                b -> press(SchematicannonMenu.BUTTON_IGNORE_AIR)).bounds(x + 72, y + 150, 62, 20).build());
        this.replaceButton = this.addRenderableWidget(Button.builder(Component.empty(),
                b -> press(SchematicannonMenu.BUTTON_REPLACE)).bounds(x + 138, y + 150, 60, 20).build());
        this.speedButton = this.addRenderableWidget(Button.builder(Component.empty(),
                b -> press(SchematicannonMenu.BUTTON_SPEED)).bounds(x + 8, y + 172, 62, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("schematicannon.reset"),
                b -> press(SchematicannonMenu.BUTTON_RESET)).bounds(x + 72, y + 172, 62, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("schematicannon.refresh"),
                b -> ModNetwork.sendRequestFileList(this.menu.blockPos())).bounds(x + 138, y + 172, 60, 20).build());

        this.skipMissingButton = this.addRenderableWidget(Button.builder(Component.empty(),
                b -> press(SchematicannonMenu.BUTTON_SKIP_MISSING)).bounds(x + 8, y + 194, 62, 20).build());
        this.previewButton = this.addRenderableWidget(Button.builder(Component.empty(),
                b -> {
                    SchematicPreview.toggle();
                    refreshLabels();
                }).bounds(x + 72, y + 194, 62, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("schematicannon.material_list"),
                b -> press(SchematicannonMenu.BUTTON_MATERIAL_LIST)).bounds(x + 138, y + 194, 60, 20).build());

        this.targetButton = this.addRenderableWidget(Button.builder(Component.empty(),
                b -> press(SchematicannonMenu.BUTTON_TARGET)).bounds(x + 8, y + 216, 184, 20).build());

        ModNetwork.sendRequestFileList(this.menu.blockPos());
        refreshLabels();
    }

    public void setLoadedName(BlockPos pos, String name) {
        if (!this.menu.blockPos().equals(pos)) {
            return;
        }
        this.loadedName = name == null ? "" : name;
        applyLoadedName();
    }

    /**
     * Pushes {@link #loadedName} into the name field and the file picker.
     *
     * <p>Called from both the file list and the cannon state handler, because those two packets can
     * land in either order and the field starts out blank on every fresh open. Running it from both
     * sides means the remembered schematic is shown no matter which one arrives last.
     */
    private void applyLoadedName() {
        if (this.loadedName.isBlank() || this.nameField == null) {
            return;
        }
        this.pendingName = this.loadedName;
        this.nameField.setValue(this.loadedName);
        int index = this.files.indexOf(this.loadedName);
        if (index >= 0) {
            this.fileIndex = index;
        }
    }

    @Override
    public void removed() {
        ClientHooks.setActiveCannonScreen(null);
        super.removed();
    }

    private void press(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void loadTypedName() {
        String name = this.nameField == null ? "" : this.nameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        pendingName = com.schematicannon.schematic.Schematic.sanitizeFileName(name);
        this.nameField.setValue(pendingName);
        ModNetwork.sendLoad(this.menu.blockPos(), pendingName);
    }

    private void cycleFile(int delta) {
        if (this.files.isEmpty()) {
            return;
        }
        this.fileIndex = Math.floorMod(this.fileIndex + delta, this.files.size());
        pendingName = this.files.get(this.fileIndex);
        if (this.nameField != null) {
            this.nameField.setValue(pendingName);
        }
    }

    public void setAvailableFiles(String joined) {
        this.files.clear();
        if (joined != null && !joined.isEmpty()) {
            for (String line : joined.split("\n")) {
                if (!line.isBlank()) {
                    this.files.add(line);
                }
            }
        }
        if (this.files.isEmpty()) {
            return;
        }
        if (this.fileIndex >= this.files.size()) {
            this.fileIndex = 0;
        }
        // The cannon's remembered schematic always wins over the default first entry.
        if (!this.loadedName.isBlank()) {
            applyLoadedName();
            return;
        }
        // Keep the field in sync only when the user has not typed anything yet.
        if (this.nameField != null && (this.nameField.getValue().isBlank()
                || !this.files.contains(this.nameField.getValue().trim()))) {
            pendingName = this.files.get(this.fileIndex);
            this.nameField.setValue(pendingName);
        }
    }

    // ------------------------------------------------------------------ render

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

        int total = this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_TOTAL);
        int placed = this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_PLACED);

        // progress bar
        graphics.fill(x + 8, y + 138, x + 192, y + 144, SLOT_INNER);
        if (total > 0) {
            int w = Math.max(1, Math.min(184, placed * 184 / total));
            graphics.fill(x + 8, y + 138, x + 8 + w, y + 144, BAR_FILL);
        }

        // fuel bar under the fuel slot
        graphics.fill(x + 174, y + 44, x + 192, y + 48, SLOT_INNER);
        int fuel = Math.max(0, Math.min(100, this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_FUEL)));
        if (fuel > 0) {
            graphics.fill(x + 174, y + 44, x + 174 + (18 * fuel / 100), y + 48, FUEL_FILL);
        }

        graphics.text(this.font, Component.translatable("schematicannon.progress", placed, total), x + 8, y + 126, TEXT, false);
        graphics.text(this.font, statusComponent(), x + 8, y + 118, TEXT, false);
        graphics.text(this.font, Component.translatable("schematicannon.current_file",
                        this.loadedName.isBlank() ? Component.translatable("schematicannon.none") : Component.literal(this.loadedName)),
                x + 8, y + 82, TEXT, false);
        int fileCount = this.files.size();
        if (fileCount > 0) {
            graphics.text(this.font, Component.translatable("schematicannon.file_count", this.fileIndex + 1, fileCount),
                    x + 8, y + 68, TEXT, false);
        }

        drawTargetHint(graphics, x, y);
    }

    private Component statusComponent() {
        int status = this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_STATUS);
        Component base = switch (status) {
            case SchematicannonBlockEntity.STATUS_NO_SCHEMATIC -> Component.translatable("schematicannon.status.no_schematic");
            case SchematicannonBlockEntity.STATUS_RUNNING -> Component.translatable("schematicannon.status.running");
            case SchematicannonBlockEntity.STATUS_PAUSED -> Component.translatable("schematicannon.status.paused");
            case SchematicannonBlockEntity.STATUS_NO_FUEL -> Component.translatable("schematicannon.status.no_fuel");
            case SchematicannonBlockEntity.STATUS_MISSING_BLOCKS -> Component.translatable("schematicannon.status.missing_blocks");
            case SchematicannonBlockEntity.STATUS_FINISHED -> Component.translatable("schematicannon.status.finished");
            default -> Component.translatable("schematicannon.status.ready");
        };
        // Started by a creative player: nothing is consumed, say so instead of looking broken.
        if ((this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_OPTIONS)
                & SchematicannonBlockEntity.OPT_FREE_RUN) != 0) {
            return base.copy().append(Component.translatable("schematicannon.status.creative_tag"));
        }
        return base;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.nameField != null && !this.nameField.getValue().equals(pendingName)) {
            pendingName = this.nameField.getValue();
        }
        refreshLabels();
    }

    private void refreshLabels() {
        if (this.startStopButton == null) {
            return;
        }
        int status = this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_STATUS);
        boolean running = status == SchematicannonBlockEntity.STATUS_RUNNING;
        this.startStopButton.setMessage(Component.translatable(running ? "schematicannon.stop" : "schematicannon.start"));

        boolean ignoreAir = (this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_OPTIONS)
                & SchematicannonBlockEntity.OPT_IGNORE_AIR) != 0;
        this.ignoreAirButton.setMessage(Component.translatable("schematicannon.ignore_air")
                .append(Component.literal(ignoreAir ? " [x]" : " [ ]")));

        boolean replace = (this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_OPTIONS)
                & SchematicannonBlockEntity.OPT_REPLACE_BLOCKS) != 0;
        this.replaceButton.setMessage(Component.translatable("schematicannon.replace_blocks")
                .append(Component.literal(replace ? " [x]" : " [ ]")));

        int interval = Math.max(1, this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_INTERVAL));
        this.speedButton.setMessage(Component.translatable("schematicannon.speed", interval));

        boolean skipMissing = (this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_OPTIONS)
                & SchematicannonBlockEntity.OPT_SKIP_MISSING) != 0;
        this.skipMissingButton.setMessage(Component.translatable("schematicannon.skip_missing")
                .append(Component.literal(skipMissing ? " [x]" : " [ ]")));

        if (this.previewButton != null) {
            this.previewButton.setMessage(Component.translatable("schematicannon.preview")
                    .append(Component.literal(SchematicPreview.isEnabled() ? " [x]" : " [ ]")));
        }

        if (this.targetButton != null) {
            boolean aimed = this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_TARGET_SET) != 0;
            if (aimed) {
                int tx = this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_TARGET_X);
                int ty = this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_TARGET_Y);
                int tz = this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_TARGET_Z);
                this.targetButton.setMessage(
                        Component.translatable("schematicannon.target_clear", tx, ty, tz));
            } else {
                this.targetButton.setMessage(Component.translatable("schematicannon.target_default"));
            }
        }
    }

    /** One line under the target button, so the quill flow is discoverable from the GUI itself. */
    private void drawTargetHint(GuiGraphicsExtractor graphics, int x, int y) {
        boolean aimed = this.menu.getSyncedData().get(SchematicannonBlockEntity.DATA_TARGET_SET) != 0;
        graphics.text(this.font, Component.translatable(aimed
                        ? "schematicannon.target_hint_set"
                        : "schematicannon.target_hint_unset"),
                x + 8, y + 238, TEXT, false);
    }
}
