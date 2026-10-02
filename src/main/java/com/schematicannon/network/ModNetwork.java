package com.schematicannon.network;

import com.schematicannon.SchematicannonMod;
import com.schematicannon.blockentity.SchematicTableBlockEntity;
import com.schematicannon.blockentity.SchematicannonBlockEntity;
import com.schematicannon.schematic.Schematic;
import com.schematicannon.schematic.SchematicTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ModNetwork {

    private ModNetwork() {
    }

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1.0.0");

        registrar.playToServer(LoadSchematicPayload.TYPE, LoadSchematicPayload.STREAM_CODEC, ModNetwork::handleLoad);
        registrar.playToServer(RequestFileListPayload.TYPE, RequestFileListPayload.STREAM_CODEC, ModNetwork::handleFileListRequest);
        registrar.playToServer(SaveSchematicPayload.TYPE, SaveSchematicPayload.STREAM_CODEC, ModNetwork::handleSave);
        registrar.playToServer(QuillStatePayload.TYPE, QuillStatePayload.STREAM_CODEC, ModNetwork::handleQuillState);
        registrar.playToClient(FileListPayload.TYPE, FileListPayload.STREAM_CODEC, ModNetwork::handleFileList);
        registrar.playToClient(CannonStatePayload.TYPE, CannonStatePayload.STREAM_CODEC, ModNetwork::handleCannonState);
        registrar.playToClient(PreviewPayload.TYPE, PreviewPayload.STREAM_CODEC, ModNetwork::handlePreview);
    }

    /** Ghost previews bigger than this are cut off, mostly to keep the packet small. */
    public static final int PREVIEW_MAX_BLOCKS = 40000;

    // ------------------------------------------------------------------ payloads

    /** Client -> server: load the given file into the cannon at {@code pos}. */
    public record LoadSchematicPayload(BlockPos pos, String name) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LoadSchematicPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SchematicannonMod.MODID, "load_schematic"));

        public static final StreamCodec<RegistryFriendlyByteBuf, LoadSchematicPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, LoadSchematicPayload::pos,
                        ByteBufCodecs.STRING_UTF8, LoadSchematicPayload::name,
                        LoadSchematicPayload::new);

        @Override
        public CustomPacketPayload.Type<LoadSchematicPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: send me the list of available schematic files. */
    public record RequestFileListPayload(BlockPos pos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RequestFileListPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SchematicannonMod.MODID, "request_files"));

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestFileListPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, RequestFileListPayload::pos, RequestFileListPayload::new);

        @Override
        public CustomPacketPayload.Type<RequestFileListPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: which file the cannon at {@code pos} currently has loaded. */
    public record CannonStatePayload(BlockPos pos, String name) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CannonStatePayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SchematicannonMod.MODID, "cannon_state"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CannonStatePayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, CannonStatePayload::pos,
                        ByteBufCodecs.STRING_UTF8, CannonStatePayload::name,
                        CannonStatePayload::new);

        @Override
        public CustomPacketPayload.Type<CannonStatePayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the list of available schematic files (newline separated). */
    public record FileListPayload(String joined) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FileListPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SchematicannonMod.MODID, "file_list"));

        public static final StreamCodec<RegistryFriendlyByteBuf, FileListPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, FileListPayload::joined, FileListPayload::new);

        @Override
        public CustomPacketPayload.Type<FileListPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server -&gt; client: the blocks a cannon is going to print. {@code packed} holds the absolute
     * {@link BlockPos#asLong()} of every target, {@code states} the matching {@link Block#getId}
     * value. Empty arrays mean "clear the preview". {@code transform} carries the orientation the
     * cannon is using, so the ghost matches what will actually be built, and {@code cursor} is the
     * number of blocks already done, skipped when redrawing the not-yet-placed part.
     */
    public record PreviewPayload(BlockPos pos, long[] packed, int[] states, int transform) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PreviewPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SchematicannonMod.MODID, "preview"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PreviewPayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    BlockPos.STREAM_CODEC.encode(buf, value.pos());
                    buf.writeVarInt(value.transform());
                    int n = Math.min(value.packed().length, value.states().length);
                    buf.writeVarInt(n);
                    for (int i = 0; i < n; i++) {
                        buf.writeLong(value.packed()[i]);
                        buf.writeVarInt(value.states()[i]);
                    }
                },
                buf -> {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int transform = buf.readVarInt();
                    int n = buf.readVarInt();
                    if (n < 0 || n > PREVIEW_MAX_BLOCKS) {
                        n = 0;
                    }
                    long[] packed = new long[n];
                    int[] states = new int[n];
                    for (int i = 0; i < n; i++) {
                        packed[i] = buf.readLong();
                        states[i] = buf.readVarInt();
                    }
                    return new PreviewPayload(pos, packed, states, transform);
                });

        @Override
        public CustomPacketPayload.Type<PreviewPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: write the region of the quill in the table at {@code pos} to {@code name}. */
    public record SaveSchematicPayload(BlockPos pos, String name) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SaveSchematicPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SchematicannonMod.MODID, "save_schematic"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SaveSchematicPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, SaveSchematicPayload::pos,
                        ByteBufCodecs.STRING_UTF8, SaveSchematicPayload::name,
                        SaveSchematicPayload::new);

        @Override
        public CustomPacketPayload.Type<SaveSchematicPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client -> server: "the quill I am holding now looks like this".
     *
     * <p>The quill's mode and orientation live in item NBT, and the client edits its own copy so the
     * GUI and the action bar react instantly. Without this packet the server would keep its stale
     * copy, and the value read when binding a cannon would be whatever was there before any change -
     * which is exactly the "only the first change takes effect" bug.
     *
     * <p>{@code hand} says which hand holds the quill (0 main, 1 off) so the server edits the right
     * stack; {@code mode} is 0 select / 1 target; {@code transform} is the packed
     * {@link SchematicTransform} ({@code (rotation & 3) | (mirrored ? 4 : 0)}).
     */
    public record QuillStatePayload(int hand, int mode, int transform) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<QuillStatePayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SchematicannonMod.MODID, "quill_state"));

        public static final StreamCodec<RegistryFriendlyByteBuf, QuillStatePayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, QuillStatePayload::hand,
                        ByteBufCodecs.VAR_INT, QuillStatePayload::mode,
                        ByteBufCodecs.VAR_INT, QuillStatePayload::transform,
                        QuillStatePayload::new);

        @Override
        public CustomPacketPayload.Type<QuillStatePayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------ handlers

    private static void handleLoad(final LoadSchematicPayload payload, final IPayloadContext context) {        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.pos()) instanceof SchematicannonBlockEntity be) {
                be.requestSchematic(payload.name(), player);
                String loaded = be.getSchematicName();
                PacketDistributor.sendToPlayer(player,
                        new CannonStatePayload(payload.pos(), loaded == null ? "" : loaded));
                sendPreviewTo(player, payload.pos(), be.currentSchematic(), be.origin(), be.getTransform());
            }
        });
    }

    private static void handleFileListRequest(final RequestFileListPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                List<String> names = Schematic.listSchematicFiles(SchematicannonBlockEntity.schematicDirectory());
                PacketDistributor.sendToPlayer(player, new FileListPayload(String.join("\n", names)));
                if (player.level().getBlockEntity(payload.pos()) instanceof SchematicannonBlockEntity be) {
                    String loaded = be.getSchematicName();
                    PacketDistributor.sendToPlayer(player,
                            new CannonStatePayload(payload.pos(), loaded == null ? "" : loaded));
                    sendPreviewTo(player, payload.pos(), be.currentSchematic(), be.origin(), be.getTransform());
                }
            }
        });
    }

    private static void handleFileList(final FileListPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> com.schematicannon.client.ClientHooks.acceptFileList(payload.joined()));
    }

    /**
     * Applies the client's quill mode / orientation to the authoritatively held stack.
     *
     * <p>Written against {@link net.minecraft.world.entity.LivingEntity#getItemInHand} rather than a
     * slot index so it cannot be spoofed into editing an arbitrary inventory slot.
     */
    private static void handleQuillState(final QuillStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            net.minecraft.world.InteractionHand hand = payload.hand() == 1
                    ? net.minecraft.world.InteractionHand.OFF_HAND
                    : net.minecraft.world.InteractionHand.MAIN_HAND;
            net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
            if (!(held.getItem() instanceof com.schematicannon.item.SchematicAndQuillItem)) {
                return;
            }
            com.schematicannon.item.SchematicAndQuillItem.setMode(held,
                    payload.mode() == 1 ? com.schematicannon.item.SchematicAndQuillItem.Mode.TARGET
                            : com.schematicannon.item.SchematicAndQuillItem.Mode.SELECT);
            com.schematicannon.item.SchematicAndQuillItem.setTransform(held,
                    SchematicTransform.decode(payload.transform()));
        });
    }

    private static void handleCannonState(final CannonStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> com.schematicannon.client.ClientHooks.acceptCannonState(payload.pos(), payload.name()));
    }

    private static void handlePreview(final PreviewPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> com.schematicannon.client.ClientHooks.acceptPreview(
                payload.pos(), payload.packed(), payload.states(), payload.transform()));
    }

    // ---------------------------------------------------------------- preview

    /** Packs the pending blocks of {@code schematic} into a client side ghost preview. */
    public static PreviewPayload buildPreview(BlockPos pos, @Nullable Schematic schematic, BlockPos origin,
                                              SchematicTransform transform) {
        if (schematic == null) {
            return new PreviewPayload(pos, new long[0], new int[0], 0);
        }
        List<Schematic.Placement> blocks = schematic.blocks();
        long[] packed = new long[Math.min(blocks.size(), PREVIEW_MAX_BLOCKS)];
        int[] states = new int[packed.length];
        int n = 0;
        for (Schematic.Placement placement : blocks) {
            if (n >= packed.length) {
                break;
            }
            net.minecraft.world.level.block.state.BlockState state = transform.transformState(placement.state());
            if (state == null || state.isAir()) {
                continue;
            }
            int[] offset = transform.applyToOffset(placement.x(), placement.z(),
                    schematic.sizeX(), schematic.sizeZ());
            packed[n] = BlockPos.asLong(origin.getX() + offset[0],
                    origin.getY() + placement.y(),
                    origin.getZ() + offset[1]);
            states[n] = net.minecraft.world.level.block.Block.getId(state);
            n++;
        }
        return new PreviewPayload(pos, java.util.Arrays.copyOf(packed, n), java.util.Arrays.copyOf(states, n),
                transform.encode());
    }

    public static void sendPreviewTo(net.minecraft.server.level.ServerPlayer player, BlockPos pos,
                                     @Nullable Schematic schematic, BlockPos origin,
                                     SchematicTransform transform) {
        PacketDistributor.sendToPlayer(player, buildPreview(pos, schematic, origin, transform));
    }

    /** Refreshes the preview for everyone who can see the cannon. */
    public static void broadcastPreview(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                        @Nullable Schematic schematic, BlockPos origin,
                                        SchematicTransform transform) {
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk, buildPreview(pos, schematic, origin, transform));
    }

    private static void handleSave(final SaveSchematicPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.pos()) instanceof SchematicTableBlockEntity be) {
                int result = be.saveSchematic(payload.name());
                player.sendSystemMessage(com.schematicannon.util.Messages.tableResult(
                        result, be.getLastFileName(), be.getSavedBlocks(), be.getLastErrorDetail()));
            }
        });
    }

    // ------------------------------------------------------------- client senders

    public static void sendLoad(BlockPos pos, String name) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new LoadSchematicPayload(pos, name));
    }

    public static void sendRequestFileList(BlockPos pos) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new RequestFileListPayload(pos));
    }

    public static void sendSave(BlockPos pos, String name) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new SaveSchematicPayload(pos, name));
    }

    /** Tells the server what the held quill now looks like. See {@link QuillStatePayload}. */
    public static void sendQuillState(int hand, int mode, int transform) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new QuillStatePayload(hand, mode, transform));
    }

    /**
     * Result of a server side load attempt: either the schematic, or a machine readable reason
     * ("missing" / "invalid" / "path") that is turned into a chat message.
     */
    public record LoadResult(@Nullable Schematic schematic, @Nullable String error) {
        public boolean failed() {
            return schematic == null;
        }
    }

    /** Lazily loads a schematic on the server side from the `schematics` folder. */
    public static LoadResult loadServerSchematic(String name, HolderLookup<Block> lookup) {
        try {
            Path dir = SchematicannonBlockEntity.schematicDirectory();
            Files.createDirectories(dir);
            Path file = dir.resolve(name).normalize();
            if (!file.startsWith(dir.toAbsolutePath().normalize())) {
                return new LoadResult(null, "path");
            }
            if (!Files.isRegularFile(file)) {
                return new LoadResult(null, "missing");
            }
            Schematic parsed = Schematic.load(file, lookup);
            if (parsed.blockCount() == 0) {
                // 文件合法但一个方块都没解析出来 —— 多半是 blocks[].pos / blocks[].state
                // 用了我们没认出来的标签类型。明确报错，不要静默地"载入 0 个方块"。
                SchematicannonMod.LOGGER.warn(
                        "Schematic '{}' parsed to 0 blocks (size={}x{}x{}); unsupported or empty NBT layout?",
                        name, parsed.sizeX(), parsed.sizeY(), parsed.sizeZ());
                return new LoadResult(null, "empty");
            }
            return new LoadResult(parsed, null);
        } catch (Exception e) {
            SchematicannonMod.LOGGER.warn("Failed to load schematic '{}': {}", name, e.toString());
            return new LoadResult(null, "invalid");
        }
    }

    @SuppressWarnings("unused")
    private static BlockEntity unused() {
        return null;
    }
}
