# 交接文档 — Schematicannon（蓝图炮）

> 面向接手本项目开发的工程师。
> 使用说明（玩家视角）见 `README.md`；本文只讲**代码怎么读、怎么改、改完怎么验证**。
>
> 适用版本：Minecraft **26.1.2** / NeoForge **26.1.2.112** / ModDevGradle 2.0.148 / Gradle 9.1.0 / Java toolchain **25**
> 仓库根：`D:\Projects\Create-schematicannon`

---

## 1. 项目是什么

把机械动力（Create）的**蓝图炮**玩法用单方块重做，不依赖 Create 本体、不需要应力系统。

| 内容 | 类型 | 一句话 |
| --- | --- | --- |
| 蓝图炮 `schematicannon` | 方块 + 方块实体 + GUI | 载入 `.nbt` 原理图，逐块消耗材料打印，带结构预览 |
| 蓝图桌 `schematic_table` | 方块 + 方块实体 + GUI | 把蓝图与笔标记的区域导出成 `.nbt` |
| 蓝图与笔 `schematic_and_quill` | 道具 | 两种模式：**选区**（圈两个角做原理图）／**打印目标**（定一个点改打印原点） |
| 材料清单 `material_list` | 道具 | 由蓝图炮「输出清单」按钮生成，右键看已收集/未收集，可翻页 |

关键玩法点（改动时别破坏）：

- 材料来源三层：**炮内 27 格材料槽** → **相邻 6 个容器**（原版 `Container` 与 NeoForge `ResourceHandler` 双通道，见 `NeighbourInventory`）→ **任意玩家背包**（兜底）。
- **创造模式玩家启动时自动进入免材料模式**（`OPT_FREE_RUN` 由 `setRunning(value, player)` 自动推导，没有 GUI 开关）。
- **缺料默认跳过**（`OPT_SKIP_MISSING` 默认 ON），跳过的位置计入 `missingCount`，结束时播报区分「被阻挡」与「缺材料」两类。
- 打印**无旋转/无镜像**，默认原点是炮上方一格；蓝图与笔可以把原点改到任意位置。

---

## 2. 仓库结构

### 2.1 顶层（mod 工程平铺在根目录）

```
D:\Projects\Create-schematicannon\
├── build.gradle              # ModDevGradle；含 -PquickPlay=<world> 测试入口
├── settings.gradle
├── gradle.properties         # 版本号 + 仓库外的 JDK 25 路径
├── gradlew / gradlew.bat
├── gradle/wrapper/           # Gradle 9.1.0（wrapper jar 已入库，别被 .gitignore 干掉）
├── src/main/java/com/schematicannon/
├── src/main/resources/       # META-INF + assets
├── tools/                    # 工具脚本（见 §5）
├── gen_textures.py           # 初版贴图生成器 —— 不要再跑，见 §5.4
├── README.md                 # 使用文档
├── HANDOVER.md               # 本文件
├── LICENSE                   # MIT
├── .gitignore
├── schematicannon-1.0.0.jar  # 打包产物（已被 .gitignore 忽略）
└── textures_sheet.png        # 贴图预览（已被 .gitignore 忽略）
```

> 仓库初始化时按选择**只建了目录与 `.gitignore`，没有 `git init`**（`.git` 目录存在但**零提交**）。
> 第一次提交由接手人做：`git add . && git commit -m "init"`。

### 2.2 Java 源码（20 个文件 / 3890 行）

