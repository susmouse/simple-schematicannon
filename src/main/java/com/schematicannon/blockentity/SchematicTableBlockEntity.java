package com.schematicannon.blockentity;

import com.schematicannon.ModRegistry;
import com.schematicannon.item.SchematicAndQuillItem;
import com.schematicannon.schematic.Schematic;
import com.schematicannon.util.IntArrayContainerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;

public class SchematicTableBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_QUILL = 0;
    public static final int CONTAINER_SIZE = 1;

    public static final int DATA_STATUS = 0;
    public static final int DATA_POS_X = 1;
    public static final int DATA_POS_Y = 2;
    public static final int DATA_POS_Z = 3;
    public static final int DATA_SAVED_BLOCKS = 4;
    public static final int DATA_COUNT = 5;

    public static final int RESULT_OK = 0;
    public static final int RESULT_NO_QUILL = 1;
    public static final int RESULT_NO_REGION = 2;
    public static final int RESULT_ERROR = 3;
    public static final int RESULT_NO_NAME = 4;

    private final SimpleContainer inventory = new SimpleContainer(CONTAINER_SIZE);
    private final IntArrayContainerData synced = new IntArrayContainerData(DATA_COUNT);

    /** Last save attempt, exposed so the network handler can report it in the chat. */
    private String lastFileName = "";
    @Nullable
    private String lastErrorDetail;

    public SchematicTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SCHEMATIC_TABLE_BE.get(), pos, state);
        synced.set(DATA_POS_X, pos.getX());
        synced.set(DATA_POS_Y, pos.getY());
        synced.set(DATA_POS_Z, pos.getZ());
    }

    public String getLastFileName() {
        return lastFileName;
    }

    @Nullable
    public String getLastErrorDetail() {
        return lastErrorDetail;
    }

    /** Block count of the most recent successful save. */
    public int getSavedBlocks() {
        return synced.get(DATA_SAVED_BLOCKS);
    }

    public Container getInventory() {
        return inventory;
    }

    public ContainerData getSyncedData() {
        return synced;
    }

    /** Writes the region marked on the quill to a .nbt file. The quill is a reusable tool. */
    public int saveSchematic(String rawName) {
        this.lastFileName = Schematic.sanitizeFileName(rawName);
        this.lastErrorDetail = null;
        int result = trySaveSchematic(rawName);
        synced.set(DATA_STATUS, result);
        setChanged();
        return result;
    }

    private int trySaveSchematic(String rawName) {
        if (level == null) {
            this.lastErrorDetail = "level is not loaded";
            return RESULT_ERROR;
        }
        String fileName = Schematic.sanitizeFileName(rawName);
        this.lastFileName = fileName;
        if (fileName.isEmpty()) {
            return RESULT_NO_NAME;
        }

        ItemStack quill = inventory.getItem(SLOT_QUILL);
        if (quill.isEmpty() || !(quill.getItem() instanceof SchematicAndQuillItem)) {
            return RESULT_NO_QUILL;
        }
        BlockPos a = SchematicAndQuillItem.getFirstPos(quill);
        BlockPos b = SchematicAndQuillItem.getSecondPos(quill);
        if (a == null || b == null) {
            return RESULT_NO_REGION;
        }
        try {
            Path dir = SchematicannonBlockEntity.schematicDirectory().toAbsolutePath().normalize();
            Path file = dir.resolve(fileName).normalize();
            if (!file.startsWith(dir)) {
                this.lastErrorDetail = "file name is not valid: " + fileName;
                return RESULT_ERROR;
            }
            Schematic captured = Schematic.capture(level, a, b, file);
            synced.set(DATA_SAVED_BLOCKS, captured.blockCount());
            setChanged();
            return RESULT_OK;
        } catch (IOException | RuntimeException e) {
            com.schematicannon.SchematicannonMod.LOGGER.warn("Could not save schematic '{}': {}", fileName, e.toString());
            this.lastErrorDetail = e.getMessage() == null ? e.toString() : e.getMessage();
            return RESULT_ERROR;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, inventory.getItems());
        for (int i = 0; i < DATA_COUNT; i++) {
            output.putInt("Sync" + i, synced.get(i));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, inventory.getItems());
        for (int i = 0; i < DATA_COUNT; i++) {
            synced.set(i, input.getIntOr("Sync" + i, synced.get(i)));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.schematic_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new com.schematicannon.menu.SchematicTableMenu(containerId, playerInventory, inventory, synced);
    }
}
