# Core-cluster port spec (Minecraft 26.1.2 / NeoForge)

Shared helpers already exist and compile — USE THEM, do not reinvent:

- `com.bikerboys.schematicannon.foundation.utility.PortNbt`
  - `hasTag(stack)` / `getTagOrEmpty(stack)` / `setTag(stack, tag)` / `updateTag(stack, consumer)`
    → replaces `ItemStack#getTag/hasTag/getOrCreateTag/setTag` via `DataComponents.CUSTOM_DATA`
  - `putBlockPos(tag, key, pos)` / `getBlockPos(tag, key)` → replaces `NbtUtils.write/readBlockPos`,
    uses the same `{X,Y,Z}` layout as `SchematicInstances`
  - `componentToNbt(comp)` / `componentFromNbt(tag)` → replaces `Component.Serializer.toJson/fromJson`
  - `saveStack(stack, provider)` / `loadStack(tag, provider)` → replaces `ItemStack.serializeNBT()` /
    `ItemStack.of(tag)` (codec-based, needs a `HolderLookup.Provider` — use `level.registryAccess()`
    or `Minecraft.getInstance().level.registryAccess()`)
- `com.bikerboys.schematicannon.foundation.utility.EmptyItemHandler.INSTANCE`
  → replaces `net.neoforged.neoforge.items.wrapper.EmptyHandler.INSTANCE`
- `com.bikerboys.schematicannon.foundation.utility.INBTSerializable<T>` → vendored replacement for
  the removed NeoForge `INBTSerializable`
- `com.bikerboys.schematicannon.foundation.utility.PortClientKeys.hasShiftDown()/hasControlDown()/hasAltDown()`
  → replaces the removed `Screen` statics

## Verified API mapping (all checked against `_tools/mcsrc` + `_tools/nfsrc` decompiled sources)