| 文件 | 行数 | 职责 |
| --- | ---: | --- |
| `SchematicannonMod.java` | 38 | `@Mod` 入口：注册 DeferredRegister、网络、客户端监听 |
| `ModRegistry.java` | 104 | 全部注册集中在这里（方块/物品/方块实体/菜单/创造页） |
| `blockentity/SchematicannonBlockEntity.java` | **809** | 核心：打印状态机、燃料、材料消耗、进度同步、清单导出、打印原点 |
| `item/SchematicAndQuillItem.java` | 352 | 选区 + 打印目标两种模式，NBT 存点 |
| `client/SchematicPreview.java` | 340 | 结构预览 + 选区/目标高亮的几何渲染 |
| `client/SchematicannonScreen.java` | 319 | 炮的 GUI（纯代码绘制面板） |
| `network/ModNetwork.java` | 308 | 6 个自定义包 + 服务端处理 |
| `schematic/Schematic.java` | 255 | `.nbt` 读写 / 区域捕获 / 文件枚举 / 文件名清洗 |
| `util/NeighbourInventory.java` | 166 | 相邻容器取料与计数（双通道） |
| `client/MaterialListScreen.java` | 163 | 材料清单界面（分页） |
| `blockentity/SchematicTableBlockEntity.java` | 158 | 蓝图桌保存逻辑 + 结果码 |
| `item/MaterialListItem.java` | 155 | 清单数据 NBT 读写 |
| `menu/SchematicannonMenu.java` | 151 | 炮的容器/槽位/按钮 id |
| `client/ClientHooks.java` | 117 | 屏幕注册、包回调、tick 驱动预览 |
| `menu/SchematicTableMenu.java` | 107 | 桌的容器/槽位 |
| `client/SchematicTableScreen.java` | 99 | 桌的 GUI |
| `util/Messages.java` | 90 | 聊天栏播报 + 失败原因文案 |
| `block/SchematicannonBlock.java` | 72 | 方块 + ticker 挂载 + 破坏掉落 |
| `block/SchematicTableBlock.java` | 54 | 同上 |
| `util/IntArrayContainerData.java` | 33 | `ContainerData` 的 int 数组实现（同步槽的载体） |

包名统一 `com.schematicannon`，`modid = schematicannon`。

### 2.3 资源

```
src/main/resources/
├── META-INF/neoforge.mods.toml       # modLoader=javafml, loaderVersion="[4,)"
└── assets/schematicannon/
    ├── blockstates/   schematicannon.json, schematic_table.json
    ├── items/         *.json  (4 个)   # 26.1 新增，缺了会报 Missing item model
    ├── models/block/  schematicannon.json, schematic_table.json
    ├── models/item/   *.json  (4 个)
    ├── lang/          zh_cn.json, en_us.json   (各 96 个键)
    └── textures/block/ *.png (6)
              item/     *.png (2)
```

---

## 3. 后续开发注意事项

### 3.1 26.1 API 陷阱（都已踩过，别再踩）

| 老写法 / 直觉 | 26.1 必须这样 |
| --- | --- |
| `new ResourceLocation(ns, path)` | **`Identifier.fromNamespaceAndPath(ns, path)`**，类名是 `net.minecraft.resources.Identifier` |
| 只放 `models/item/x.json` | **还需 `assets/<ns>/items/x.json`**，内容 `{"model":{"type":"minecraft:model","model":"<同上路径>"}}`。缺了进游戏看 `Missing item model` 警告、物品变成紫黑块 |
| `Screen.renderBg(...)` | **`extractBackground(GuiGraphicsExtractor, int, int, float)`**；子类覆盖时**记得调 `super.extractBackground(...)`**，否则面板背后的世界不会变暗（`MaterialListScreen` 就漏过这一行） |
| `new BlockEntityType.Builder<>(...).build(...)` | **`new BlockEntityType<>(Supplier, Block...)`**，Builder 已删 |
| `@EventBusSubscriber(bus = ...)` | 没有 `bus()` 参数了；本项目改用**构造函数里手动 `addListener`**，更直观 |
| `save(CompoundTag)` / `load(CompoundTag)` | **`saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)`**；用 `output.putInt/putBoolean/putIntArray`，读用 **`input.getIntOr(key, def)`**、`input.getIntArray(key).orElse(...)`、`input.getBooleanOr(...)`、`input.getString(key).orElse(...)` |
| `ContainerHelper.saveAllItems(tag, container, ...)` | 签名变成 **`saveAllItems(ValueOutput, Container)`** / `loadAllItems(ValueInput, Container)`，**不再传 `HolderLookup.Provider`** |
| `RenderLevelStageEvent` 画自定义几何 | **`SubmitCustomGeometryEvent`**：`event.getSubmitNodeCollector().submitCustomGeometry(poseStack, renderType, (pose, buffer) -> ...)`；相机位置来自 `event.getLevelRenderState().cameraRenderState.pos`（**是 `Vec3`，不是 `Vector3f`**） |
| `new ChunkPos(BlockPos)` | 构造器没了，用 **`new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)`** |
| 物品栏能力 `IItemHandler` | NeoForge 已弃用，走 **`Capabilities.Item.BLOCK` → `ResourceHandler<ItemResource>`** + `Transaction.openRoot()` / `commit()`（见 `NeighbourInventory.handler(...)`） |

