package com.schematicannon.client;

import com.schematicannon.item.MaterialListItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Plain client side screen that shows what a material list item recorded. */
public class MaterialListScreen extends Screen {

    private static final int WIDTH = 232;
    private static final int HEIGHT = 232;
    private static final int ROWS_PER_PAGE = 9;
    private static final int ROW_HEIGHT = 16;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_EDGE = 0xFF8B8B8B;
    private static final int ROW_ALT = 0xFFB4B4B4;
    private static final int TEXT = 0xFF404040;
    private static final int TEXT_DIM = 0xFF6B6B6B;
    private static final int OK = 0xFF2E7D32;
    private static final int MISSING = 0xFFB71C1C;

    private final List<MaterialListItem.Entry> entries;
    private final String schematicName;
    private int page;

    private MaterialListScreen(ItemStack stack) {
        super(Component.translatable("schematicannon.list.title"));
        this.entries = new ArrayList<>(MaterialListItem.read(stack));
        this.entries.sort(Comparator
                .comparing(MaterialListItem.Entry::complete)
                .thenComparingInt(e -> -e.required()));
        this.schematicName = MaterialListItem.getSchematicName(stack);
    }

    /** Only ever called from the client dist. */
    public static void open(ItemStack stack) {
        Minecraft.getInstance().setScreen(new MaterialListScreen(stack));
    }

    // -------------------------------------------------------------------- init

    @Override
    protected void init() {
        super.init();
        int left = left();
        int top = top();

        this.addRenderableWidget(Button.builder(Component.translatable("schematicannon.list.prev"),
                b -> flipPage(-1)).bounds(left + 8, top + HEIGHT - 22, 60, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("schematicannon.list.next"),
                b -> flipPage(1)).bounds(left + 72, top + HEIGHT - 22, 60, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("schematicannon.list.close"),
                b -> this.onClose()).bounds(left + WIDTH - 68, top + HEIGHT - 22, 60, 18).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void flipPage(int delta) {
        int pages = pageCount();
        if (pages <= 1) {
            return;
        }
        this.page = Math.floorMod(this.page + delta, pages);
    }

    private int pageCount() {
        return Math.max(1, (entries.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();

        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        graphics.fill(left, top, left + WIDTH, top + 1, 0xFFFFFFFF);
        graphics.fill(left, top + HEIGHT - 1, left + WIDTH, top + HEIGHT, 0xFF555555);
        graphics.fill(left, top, left + 1, top + HEIGHT, PANEL_EDGE);
        graphics.fill(left + WIDTH - 1, top, left + WIDTH, top + HEIGHT, PANEL_EDGE);

        graphics.text(this.font, Component.translatable("schematicannon.list.title"), left + 8, top + 8, TEXT, false);
        graphics.text(this.font, Component.translatable("schematicannon.list.schematic",
                        schematicName.isEmpty() ? Component.translatable("schematicannon.none")
                                : Component.literal(schematicName)),
                left + 8, top + 24, TEXT_DIM, false);

        if (entries.isEmpty()) {
            graphics.text(this.font, Component.translatable("schematicannon.list.empty"), left + 8, top + 46, TEXT, false);
            return;
        }

        int have = 0;
        for (MaterialListItem.Entry entry : entries) {
            have += Math.min(entry.required(), entry.available());
        }
        int total = MaterialListItem.totalRequired(entries);
        graphics.text(this.font, Component.translatable("schematicannon.list.summary", have, total,
                        entries.size()), left + 8, top + 38, TEXT, false);

        int start = this.page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE && start + row < entries.size(); row++) {
            MaterialListItem.Entry entry = entries.get(start + row);
            int y = top + 52 + row * ROW_HEIGHT;
            if (row % 2 == 1) {
                graphics.fill(left + 8, y - 1, left + WIDTH - 8, y + ROW_HEIGHT - 1, ROW_ALT);
            }

            ItemStack icon = new ItemStack(entry.item());
            graphics.item(icon, left + 10, y - 2);

            Component name = icon.getHoverName();
            graphics.text(this.font, trim(name), left + 30, y + 1, TEXT, false);

            Component amount = entry.complete()
                    ? Component.translatable("schematicannon.list.done", entry.available(), entry.required())
                    : Component.translatable("schematicannon.list.missing", entry.available(), entry.required());
            int w = this.font.width(amount);
            graphics.text(this.font, amount, left + WIDTH - 10 - w, y + 1, entry.complete() ? OK : MISSING, false);
        }

        Component pageLabel = Component.translatable("schematicannon.list.page", this.page + 1, pageCount());
        graphics.text(this.font, pageLabel, left + WIDTH - 10 - this.font.width(pageLabel), top + 38, TEXT_DIM, false);
    }

    private Component trim(Component component) {
        String plain = component.getString();
        int maxWidth = WIDTH - 30 - 74;
        if (this.font.width(plain) <= maxWidth) {
            return component;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < plain.length(); i++) {
            if (this.font.width(sb.toString() + "...") > maxWidth) {
                break;
            }
            sb.append(plain.charAt(i));
        }
        return Component.literal(sb + "...");
    }

    private int left() {
        return (this.width - WIDTH) / 2;
    }

    private int top() {
        return (this.height - HEIGHT) / 2;
    }
}
