package com.schematicannon.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small, dependency free reader/writer for the two "standard" schematic flavours:
 * <ul>
 *   <li>Create / Schematica style: every block entry carries a full "state" compound.</li>
 *   <li>Vanilla structure-block style: every block entry carries a "state" index into a "palette" list.</li>
 * </ul>
 */
public class Schematic {

    public record Placement(int x, int y, int z, BlockState state, @Nullable CompoundTag blockEntityData) {
    }

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<Placement> blocks;

    public Schematic(int sizeX, int sizeY, int sizeZ, List<Placement> blocks) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = blocks;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public List<Placement> blocks() {
        return blocks;
    }

    public int blockCount() {
        return blocks.size();
    }

    // ------------------------------------------------------------------ loading

    public static Schematic load(Path file, HolderGetter<Block> blockLookup) throws IOException {
        CompoundTag root = readRoot(file);

        int[] size = readSize(root);
        ListTag palette = root.getListOrEmpty("palette");
        ListTag blockList = root.getListOrEmpty("blocks");
        List<Placement> blocks = new ArrayList<>();

        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag tag = blockList.getCompoundOrEmpty(i);

            // "pos" 有两种写法，必须都认：
            //   TAG_Int_Array  —— 我们自己 capture() 写的，以及大多数编辑器
            //   TAG_List<Int>  —— 原版结构方块导出 / 各种网页转换工具
            // getIntArray() 只认前者，对后者返回 Optional.empty()，所以不能只用它。
            int[] pos = readIntTriple(tag, "pos");
            if (pos == null) {
                continue;
            }

            // "state" 也有两种写法：
            //   TAG_Compound —— Create / Schematica 风格，内联完整方块状态
            //   TAG_Int      —— 原版结构方块风格，palette 索引
            // 注意 getCompound() 对 IntTag 返回 empty，正好落到 palette 分支。
            BlockState state = null;
            CompoundTag stateTag = tag.getCompound("state").orElse(null);
            if (stateTag != null) {
                state = readState(blockLookup, stateTag);
            } else {
                int index = tag.getIntOr("state", -1);
                if (index >= 0 && index < palette.size()) {
                    state = readState(blockLookup, palette.getCompoundOrEmpty(index));
                }
            }
            if (state == null) {
                continue;
            }

            blocks.add(new Placement(pos[0], pos[1], pos[2], state, tag.getCompound("nbt").orElse(null)));
        }

        // bottom-up, layer by layer - exactly like Create does it
        blocks.sort(Comparator.<Placement>comparingInt(Placement::y)
                .thenComparingInt(Placement::x)
                .thenComparingInt(Placement::z));

        if (size[0] <= 0 && !blocks.isEmpty()) {
            int maxX = 0, maxY = 0, maxZ = 0;
            for (Placement p : blocks) {
                maxX = Math.max(maxX, p.x());
                maxY = Math.max(maxY, p.y());
                maxZ = Math.max(maxZ, p.z());
            }
            size = new int[]{maxX + 1, maxY + 1, maxZ + 1};
        }

        return new Schematic(Math.max(1, size[0]), Math.max(1, size[1]), Math.max(1, size[2]), blocks);
    }

    // ------------------------------------------------------------------ saving

    /** Captures every non-air block inside the box and writes it as a gzipped NBT file. */
    public static Schematic capture(Level level, BlockPos a, BlockPos b, Path file) throws IOException {
        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));

        int sx = max.getX() - min.getX() + 1;
        int sy = max.getY() - min.getY() + 1;
        int sz = max.getZ() - min.getZ() + 1;
        if ((long) sx * sy * sz > 4_000_000L) {
            throw new IOException("Selection is too large (limit: 4,000,000 blocks)");
        }

        CompoundTag root = new CompoundTag();
        ListTag palette = new ListTag();
        ListTag blockList = new ListTag();
        Map<BlockState, Integer> lookup = new HashMap<>();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    cursor.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    Integer id = lookup.get(state);
                    if (id == null) {
                        id = palette.size();
                        palette.add(NbtUtils.writeBlockState(state));
                        lookup.put(state, id);
                    }
                    CompoundTag entry = new CompoundTag();
                    entry.putIntArray("pos", new int[]{x, y, z});
                    entry.putInt("state", id);
                    blockList.add(entry);
                }
            }
        }

        root.putIntArray("size", new int[]{sx, sy, sz});
        root.put("palette", palette);
        root.put("blocks", blockList);
        root.put("entities", new ListTag());

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        NbtIo.writeCompressed(root, file);

        // Re-read so the returned instance matches exactly what will be printed later.
        return load(file, level.registryAccess().lookupOrThrow(Registries.BLOCK));
    }

    /** Lists every .nbt file inside a folder, sorted alphabetically. */
    public static List<String> listSchematicFiles(Path dir) {
        List<String> names = new ArrayList<>();
        try {
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".nbt"))
                        .sorted(String::compareToIgnoreCase)
                        .forEach(names::add);
            }
        } catch (IOException ignored) {
            // No folder / no permission -> simply no entries.
        }
        return names;
    }

    /** Strips any path/directory part from a user supplied file name. */
    public static String sanitizeFileName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            return "";
        }
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            name = name + ".nbt";
        }
        return name;
    }

    /**
     * Reads a 3-element integer field that may be written either as {@code TAG_Int_Array}
     * (our own {@link #capture}) or as {@code TAG_List<TAG_Int>} (vanilla structure blocks and
     * most web based converters). Returns {@code null} when the field is missing or too short.
     */
    @Nullable
    private static int[] readIntTriple(CompoundTag tag, String key) {
        int[] direct = tag.getIntArray(key).orElse(null);
        if (direct != null && direct.length >= 3) {
            return new int[]{direct[0], direct[1], direct[2]};
        }
        ListTag list = tag.getList(key).orElse(null);
        if (list != null && list.size() >= 3) {
            return new int[]{list.getIntOr(0, 0), list.getIntOr(1, 0), list.getIntOr(2, 0)};
        }
        return null;
    }

    private static BlockState readState(HolderGetter<Block> lookup, CompoundTag tag) {
        try {
            return NbtUtils.readBlockState(lookup, tag);
        } catch (Exception e) {
            return null;
        }
    }

    private static int[] readSize(CompoundTag root) {
        int[] s = readIntTriple(root, "size");
        return s != null ? s : new int[]{0, 0, 0};
    }

    /** Reads a (possibly gzip compressed) NBT file. */
    private static CompoundTag readRoot(Path file) throws IOException {
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (tag != null) {
                return tag;
            }
        } catch (IOException | IllegalArgumentException compressedFailed) {
            // fall through and try the uncompressed reader
        }
        CompoundTag plain = NbtIo.read(file);
        if (plain == null) {
            throw new IOException("Not a valid NBT file: " + file.getFileName());
        }
        return plain;
    }
}