### 3.2 GUI 文字阴影陷阱（**本项目最容易复发的 bug**）

`GuiGraphicsExtractor.text(Font, Component, x, y, color)` 这个 5 参重载**硬编码 `dropShadow = true`**（用 `javap -p -c` 反编译确认：调用 6 参重载前压了 `iconst_1`）。

后果：本项目面板是浅灰 `0xFFC6C6C6`、文字是深灰 `0xFF404040`，阴影色算出来是 `0xFF101010`——**比字本身还黑**，观感就是「字体重影 / 发糊」。

**规则：本项目所有 `graphics.text(...)` 一律在末尾显式传 `false`。** 三个界面（`SchematicannonScreen` / `MaterialListScreen` / `SchematicTableScreen`）已全部处理，新增文字时别漏。

> 阴影色公式（要自定义时用）：`(color & 0xFCFCFC) >> 2 | 0xFF000000`

### 3.3 客户端隔离

`SchematicannonMod` 构造函数里，**所有会碰客户端类的监听必须写在 `FMLEnvironment.getDist() == Dist.CLIENT` 判断内**：

```java
if (FMLEnvironment.getDist() == Dist.CLIENT) {
    modBus.addListener(ClientHooks::registerScreens);
    NeoForge.EVENT_BUS.addListener(ClientHooks::onSubmitGeometry); // 游戏总线
    NeoForge.EVENT_BUS.addListener(ClientHooks::onClientTick);
    NeoForge.EVENT_BUS.addListener(ClientHooks::onLoggedOut);
}
```

否则专用服务端加载时会 `NoClassDefFoundError`。**注册总线（mod bus）与游戏总线（`NeoForge.EVENT_BUS`）别搞混**：注册类事件走 mod bus，tick / 渲染 / 网络登录走游戏总线。

### 3.4 状态同步：`IntArrayContainerData` 是唯一真理来源

炮的所有跨端状态都放在 `SchematicannonBlockEntity` 的 `synced`（`IntArrayContainerData`）里，通过 `MenuType` 的 `ContainerData` 自动同步。**`DATA_COUNT` 是同步槽数量，加字段必须同时改它。**

| 常量 | 值 | 含义 |
| --- | ---: | --- |
| `DATA_PLACED` | 0 | 已放置块数 |
| `DATA_TOTAL` | 1 | 总块数 |
| `DATA_STATUS` | 2 | 状态机（见下） |
| `DATA_FUEL` | 3 | 剩余燃料 |
| `DATA_OPTIONS` | 4 | 位掩码 |
| `DATA_INTERVAL` | 5 | 速度（1/2/4/8 tick 一块） |
| `DATA_POS_X/Y/Z` | 6/7/8 | 炮的位置（客户端拿去渲染预览） |
| `DATA_NAME_HASH` | 9 | 原理图名哈希（判断界面里选的文件是否与炮里载入的一致） |
| `DATA_TARGET_SET` | 10 | 是否已设自定义打印原点 |
| `DATA_TARGET_X/Y/Z` | 11/12/13 | 打印原点坐标 |
| `DATA_COUNT` | **14** | 同步槽总数 |

`DATA_OPTIONS` 位掩码：`OPT_IGNORE_AIR = 1`、`OPT_REPLACE_BLOCKS = 2`、`OPT_FREE_RUN = 4`（只读，由创造模式自动推导）、`OPT_SKIP_MISSING = 8`。默认值 `OPT_IGNORE_AIR | OPT_SKIP_MISSING`，在构造函数里设。

状态码：`STATUS_NO_SCHEMATIC=0 READY=1 RUNNING=2 PAUSED=3 NO_FUEL=4 MISSING_BLOCKS=5 FINISHED=6`。

**新增字段的完整改动链**：`DATA_*` 常量 → `DATA_COUNT` → 构造函数赋初值 → `saveAdditional`/`loadAdditional` 持久化 → `Menu.clickMenuButton` 的按钮分支 → `Screen` 的显示 → `lang` 两个 json 各加键。

### 3.5 网络包

`ModNetwork` 里每个包都是一个 `record`，模式固定：

