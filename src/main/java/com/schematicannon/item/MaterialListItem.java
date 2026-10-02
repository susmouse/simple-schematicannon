package com.schematicannon.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Written by the schematic cannon when the "material list" button is pressed. Right clicking it
 * opens a screen listing every material the print job still needs and how much of it is already
 * sitting in the cannon or in the chests next to it.
 */
public class MaterialListItem extends Item {

    public static final String KEY_SCHEMATIC = "ListSchematic";
    public static final String KEY_TOTAL = "ListTotal";
    public static final String KEY_ENTRIES = "ListEntries";
    public static final String KEY_ID = "id";
    public static final String KEY_REQUIRED = "req";
    public static final String KEY_AVAILABLE = "have";

    /** Hard cap so a giant schematic cannot produce an oversized item. */
    public static final int MAX_ENTRIES = 300;

    /** One line of the list. */
    public record Entry(Item item, int required, int available) {
        public boolean complete() {
            return available >= required;
        }
    }

    public MaterialListItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    // ---------------------------------------------------------------------- use

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()
                && net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            com.schematicannon.client.MaterialListScreen.open(stack);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        String name = getSchematicName(stack);
        List<Entry> entries = read(stack);
        if (entries.isEmpty()) {
            lines.accept(Component.translatable("schematicannon.tooltip.material_list_empty"));
            return;
        }
        int total = totalRequired(entries);
        int have = 0;
        for (Entry entry : entries) {
            have += Math.min(entry.required(), entry.available());
        }
        lines.accept(Component.translatable("schematicannon.tooltip.material_list",
                name.isEmpty() ? Component.translatable("schematicannon.none") : Component.literal(name),
                entries.size(), have, total));
    }

    // -------------------------------------------------------------------- data

    public static void write(ItemStack stack, @Nullable String schematicName, List<Entry> entries) {
        List<Entry> capped = entries.size() > MAX_ENTRIES ? new ArrayList<>(entries.subList(0, MAX_ENTRIES)) : entries;
        int total = 0;
        for (Entry entry : capped) {
            total += entry.required();
        }
        final int finalTotal = total;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(KEY_SCHEMATIC, schematicName == null ? "" : schematicName);
            tag.putInt(KEY_TOTAL, finalTotal);
            ListTag list = new ListTag();
            for (Entry entry : capped) {
                CompoundTag line = new CompoundTag();
                line.putString(KEY_ID, BuiltInRegistries.ITEM.getKey(entry.item()).toString());
                line.putInt(KEY_REQUIRED, entry.required());
                line.putInt(KEY_AVAILABLE, entry.available());
                list.add(line);
            }
            tag.put(KEY_ENTRIES, list);
        });
    }

    public static List<Entry> read(ItemStack stack) {
        List<Entry> out = new ArrayList<>();
        if (stack.isEmpty()) {
            return out;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = tag.getListOrEmpty(KEY_ENTRIES);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag line = list.getCompoundOrEmpty(i);
            String id = line.getString(KEY_ID).orElse("");
            Item item = itemById(id);
            if (item == null) {
                continue;
            }
            out.add(new Entry(item,
                    Math.max(0, line.getIntOr(KEY_REQUIRED, 0)),
                    Math.max(0, line.getIntOr(KEY_AVAILABLE, 0))));
        }
        return out;
    }

    public static String getSchematicName(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getString(KEY_SCHEMATIC).orElse("");
    }

    public static int totalRequired(List<Entry> entries) {
        int total = 0;
        for (Entry entry : entries) {
            total += entry.required();
        }
        return total;
    }

    @Nullable
    private static Item itemById(String id) {
        if (id.isEmpty()) {
            return null;
        }
        try {
            return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        } catch (Exception e) {
            return null;
        }
    }
}