| 1.20.1 / Forge | 26.1.2 / NeoForge |
|---|---|
| `ForgeCapabilities.ITEM_HANDLER` | `net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK` |
| `be.getCapability(cap, side)` | `level.getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), side)` (default method from `ILevelExtension`; returns `@Nullable IItemHandler`, **null means absent** — no LazyOptional) |
| `ItemHandlerHelper.canItemStacksStack(a,b)` | `ItemStack.isSameItemSameComponents(a, b)` (static, exists) |
| `ItemHandlerHelper.copyStackWithSize(s,n)` | `s.copyWithCount(n)` (guard empty) |
| `ItemStack.isSameItemSameTags(a,b)` | `ItemStack.isSameItemSameComponents(a, b)` |
| `InteractionResultHolder<T> use(...)` | `InteractionResult use(Level, Player, InteractionHand)` — return `InteractionResult.SUCCESS/PASS/FAIL` directly, do item mutations before returning |
| `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` | `if (FMLEnvironment.dist.isClient())` (net.neoforged.fml.loading.FMLEnvironment) with the client work extracted into a separate method, or guard on `level.isClientSide()` |
| `inventory.selected` | `inventory.getSelectedSlot()` |
| `profile.getName()` | `profile.name()` |
| `player.displayClientMessage(c,false)` | `player.sendSystemMessage(c)` |
| `player.displayClientMessage(c,true)` | `player.sendOverlayMessage(c)` |
| `item.getDescription()` | `Component.translatable(item.getDescriptionId())` |
| `Object2IntMap.getIntOr(k,0)` | `map.getInt(k)` (fastutil; returns 0 when absent) |
| `new AABB(pos1, pos2)` (BlockPoss) | `AABB.encapsulatingFullBlocks(pos1, pos2)` |
| `new AABB(pos)` (single BlockPos) | still exists: `new AABB(BlockPos)` |
| `direction.getNormal()` | `direction.getUnitVec3i()` |
| `camera.getPosition()` | `camera.position()` |
| `FarmBlock` | `net.minecraft.world.level.block.FarmlandBlock` |
| `Items.GRASS` | `Items.SHORT_GRASS` |
| `ingredient.getItems()` | `ingredient.items()` → `Stream<Holder<Item>>` (map `Holder::value`) |
| `structure.fillFromWorld(level, pos, size, b, Blocks.AIR)` | last param is `List<Block>` → `List.of(Blocks.AIR)` |
| `playerWillDestroy(...) void` | now returns `BlockState` — `public BlockState playerWillDestroy(...) { ...; return super.playerWillDestroy(level, pos, state, player); }` |
| `block.updateShape(state, facing, facingState, level, pos, facingPos)` | new signature `updateShape(BlockState, LevelReader, ScheduledTickAccess, RandomSource, BlockPos, Direction, BlockPos, BlockState, BlockPos)` — copy exact order from `_tools/mcsrc/net/minecraft/world/level/block/state/BlockBehaviour.java` line ~147 |
| missing `codec()` override | `public static final MapCodec<X> CODEC = simpleCodec(X::new);` + `@Override protected MapCodec<? extends Block> codec() { return CODEC; }` — check exact visibility in BlockBehaviour |
| `cooldowns.addCooldown(item, n)` | `cooldowns.addCooldown(itemStack, n)` |
| `Entity.serializeNBT()` | `entity.saveWithoutId(TagValueOutput.createWithoutContext(new ProblemReporter.Collector()))` then `.buildResult()` (net.minecraft.world.level.storage.TagValueOutput; net.minecraft.util.ProblemReporter) |
| `EntityType.create(tag, level)` | `EntityType.create(TagValueInput.create(new ProblemReporter.Collector(), provider, tag), level, EntitySpawnReason.DEFAULT)` — `TagValueInput.create(ProblemReporter, HolderLookup.Provider, CompoundTag)`; `provider` = `level.registryAccess()` |
| `blockEntity.load(tag)` | `blockEntity.loadWithComponents(TagValueInput.create(...))` |
| `HoverEvent.Action.SHOW_ITEM` / `HoverEvent.ItemStackInfo` | 26.1 `HoverEvent` is a sealed interface with records (e.g. `HoverEvent.ShowItem` takes an `ItemStackTemplate`) — prefer DROPPING the hover event with a `// PORT:` comment unless trivially mappable |
| `stack.set/get` custom display name | `stack.set(DataComponents.ITEM_NAME, component)` (tag element "display"/"Name" is gone) |
| written book | build `net.minecraft.world.item.component.WrittenBookContent` record: `new WrittenBookContent(Filterable.passThrough(title), author, 0, List<Filterable<Component>> pages, true)` (Filterable is `net.minecraft.server.network.Filterable`; `Filterable.passThrough(c)`), set via `stack.set(DataComponents.WRITTEN_BOOK_CONTENT, ...)` |
| `Screen.hasShiftDown()` etc | `PortClientKeys.hasShiftDown()` etc |

## Capability restructure (SchematicannonBlockEntity.attachedInventories)

Replace `LinkedHashSet<LazyOptional<IItemHandler>> attachedInventories` with a small record, e.g.
`record AttachedInventory(BlockPos pos, Direction side)` kept in a `List`. At every former
`cap.orElse(EmptyHandler.INSTANCE)` use site, query live:
`IItemHandler h = level == null ? null : level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);`
and `if (h == null) continue;`. Never store the resolved handler.

## General rules

- `ResourceLocation` → `net.minecraft.resources.Identifier` (already applied; finish any leftovers).
- No `net.minecraftforge.*`, no `net.neoforged.fml.javafmlmod.*`.
- Keep upstream class names/method names/structure — reviewer must be able to diff against
  `_tools/upstream/src/main/java/**`.
- Degradations get a `// PORT:` comment. Never leave code that NPEs/crashes at runtime.
- Compile loop: `D:/Projects/minecraftMods/_tools/portcheck.sh all` (whole tree) — only fix errors
  in YOUR files. There are 219 errors right now, all in the core cluster except one in
  `foundation/events/ClientEvents.java:168` (assigned to the schematics worker, see below).