```java
public record XxxPayload(A a, B b) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<XxxPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SchematicannonMod.MODID, "xxx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, XxxPayload> STREAM_CODEC =
            StreamCodec.composite(
                    <codec>, XxxPayload::a,
                    <codec>, XxxPayload::b,
                    XxxPayload::new);
    @Override public CustomPacketPayload.Type<XxxPayload> type() { return TYPE; }
}
```

在 `register()` 里 `registrar.playToServer(...)` / `playToClient(...)` 挂上处理方法。registrar 版本号是 **`"1.0.0"`，改动包结构时记得同步升版本**（现在是开发期，直接改无所谓）。

现有 6 个包：

| 包 | 方向 | 内容 |
| --- | --- | --- |
| `LoadSchematicPayload` | C→S | `BlockPos pos`, `String name` |
| `RequestFileListPayload` | C→S | `BlockPos pos` |
| `SaveSchematicPayload` | C→S | 蓝图桌保存（位置 + 文件名） |
| `FileListPayload` | S→C | `String joined`（换行分隔的文件列表） |
| `CannonStatePayload` | S→C | 当前载入的文件名（用于 GUI 记住选择） |
| `PreviewPayload` | S→C | `BlockPos`, `long[] packed`(`BlockPos.asLong`), `int[] states`(`Block.getId`)；空数组 = 清除预览 |

`PREVIEW_MAX_BLOCKS = 40000`，超出的预览被截断（不影响实际打印），并在聊天栏播报 `msg.preview_truncated`。

**自定义数组编解码**别用 `StreamCodec.composite` 硬塞，参考 `PreviewPayload` 用 `StreamCodec.of((buf, value) -> ..., buf -> ...)` 手写。

### 3.6 客户端预览机制

- 选区/目标高亮是**纯客户端本地状态**，每 tick 在 `ClientHooks.onClientTick` 里按玩家手持物重建，**不走网络**。目的是零延迟。
- 蓝图的炮侧预览是**服务端下发**的（`PreviewPayload`），因为只有服务端知道载入了什么、打印到哪了。
- `SchematicPreview.render` 里 `MAX_DRAWN = 8000` 是单帧绘制的方块上限，`SHELL_LIMIT` / `SHELL_EDGE` 控制只画外壳（省性能），`INSET = 0.02F` 是防 z-fighting 的内缩。改这些要同时考虑大结构的卡顿。
- 渲染用的是 `RenderTypes.debugQuads()` + 地图色着色，**不是真实方块模型**，所以看不出纹理和朝向。
- 退出世界时 `onLoggedOut` → `clearAll()`，别删这个监听，否则重进世界会残留幽灵方块。

### 3.7 贴图与模型的耦合

改贴图文件名**必须同步改 `models/block/schematicannon.json` 的贴图槽**，否则进游戏是紫黑块。当前对应关系：

| 贴图 | 模型槽位 |
| --- | --- |
| `schematicannon_top` | `up` |
| `schematicannon_bottom` | `down` |
| `schematicannon_left_right` | `east` / `west` / `particle` |
| `schematicannon_front_behind` | `north` / `south` |
| `schematic_table_top` | 桌顶面 |
| `schematic_table_side` | 桌其余五面 |

⚠️ **待确认**：`left_right` 上的圆形齿轮口朝向。如果实际游戏里齿轮口出现在了「前后」而不是「左右」，就需要把 `east`/`west` 与 `north`/`south` 的贴图对调（这是纯 JSON 改动，改完跑 `tools/pack.sh --quick` 重打包即可）。

### 3.8 中文注释与命名

代码注释、`lang/zh_cn.json` 的文案都是中文，保持一致。`en_us.json` 与 `zh_cn.json` **键必须完全一一对应**（各 96 个键），改一个记得改另一个。en_us 之前是用 Python `OrderedDict` 脚本重排的，手工加键时注意别打乱顺序。

### 3.9 已知坑：玩家状态丢失（历史 bug，勿回退）

`SchematicannonScreen.setAvailableFiles`（文件列表到达）与 `setLoadedName`（当前文件名到达）**两个包到达顺序不确定**，早期版本互相覆盖导致「每次打开都清空选中原理图」。修法是抽出 `applyLoadedName()` 由两者共同调用——改这块时保持这个结构。

同理，`requestSchematic` 失败时**不能**调 `loadSchematic(null, null)`（会把记住的文件名抹掉），必须走 `keepNameOnFailure` 分支。

