package com.schematicannon;

import com.schematicannon.block.SchematicTableBlock;
import com.schematicannon.block.SchematicannonBlock;
import com.schematicannon.blockentity.SchematicTableBlockEntity;
import com.schematicannon.blockentity.SchematicannonBlockEntity;
import com.schematicannon.item.MaterialListItem;
import com.schematicannon.item.SchematicAndQuillItem;
import com.schematicannon.menu.QuillMenu;
import com.schematicannon.menu.SchematicTableMenu;
import com.schematicannon.menu.SchematicannonMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SchematicannonMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SchematicannonMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, SchematicannonMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(BuiltInRegistries.MENU, SchematicannonMod.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, SchematicannonMod.MODID);

    // ------------------------------------------------------------------- blocks

    public static final DeferredHolder<Block, SchematicannonBlock> SCHEMATICANNON =
            BLOCKS.registerBlock("schematicannon", SchematicannonBlock::new, () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.COPPER)
                    .noOcclusion());

    public static final DeferredHolder<Item, BlockItem> SCHEMATICANNON_ITEM =
            ITEMS.registerSimpleBlockItem("schematicannon", SCHEMATICANNON);

    public static final DeferredHolder<Block, SchematicTableBlock> SCHEMATIC_TABLE =
            BLOCKS.registerBlock("schematic_table", SchematicTableBlock::new, () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion());

    public static final DeferredHolder<Item, BlockItem> SCHEMATIC_TABLE_ITEM =
            ITEMS.registerSimpleBlockItem("schematic_table", SCHEMATIC_TABLE);

    // -------------------------------------------------------------------- items

    public static final DeferredHolder<Item, SchematicAndQuillItem> SCHEMATIC_AND_QUILL =
            ITEMS.registerItem("schematic_and_quill", SchematicAndQuillItem::new);

    public static final DeferredHolder<Item, MaterialListItem> MATERIAL_LIST =
            ITEMS.registerItem("material_list", MaterialListItem::new);

    // ----------------------------------------------------------- block entities

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SchematicannonBlockEntity>> SCHEMATICANNON_BE =
            BLOCK_ENTITY_TYPES.register("schematicannon",
                    () -> new BlockEntityType<>(SchematicannonBlockEntity::new, SCHEMATICANNON.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SchematicTableBlockEntity>> SCHEMATIC_TABLE_BE =
            BLOCK_ENTITY_TYPES.register("schematic_table",
                    () -> new BlockEntityType<>(SchematicTableBlockEntity::new, SCHEMATIC_TABLE.get()));

    // -------------------------------------------------------------------- menus

    public static final DeferredHolder<MenuType<?>, MenuType<SchematicannonMenu>> SCHEMATICANNON_MENU =
            MENU_TYPES.register("schematicannon", () -> new MenuType<>(SchematicannonMenu::createClientMenu,
                    FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<SchematicTableMenu>> SCHEMATIC_TABLE_MENU =
            MENU_TYPES.register("schematic_table", () -> new MenuType<>(SchematicTableMenu::createClientMenu,
                    FeatureFlags.VANILLA_SET));

    /** Item held menu for the blueprint and quill; opened by right clicking the air. */
    public static final DeferredHolder<MenuType<?>, MenuType<QuillMenu>> QUILL_MENU =
            MENU_TYPES.register("quill", () -> new MenuType<>(QuillMenu::createClientMenu,
                    FeatureFlags.VANILLA_SET));

    // ------------------------------------------------------------- creative tab

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_TABS.register("schematicannon", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.schematicannon"))
                    .icon(() -> new ItemStack(SCHEMATICANNON_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(SCHEMATICANNON_ITEM.get());
                        output.accept(SCHEMATIC_TABLE_ITEM.get());
                        output.accept(SCHEMATIC_AND_QUILL.get());
                        output.accept(MATERIAL_LIST.get());
                    })
                    .build());

    private ModRegistry() {
    }
}
