package com.schematicannon.blockentity;

import com.schematicannon.ModRegistry;
import com.schematicannon.schematic.Schematic;
import com.schematicannon.schematic.SchematicTransform;
import com.schematicannon.util.IntArrayContainerData;
import com.schematicannon.util.Messages;
import com.schematicannon.util.NeighbourInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SchematicannonBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_FUEL = 0;
    public static final int MATERIAL_SLOTS = 27;
    public static final int MATERIAL_START = 1;
    public static final int CONTAINER_SIZE = MATERIAL_START + MATERIAL_SLOTS;

    /** Fuel ticks burned per placed block. */
    public static final int FUEL_PER_BLOCK = 8;

    /** Ticks an identical chat message is suppressed for. */
    public static final int NOTIFY_COOLDOWN = 200;

    public static final int DATA_PLACED = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_FUEL = 3;
    public static final int DATA_OPTIONS = 4;
    public static final int DATA_INTERVAL = 5;
    public static final int DATA_POS_X = 6;
    public static final int DATA_POS_Y = 7;
    public static final int DATA_POS_Z = 8;
    public static final int DATA_NAME_HASH = 9;
    public static final int DATA_TARGET_SET = 10;
    public static final int DATA_TARGET_X = 11;
    public static final int DATA_TARGET_Y = 12;
    public static final int DATA_TARGET_Z = 13;
    /** Encoded {@link SchematicTransform}: rotation in the low two bits, mirror as bit 2. */
    public static final int DATA_TRANSFORM = 14;
    public static final int DATA_COUNT = 15;

    public static final int STATUS_NO_SCHEMATIC = 0;
    public static final int STATUS_READY = 1;
    public static final int STATUS_RUNNING = 2;
    public static final int STATUS_PAUSED = 3;
    public static final int STATUS_NO_FUEL = 4;
    public static final int STATUS_MISSING_BLOCKS = 5;
    public static final int STATUS_FINISHED = 6;

    public static final int OPT_IGNORE_AIR = 1;
    public static final int OPT_REPLACE_BLOCKS = 2;
    /**
     * Read only mirror of {@link #creativeOperator}, so the GUI can show that the current run pays
     * nothing. It follows the game mode of whoever pressed start, it is never a player set option.
     */
    public static final int OPT_FREE_RUN = 4;
    /** Keep printing when a material runs out instead of stopping the whole job. */
    public static final int OPT_SKIP_MISSING = 8;

    private final SimpleContainer inventory = new SimpleContainer(CONTAINER_SIZE);
    private final IntArrayContainerData synced = new IntArrayContainerData(DATA_COUNT);

    @Nullable
    private Schematic schematic;
    @Nullable
    private String schematicName;
    /** Where to print, set with the quill in target mode. Null means "one block above the cannon". */
    @Nullable
    private BlockPos printTarget;
    /** Rotation and mirror applied to the schematic while printing. */
    private SchematicTransform transform = SchematicTransform.IDENTITY;

    private int cursor;
    private int placedCount;
    private int blockedCount;
    /** Positions skipped because the material was not there and skipping is enabled. */
    private int missingCount;
    /** True while the current run was started by a creative player: nothing is consumed. */
    private boolean creativeOperator;
    /** Missing materials already reported during this run, one message per item type. */
    private final Set<Item> missingReported = new HashSet<>();
    private int fuelTicks;
    private int maxFuelTicks = 1;
    private boolean running;
    private int cooldown;

    // ------------------------------------------------------------ chat feedback

    /** Item whose absence stopped the printer. */
    @Nullable
    private Item missingItem;
    /** Item sitting in the fuel slot that cannot be burned. */
    @Nullable
    private ItemStack invalidFuel;
    private boolean finishNotified;
    /** Set after loading from disk so the schematic is re-read on the first tick. */
    private boolean pendingRestore;
    private String lastNotifyKey = "";
    private long lastNotifyTime = Long.MIN_VALUE;

    public SchematicannonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        synced.set(DATA_OPTIONS, OPT_IGNORE_AIR | OPT_SKIP_MISSING);
        synced.set(DATA_INTERVAL, 4);
        synced.set(DATA_STATUS, STATUS_NO_SCHEMATIC);
        synced.set(DATA_POS_X, pos.getX());
        synced.set(DATA_POS_Y, pos.getY());
        synced.set(DATA_POS_Z, pos.getZ());
    }

    public SchematicannonBlockEntity(BlockPos pos, BlockState state) {
        this(ModRegistry.SCHEMATICANNON_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- inventory

    public Container getInventory() {
        return inventory;
    }

    public ContainerData getSyncedData() {
        return synced;
    }

    // ----------------------------------------------------------------- options

    public boolean isIgnoringAir() {
        return (synced.get(DATA_OPTIONS) & OPT_IGNORE_AIR) != 0;
    }

    public boolean isReplacingBlocks() {
        return (synced.get(DATA_OPTIONS) & OPT_REPLACE_BLOCKS) != 0;
    }

    /** Whether the run currently in progress was started by a creative player. */
    public boolean isCreativeRun() {
        return creativeOperator;
    }

    public boolean isFreeRunSynced() {
        return (synced.get(DATA_OPTIONS) & OPT_FREE_RUN) != 0;
    }

    public boolean isSkippingMissing() {
        return (synced.get(DATA_OPTIONS) & OPT_SKIP_MISSING) != 0;
    }

    public void setOption(int mask, boolean value) {
        int opts = synced.get(DATA_OPTIONS);
        synced.set(DATA_OPTIONS, value ? (opts | mask) : (opts & ~mask));
        setChanged();
    }

    public int getInterval() {
        return Math.max(1, synced.get(DATA_INTERVAL));
    }

    public void cycleInterval() {
        int v = getInterval();
        synced.set(DATA_INTERVAL, v >= 8 ? 1 : v >= 4 ? 8 : v >= 2 ? 4 : 2);
        setChanged();
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean value) {
        setRunning(value, null);
    }

    public void setRunning(boolean value, @Nullable Player player) {
        boolean before = this.running;
        if (value && schematic == null) {
            notify(Component.translatable("schematicannon.msg.no_schematic"), "start_fail:no_schematic", player);
        } else if (value && cursor >= (schematic == null ? 0 : schematic.blockCount())) {
            notify(Component.translatable("schematicannon.msg.already_finished"), "start_fail:finished", player);
        }
        this.running = value && schematic != null && cursor < schematic.blockCount();
        this.cooldown = 0;
        if (value) {
            // No creative button: whoever presses start decides. Creative players never pay.
            this.creativeOperator = player != null && player.isCreative();
            setOption(OPT_FREE_RUN, creativeOperator);
        }
        updateStatus();
        setChanged();
        if (this.running != before) {
            if (this.running) {
                this.finishNotified = false;
                this.missingReported.clear();
                notify(Component.translatable(creativeOperator
                                ? "schematicannon.msg.started_creative"
                                : "schematicannon.msg.started", displayName()),
                        "start", player);
            } else {
                notify(Component.translatable("schematicannon.msg.stopped", placedCount), "stop", player);
            }
        }
    }

    @Nullable
    public String getSchematicName() {
        return schematicName;
    }

    // ------------------------------------------------------------- schematic io

    /** Loads a schematic from the server side `schematics` folder and resets the print job. */
    public void requestSchematic(String name) {
        requestSchematic(name, null, false);
    }

    public void requestSchematic(String name, @Nullable Player requester) {
        requestSchematic(name, requester, false);
    }

    /**
     * @param keepNameOnFailure when restoring after a reload, a missing file must not wipe the remembered
     *                          name - otherwise the cannon silently forgets what it was printing every
     *                          time the world loads and the GUI comes up blank.
     */
    private void requestSchematic(String name, @Nullable Player requester, boolean keepNameOnFailure) {
        if (level == null) {
            return;
        }
        com.schematicannon.network.ModNetwork.LoadResult result =
                com.schematicannon.network.ModNetwork.loadServerSchematic(name,
                        level.registryAccess().lookupOrThrow(Registries.BLOCK));
        if (result.failed()) {
            loadSchematic(null, keepNameOnFailure ? name : null);
            if (requester != null) {
                notify(Component.translatable("schematicannon.msg.load_failed", name,
                                Messages.loadErrorReason(result.error())),
                        "load_fail:" + name + ":" + result.error(), requester);
            }
            return;
        }
        Schematic loaded = result.schematic();
        loadSchematic(loaded, name);
        notify(Component.translatable("schematicannon.msg.loaded", name, loaded.blockCount(),
                        loaded.sizeX(), loaded.sizeY(), loaded.sizeZ()),
                "load_ok:" + name, requester);
        if (loaded.blockCount() > com.schematicannon.network.ModNetwork.PREVIEW_MAX_BLOCKS) {
            notify(Component.translatable("schematicannon.msg.preview_truncated",
                            com.schematicannon.network.ModNetwork.PREVIEW_MAX_BLOCKS),
                    "preview_truncated:" + name, requester);
        }
    }

    @Nullable
    public Schematic currentSchematic() {
        return schematic;
    }

    public void loadSchematic(@Nullable Schematic loaded, @Nullable String name) {
        this.schematic = loaded;
        this.schematicName = name;
        this.cursor = 0;
        this.placedCount = 0;
        this.blockedCount = 0;
        this.missingCount = 0;
        this.running = false;
        this.cooldown = 0;
        this.finishNotified = false;
        this.missingItem = null;
        this.missingReported.clear();
        synced.set(DATA_TOTAL, loaded == null ? 0 : loaded.blockCount());
        synced.set(DATA_PLACED, 0);
        synced.set(DATA_NAME_HASH, name == null ? 0 : Math.floorMod(name.hashCode(), 1 << 30));
        updateStatus();
        setChanged();
        broadcastPreview();
    }

    /** Pushes the ghost preview of the pending blocks to every client watching this cannon. */
    private void broadcastPreview() {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            com.schematicannon.network.ModNetwork.broadcastPreview(
                    server, getBlockPos(), schematic, origin(), transform);
        }
    }

    public void resetProgress() {
        this.cursor = 0;
        this.placedCount = 0;
        this.blockedCount = 0;
        this.missingCount = 0;
        this.running = false;
        this.finishNotified = false;
        this.missingItem = null;
        this.missingReported.clear();
        synced.set(DATA_PLACED, 0);
        updateStatus();
        setChanged();
    }

    /** Re-reads the schematic after a chunk/world reload, once. */
    private void restoreIfNeeded() {
        if (!pendingRestore || level == null || schematicName == null) {
            return;
        }
        pendingRestore = false;
        requestSchematic(schematicName, null, true);
    }

    private void updateStatus() {
        if (schematic == null) {
            synced.set(DATA_STATUS, STATUS_NO_SCHEMATIC);
        } else if (cursor >= schematic.blockCount()) {
            synced.set(DATA_STATUS, STATUS_FINISHED);
            running = false;
        } else if (running) {
            synced.set(DATA_STATUS, STATUS_RUNNING);
        } else {
            synced.set(DATA_STATUS, STATUS_READY);
        }
    }

    // -------------------------------------------------------------------- logic

    public static void serverTick(Level level, BlockPos pos, BlockState state, SchematicannonBlockEntity be) {
        if (!level.isClientSide()) {
            be.tick();
        }
    }

    private void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        restoreIfNeeded();

        if (schematic == null || !running || cursor >= schematic.blockCount()) {
            updateStatus();
            notifyFinishedIfDone();
            return;
        }

        int interval = getInterval();
        if (cooldown < interval) {
            cooldown++;
            return;
        }
        cooldown = 0;

        int batch = Math.max(1, 8 / interval);
        for (int i = 0; i < batch && cursor < schematic.blockCount(); i++) {
            if (fuelTicks <= 0 && !isCreativeRun() && !refuel()) {
                synced.set(DATA_STATUS, STATUS_NO_FUEL);
                setChanged();
                notifyNoFuel();
                return;
            }
            if (!placeNext()) {
                synced.set(DATA_STATUS, STATUS_MISSING_BLOCKS);
                setChanged();
                notifyMissingBlocks();
                return;
            }
        }

        synced.set(DATA_PLACED, placedCount);
        synced.set(DATA_FUEL, isCreativeRun()
                ? 100
                : (int) Math.min(100L, (fuelTicks * 100L) / Math.max(1, maxFuelTicks)));
        updateStatus();
        notifyFinishedIfDone();
        setChanged();
    }

    private boolean placeNext() {
        if (level == null || schematic == null) {
            return false;
        }
        List<Schematic.Placement> blocks = schematic.blocks();
        Schematic.Placement placement = blocks.get(cursor);
        return place(placement);
    }

    /**
     * Places one entry of the schematic at its transformed position. Split out of {@link #placeNext()}
     * so the cursor handling stays readable.
     */
    private boolean place(Schematic.Placement placement) {
        if (level == null) {
            return false;
        }
        int[] offset = transform.applyToOffset(placement.x(), placement.z(), schematic.sizeX(), schematic.sizeZ());
        BlockPos target = origin().offset(offset[0], placement.y(), offset[1]);
        BlockState wanted = transform.transformState(placement.state());
        if (wanted == null) {
            cursor++;
            return true;
        }

        if (wanted.isAir() && isIgnoringAir()) {
            cursor++;
            return true;
        }

        BlockState existing = level.getBlockState(target);
        boolean targetFree = existing.isAir() || existing.canBeReplaced();
        if (!targetFree && !isReplacingBlocks()) {
            cursor++;
            blockedCount++;
            return true;
        }
        if (!existing.isAir() && existing.getDestroySpeed(level, target) < 0) {
            cursor++; // bedrock & friends: never diggable
            blockedCount++;
            return true;
        }
        if (existing == wanted) {
            cursor++; // already correct, skip to save materials
            return true;
        }

        boolean free = isCreativeRun();

        Item item = wanted.getBlock().asItem();
        if (!free && item != null && item != net.minecraft.world.item.Items.AIR) {
            if (!consumeMaterial(item)) {
                this.missingItem = item;
                if (isSkippingMissing()) {
                    // Keep going: the rest of the structure can still be built without this one.
                    cursor++;
                    missingCount++;
                    noteMissing(item);
                    return true;
                }
                return false;
            }
        }

        level.setBlock(target, wanted, Block.UPDATE_ALL);
        applyBlockEntityData(target, placement.blockEntityData());

        if (!free) {
            fuelTicks -= FUEL_PER_BLOCK;
            if (fuelTicks < 0) {
                fuelTicks = 0;
            }
        }
        cursor++;
        placedCount++;
        this.missingItem = null;
        synced.set(DATA_PLACED, placedCount);
        return true;
    }

    private void applyBlockEntityData(BlockPos target, @Nullable CompoundTag data) {
        if (level == null || data == null || data.isEmpty()) {
            return;
        }
        BlockEntity be = level.getBlockEntity(target);
        if (be == null) {
            return;
        }
        try {
            CompoundTag merged = be.saveWithoutMetadata(level.registryAccess());
            for (String key : data.keySet()) {
                merged.put(key, data.get(key));
            }
            be.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), merged));
        } catch (Exception ignored) {
            // Corrupt block-entity data inside a schematic must never break printing.
        }
    }

    /**
     * Takes one {@code item} from the cannon's own material slots, falling back to the six
     * inventories touching the cannon (chest, barrel, shulker box, hopper, modded handlers ...).
     */
    private boolean consumeMaterial(Item item) {
        for (int i = MATERIAL_START; i < CONTAINER_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return level != null && NeighbourInventory.takeItem(level, getBlockPos(), item);
    }

    // ------------------------------------------------------------ material list

    /** How many of {@code item} the cannon and its neighbouring inventories can supply. */
    public int countAvailable(Item item) {
        int total = 0;
        for (int i = MATERIAL_START; i < CONTAINER_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total + (level == null ? 0 : NeighbourInventory.countItem(level, getBlockPos(), item));
    }

    /** Materials still needed for the blocks that have not been printed yet. */
    public List<com.schematicannon.item.MaterialListItem.Entry> buildMaterialList() {
        List<com.schematicannon.item.MaterialListItem.Entry> out = new ArrayList<>();
        if (schematic == null) {
            return out;
        }
        Map<Item, Integer> required = new LinkedHashMap<>();
        List<Schematic.Placement> blocks = schematic.blocks();
        for (int i = cursor; i < blocks.size(); i++) {
            BlockState state = blocks.get(i).state();
            if (state.isAir()) {
                continue;
            }
            Item item = state.getBlock().asItem();
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            required.merge(item, 1, Integer::sum);
        }
        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            out.add(new com.schematicannon.item.MaterialListItem.Entry(
                    entry.getKey(), entry.getValue(), countAvailable(entry.getKey())));
            if (out.size() >= com.schematicannon.item.MaterialListItem.MAX_ENTRIES) {
                break;
            }
        }
        return out;
    }

    /**
     * Writes the current material list into a {@link com.schematicannon.item.MaterialListItem}.
     * An existing list inside the player's inventory is refreshed, otherwise a new one is handed
     * out (dropped at the cannon when the inventory is full).
     */
    public boolean exportMaterialList(@Nullable Player player) {
        if (schematic == null) {
            reply(Component.translatable("schematicannon.msg.no_schematic"), player);
            return false;
        }
        List<com.schematicannon.item.MaterialListItem.Entry> entries = buildMaterialList();
        if (entries.isEmpty()) {
            reply(Component.translatable("schematicannon.msg.list_empty"), player);
            return false;
        }

        ItemStack stack = null;
        if (player != null) {
            stack = findMaterialList(player);
        }
        if (stack != null) {
            com.schematicannon.item.MaterialListItem.write(stack, schematicName, entries);
            if (player != null) {
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            }
        } else {
            ItemStack fresh = new ItemStack(com.schematicannon.ModRegistry.MATERIAL_LIST.get());
            com.schematicannon.item.MaterialListItem.write(fresh, schematicName, entries);
            if (player != null && player.getInventory().add(fresh)) {
                player.containerMenu.broadcastChanges();
            } else if (level != null) {
                BlockPos pos = getBlockPos();
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, fresh));
            }
        }

        int total = 0;
        for (com.schematicannon.item.MaterialListItem.Entry entry : entries) {
            total += entry.required();
        }
        reply(Component.translatable("schematicannon.msg.list_exported", entries.size(), total), player);
        return true;
    }

    /** Refreshes an existing material list inside the player's inventory, or hands out a new one. */
    @Nullable
    private ItemStack findMaterialList(Player player) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof com.schematicannon.item.MaterialListItem) {
                return stack;
            }
        }
        return null;
    }

    /**
     * Immediate feedback for an action the player triggered from the GUI. Unlike {@link #notify}
     * this is never rate limited, because the player expects an answer to every click.
     */
    private void reply(Component message, @Nullable Player player) {
        Messages.sendNear(level, getBlockPos(), message, Messages.DEFAULT_RADIUS, player);
    }

    private boolean refuel() {
        ItemStack stack = inventory.getItem(SLOT_FUEL);
        if (stack.isEmpty()) {
            this.invalidFuel = null;
            return false;
        }
        int burn = burnDuration(stack);
        if (burn <= 0) {
            this.invalidFuel = stack.copy();
            return false;
        }
        this.invalidFuel = null;
        stack.shrink(1);
        fuelTicks += burn;
        maxFuelTicks = Math.max(1, burn);
        inventory.setChanged();
        return true;
    }

    private int burnDuration(ItemStack stack) {
        if (level == null) {
            return 0;
        }
        try {
            return level.fuelValues().burnDuration(stack);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Where printing starts: the quill assigned target if there is one, otherwise one block above the
     * cannon and centred on it.
     */
    public BlockPos origin() {
        if (printTarget != null) {
            return printTarget;
        }
        int sx = schematic == null ? 0 : transform.transformedSizeX(schematic.sizeX(), schematic.sizeZ());
        int sz = schematic == null ? 0 : transform.transformedSizeZ(schematic.sizeX(), schematic.sizeZ());
        return getBlockPos().offset(-(sx / 2), 1, -(sz / 2));
    }

    @Nullable
    public BlockPos getPrintTarget() {
        return printTarget;
    }

    public void setPrintTarget(@Nullable BlockPos target) {
        this.printTarget = target;
        synced.set(DATA_TARGET_SET, target == null ? 0 : 1);
        synced.set(DATA_TARGET_X, target == null ? 0 : target.getX());
        synced.set(DATA_TARGET_Y, target == null ? 0 : target.getY());
        synced.set(DATA_TARGET_Z, target == null ? 0 : target.getZ());
        setChanged();
        broadcastPreview();
    }

    // -------------------------------------------------------------- transform

    public SchematicTransform getTransform() {
        return transform;
    }

    /**
     * Installs a new orientation. Printing always restarts from the beginning, because every already
     * placed block sits at a coordinate the new transform no longer maps to - continuing would litter
     * the world with the leftovers of the old orientation.
     *
     * <p>Setting the value it already has is a no-op. That matters because the quill hands its
     * orientation over on every target click, and a repeated identical value must not keep wiping a
     * running job's progress.
     */
    public void setTransform(SchematicTransform value, @Nullable Player player) {
        SchematicTransform next = value == null ? SchematicTransform.IDENTITY : value;
        if (next.equals(this.transform)) {
            return;
        }
        this.transform = next;
        synced.set(DATA_TRANSFORM, this.transform.encode());
        boolean wasRunning = running;
        if (wasRunning) {
            running = false;
        }
        resetProgress();
        // The ghost preview carries the transform with it, so it has to be re-sent - otherwise the
        // client keeps drawing the previous orientation until something else happens to broadcast.
        broadcastPreview();
        setChanged();
        if (player != null) {
            notify(Component.translatable(wasRunning
                            ? "schematicannon.msg.transform_stopped"
                            : "schematicannon.msg.transform",
                    this.transform.describe(), this.transform.compass()),
                    "transform:" + this.transform.encode(), player);
        }
    }

    // There are deliberately no rotate/flip convenience methods here. Orientation is owned by the
    // blueprint and quill: the quill's menu edits it and {@link #setTransform} is the single way it
    // reaches this block. Convenience wrappers existed only for the cannon's own GUI buttons, which
    // have been removed so the two cannot disagree about which way the structure is going to be
    // built.

    // ---------------------------------------------------------- chat reporting

    private String displayName() {
        return schematicName == null ? "?" : schematicName;
    }

    private void notifyNoFuel() {
        ItemStack bad = this.invalidFuel;
        if (bad != null && !bad.isEmpty()) {
            notify(Component.translatable("schematicannon.msg.bad_fuel", bad.getHoverName()),
                    "bad_fuel:" + BuiltInRegistries.ITEM.getKey(bad.getItem()).toString(), null);
        } else {
            notify(Component.translatable("schematicannon.msg.no_fuel"), "no_fuel", null);
        }
    }

    private void notifyMissingBlocks() {
        Item item = this.missingItem;
        Component what = item == null
                ? Component.translatable("schematicannon.error.unknown")
                : new ItemStack(item).getHoverName();
        String key = "missing:" + (item == null ? "?" : BuiltInRegistries.ITEM.getKey(item).toString());
        notify(Component.translatable("schematicannon.msg.missing_blocks", what), key, null);
    }

    /**
     * One chat line the first time a material turns out to be missing during a skipping run, so the
     * player learns what to bring instead of wondering why blocks are absent.
     */
    private void noteMissing(Item item) {
        if (!missingReported.add(item)) {
            return;
        }
        notify(Component.translatable("schematicannon.msg.missing_skipped", new ItemStack(item).getHoverName()),
                "skipping:" + BuiltInRegistries.ITEM.getKey(item).toString(), null);
    }

    private void notifyFinishedIfDone() {
        if (schematic == null || cursor < schematic.blockCount() || finishNotified) {
            return;
        }
        finishNotified = true;
        Component message;
        if (blockedCount > 0 && missingCount > 0) {
            message = Component.translatable("schematicannon.msg.finished_both", placedCount, blockedCount, missingCount);
        } else if (blockedCount > 0) {
            message = Component.translatable("schematicannon.msg.finished_skipped", placedCount, blockedCount);
        } else if (missingCount > 0) {
            message = Component.translatable("schematicannon.msg.finished_missing", placedCount, missingCount);
        } else {
            message = Component.translatable("schematicannon.msg.finished", placedCount);
        }
        notify(message, "finished:" + placedCount + ":" + blockedCount + ":" + missingCount, null);
    }

    /**
     * Sends {@code message} to {@code direct} and everyone nearby. Identical messages are
     * suppressed for {@link #NOTIFY_COOLDOWN} ticks so a stalled printer cannot spam the chat.
     */
    private void notify(Component message, String key, @Nullable Player direct) {
        if (level == null || level.isClientSide()) {
            return;
        }
        long now = level.getGameTime();
        if (key.equals(lastNotifyKey) && now - lastNotifyTime < NOTIFY_COOLDOWN) {
            return;
        }
        lastNotifyKey = key;
        lastNotifyTime = now;
        Messages.sendNear(level, getBlockPos(), message, Messages.DEFAULT_RADIUS, direct);
    }

    // -------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, inventory.getItems());
        output.putInt("Cursor", cursor);
        output.putInt("Placed", placedCount);
        output.putInt("Blocked", blockedCount);
        output.putInt("Missing", missingCount);
        output.putInt("Fuel", fuelTicks);
        output.putBoolean("Running", running);
        output.putBoolean("CreativeRun", creativeOperator);
        if (schematicName != null) {
            output.putString("Schematic", schematicName);
        }
        if (printTarget != null) {
            output.putIntArray("PrintTarget",
                    new int[]{printTarget.getX(), printTarget.getY(), printTarget.getZ()});
        }
        output.putInt("Transform", transform.encode());
        for (int i = 0; i < DATA_COUNT; i++) {
            output.putInt("Sync" + i, synced.get(i));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, inventory.getItems());
        cursor = input.getIntOr("Cursor", 0);
        placedCount = input.getIntOr("Placed", 0);
        blockedCount = input.getIntOr("Blocked", 0);
        missingCount = input.getIntOr("Missing", 0);
        fuelTicks = input.getIntOr("Fuel", 0);
        running = input.getBooleanOr("Running", false);
        creativeOperator = input.getBooleanOr("CreativeRun", false);
        schematicName = input.getString("Schematic").orElse(null);
        this.pendingRestore = schematicName != null;
        int[] target = input.getIntArray("PrintTarget").orElse(null);
        if (target != null && target.length >= 3) {
            this.printTarget = new BlockPos(target[0], target[1], target[2]);
        } else {
            this.printTarget = null;
        }
        setPrintTarget(printTarget);
        this.transform = SchematicTransform.decode(input.getIntOr("Transform", 0));
        for (int i = 0; i < DATA_COUNT; i++) {
            synced.set(i, input.getIntOr("Sync" + i, synced.get(i)));
        }
        if (schematicName == null) {
            synced.set(DATA_STATUS, STATUS_NO_SCHEMATIC);
        }
    }

    // ------------------------------------------------------------------- menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.schematicannon");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new com.schematicannon.menu.SchematicannonMenu(containerId, playerInventory, inventory, synced);
    }

    // ---------------------------------------------------------------- helpers

    public static Path schematicDirectory() {
        return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("schematics");
    }
}