---

## 4. 常用 API 备忘

### 4.1 注册（`ModRegistry.java`）

```java
DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
DeferredRegister.Items  ITEMS  = DeferredRegister.createItems(MODID);
DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MODID);
DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(BuiltInRegistries.MENU, MODID);
DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, MODID);

// 方块 + 方块物品
BLOCKS.registerBlock("schematicannon", SchematicannonBlock::new, () -> properties);
ITEMS.registerSimpleBlockItem("schematicannon", SCHEMATICANNON);

// 普通物品
ITEMS.registerItem("schematic_and_quill", SchematicAndQuillItem::new);

// 方块实体（26.1：构造器直接收 Block）
new BlockEntityType<>(SchematicannonBlockEntity::new, SCHEMATICANNON.get())

// 菜单
new MenuType<>(SchematicannonMenu::createClientMenu, FeatureFlags.VANILLA_SET)
```

方块实体必须在方块里挂 ticker（见 `SchematicannonBlock.getTicker`），且**只在服务端跑逻辑**：

```java
if (level.isClientSide()) return null;
return (lvl, pos, st, be) -> SchematicannonBlockEntity.serverTick(lvl, pos, st, be);
```

### 4.2 容器与槽位

- 炮：`SLOT_FUEL = 0`，材料槽 `MATERIAL_START = 1` 起 27 格，`CONTAINER_SIZE = 28`。
- 桌：`SLOT_QUILL = 0`，`CONTAINER_SIZE = 1`，`mayPlace` 只收蓝图与笔。
- 炮的 GUI 里**玩家背包槽从 `264 + row*18` 起，快捷栏 `322`**（改 GUI 高度时这两个偏移要一起动）。
- 界面高度现在 **346**（加 `drawTargetHint` 那行时从 334 改过）。

### 4.3 GUI 按钮 id（`clickMenuButton`）

```java
SchematicannonMenu:
  BUTTON_START_STOP    = 1   // setRunning(!isRunning(), player)
  BUTTON_IGNORE_AIR    = 2
  BUTTON_REPLACE       = 3
  BUTTON_SPEED         = 4   // cycleInterval(): 1 -> 2 -> 4 -> 8 -> 1
  BUTTON_RESET         = 5
  BUTTON_SKIP_MISSING  = 6
  BUTTON_MATERIAL_LIST = 7   // exportMaterialList(player)
  BUTTON_TARGET        = 8   // 有原点→清除；无原点→发使用提示（曾经只清除，被当成按钮坏了）
```

`BUTTON_TARGET` 的**双分支反馈**是刻意的：任何「设置类」按钮必须有可观测反馈，否则玩家会以为按钮没反应。

### 4.4 `Schematic` 静态 API

```java
Schematic.load(Path file, HolderGetter<Block> lookup) throws IOException
Schematic.capture(Level level, BlockPos a, BlockPos b, Path file) throws IOException
List<String> Schematic.listSchematicFiles(Path dir)
String Schematic.sanitizeFileName(String raw)

record Placement(int x, int y, int z, BlockState state, @Nullable CompoundTag blockEntityData)
int sizeX() / sizeY() / sizeZ() / List<Placement> blocks() / int blockCount()
```

原理图目录：`SchematicannonBlockEntity.schematicDirectory()` —— 服务端游戏目录下的 `schematics/`（`FMLPaths.GAMEDIR` 派生），不存在会自动建。

### 4.5 蓝图与笔（`SchematicAndQuillItem`）

```java
KEY_A = "FirstPos";  KEY_B = "SecondPos";  KEY_MODE = "Mode";
KEY_TARGET = "TargetPos";  KEY_BOUND = "BoundCannon";

enum Mode { SELECT, TARGET }                    // 右键空气切换
static Mode getMode(ItemStack) / setMode(...)
static BlockPos getFirstPos/getSecondPos(ItemStack) / clearRegion(ItemStack)
static BlockPos getTargetPos/setTargetPos/clearTarget(ItemStack)
static BlockPos getBoundCannon/setBoundCannon(ItemStack)
static BlockPos cornerAt(BlockPos clicked, @Nullable Direction face)  // 点面 → 面外侧那一格
static BlockPos aimedPos(Player)                // player.pick(24.0D, 0.0F, false) 取准星方块
```

**设计要点（历史教训）**：TARGET 模式是**单点**，不是区域。早期复用了区域选择逻辑取最小角，导致玩家以为「还要选区域」。现在的流程是三段式：
① 对空气右键切模式 → ② 右键一个方块定原点 → ③ 右键蓝图炮应用（或潜行右键取消）。
`KEY_BOUND` 记住最近一次瞄准的炮，后续点击**直接重定该炮的原点**，不用走回去再点炮。

### 4.6 `Messages` 播报

```java
Messages.sendNear(Level level, BlockPos pos, Component msg)                  // 默认 24 格半径
Messages.sendNear(Level level, BlockPos pos, Component msg, double r, Player direct)
Messages.loadErrorReason(@Nullable String code)   // "missing" / "invalid" / "path"
Messages.tableResult(int result, String fileName, int blocks, String detail)
```

服务端才有效（`level.isClientSide()` 直接 return）。**注意别在 tick 里无条件播报**——`NOTIFY_COOLDOWN = 200` 就是为此存在；缺料播报另用 `missingReported` Set 做「每种物品只说一次」。

### 4.7 反编译原版类（怀疑 API 签名时用这个）

```bash
JAR=~/.gradle/caches/neoformruntime/artifacts/minecraft_26.1.2_client.jar
mkdir -p /tmp/mcx && cd /tmp/mcx && jar xf "$JAR" net/minecraft/client/gui/GuiGraphicsExtractor.class
javap -p -c net/minecraft/client/gui/GuiGraphicsExtractor.class | less
```

`javap -p -c` 能直接看到硬编码的常量参数（当年就是这样发现 `text(...)` 强制 `dropShadow=true` 的）。只看签名去掉 `-c` 更快。

其他有用位置：

- NeoForge 反编译源码：`~/.gradle/caches/neoformruntime/artifacts/`、`build/` 下的 `neoFormRecompile` 输出
- 原版数据包/贴图：解压官方 client jar 到 `_tools/upstream/`（已在 `.gitignore` 里）

---

## 5. 工具位置

### 5.1 仓库内 `tools/`

| 文件 | 用途 |
| --- | --- |
| **`pack.sh`** | **主入口**：材质更新后的完整打包流程（校验 → 预览 → 编译 → 校验 jar） |
| `tex_preview.py` | 贴图拼版预览，输出 `textures_sheet.png`（10 倍放大 + 棋盘底衬，缺图会画红块标注） |
| `create/` | Create 本体的参考源码（`SchematicItem.java` / `SchematicPrinter.java` / `SchematicannonBlockEntity.java`），**只作实现参考，不参与编译** |
| `CORE-SPEC.md` / `VENDOR-SPEC.md` | 早期移植时整理的核心 / 第三方 API 规格笔记 |
| `portcheck.sh` / `portcheck-all.sh` / `portfilter.sh` | 移植期用于批量筛查 API 差异的脚本（历史遗留，基本用不上了） |
| `migrate_forge.py` / `migrate_pass2.py` / `make_clusters.py` | 移植期的批量改写脚本（同上，历史遗留） |
| `errstats.py` / `errdetail.py` | 解析 gradle 编译错误输出、聚类统错（排查大批 API 报错时很好用） |

### 5.2 `pack.sh` 用法

```bash
bash tools/pack.sh              # 四步全做
bash tools/pack.sh --quick      # 跳过贴图预览
bash tools/pack.sh --preview    # 只出贴图预览（画贴图时快速看效果）
bash tools/pack.sh --check      # 只做贴图校验 + 预览，不编译
```

四步依次是：

1. **校验贴图** —— 每张必须是 16×16 合法 PNG；模型里引用的贴图必须存在；列出没被任何模型引用的孤儿贴图（重命名后最容易漏）。
2. **生成预览** —— 调 `tools/tex_preview.py` 出 `textures_sheet.png`。
3. **编译打包** —— `gradlew jar`，产物复制到仓库根 `schematicannon-1.0.0.jar`。
4. **校验字节** —— 逐张比对 jar 内与磁盘上的贴图大小，确认打进去的是最新素材（防 gradle 增量缓存没刷新）。

脚本内的路径常量（改目录结构时同步改）：

```bash
PROJ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOD_DIR="$PROJ_ROOT"                          # mod 工程就在仓库根
ASSETS="$MOD_DIR/src/main/resources/assets/schematicannon"
JAR_OUT="$PROJ_ROOT/schematicannon-1.0.0.jar"
JAVA_HOME_WIN="${JAVA_HOME:-/d/installation/jdk21}"
```

### 5.3 仓库外的依赖（**未入库，路径已写进 `gradle.properties`**）

| 东西 | 位置 | 说明 |
| --- | --- | --- |
| **JDK 25** | `D:\Projects\minecraftMods\_tools\jdk25\jdk-25` | `gradle.properties` 的 `org.gradle.java.installations.paths` 指这里。**换机器要改这个路径**，或删掉该行让 Gradle 自己找 |
| 原版上游资源 | `D:\Projects\minecraftMods\_tools\upstream\` | 移植参考，`.gitignore` 已排除 `tools/upstream/` 同名目录 |
| MC 反编译源码 / 反编译产物 | `D:\Projects\minecraftMods\_tools\mcsrc\`、`nfsrc\` | 同上，参考用 |

构建时环境变量：`JAVA_HOME` 需要能被 gradlew 找到 JDK（历史上用的是 `D:\installation\jdk21` 作为**启动 gradle 的 JVM**，toolchain 再切到 JDK 25 编译；两者不是一回事，别混淆）。

> `pack.sh` 里 `JAVA_HOME_WIN` 默认值就是 `/d/installation/jdk21`。如果本机没有这个 JDK，导出 `JAVA_HOME` 覆盖它。

### 5.4 ⚠️ `gen_textures.py` 不要再跑

它是**最初那套贴图的程序化生成器**（Python 3 + zlib，无第三方依赖）。现在 8 张贴图都已手工精修过，**再跑会直接覆盖掉手改的素材**。留着只是为了记录初始素材怎么来的。要新增贴图，手画或另写脚本，别复用这个。

### 5.5 构建与测试命令

```bash
# 打包
bash tools/pack.sh --quick

# 只编译
./gradlew jar

# 起客户端（build.gradle 里加的 -PquickPlay 会自动进指定存档，方便冒烟测试）
./gradlew runClient -PquickPlay=testworld

# 起服务端
./gradlew runServer

# 干净重建（gradle 缓存/增量行为可疑时用）
./gradlew clean jar
```

**冒烟测试检查项**（跑完客户端后在日志里查）：

```bash
grep -c "Missing item model" run/logs/latest.log    # 期望 0
grep -ci "exception" run/logs/latest.log            # 期望 0（或有已知无害项）
```

> `runClient` 被 `timeout` 杀掉时是 **exit 143 / 124**，`BUILD FAILED` 也可能是超时导致，**这不等于模组出错**。判定要看日志里有没有异常，而不是看退出码。

---

## 6. 待办 / 待人工确认

以下都是代码已完成、但需要**进游戏实机确认**的事项（接手时优先跑一遍）：

1. **蓝图与笔选区预览**是真正的三维长方体（历史 bug：曾被 `project()` 压成单层/单面，已修但需目视确认）。
2. **蓝图炮记住原理图**：关 GUI 再开、退出世界再进，选中项不丢。
3. **打印目标模式**右键蓝图炮能重定向已绑定的炮。
4. **`跳过缺料` 默认 ON** 是否符合预期（当前构造函数默认打开）。
5. **材料清单界面文字**无重影（已统一传 `dropShadow=false`）。
6. **蓝图炮六面贴图朝向**，特别是 `left_right` 的齿轮口是否真的在左右（见 §3.7）。
7. **仓库首次提交**：`.git` 已存在但零提交，需要 `git add . && git commit`。

---

## 7. 快速上手清单

```bash
cd /d/Projects/Create-schematicannon

# 1. 确认 JDK 25 路径存在（否则改 gradle.properties）
ls "D:/Projects/minecraftMods/_tools/jdk25/jdk-25/bin/java.exe"

# 2. 编译 + 打包
bash tools/pack.sh --quick

# 3. 编译期冒烟
./gradlew runClient -PquickPlay=testworld

# 4. 首次提交
git add . && git commit -m "Initial commit: schematicannon mod"
```

改代码前的必读顺序：`ModRegistry` → `SchematicannonMod` → `SchematicannonBlockEntity` → `ModNetwork` → `ClientHooks` → 具体功能类。
