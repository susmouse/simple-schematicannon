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

### 2.2 Java 源码（23 个文件 / 4719 行）

| 文件 | 行数 | 职责 |
| --- | ---: | --- |
| `SchematicannonMod.java` | 41 | `@Mod` 入口：注册 DeferredRegister、网络、客户端监听 |
| `ModRegistry.java` | 110 | 全部注册集中在这里（方块/物品/方块实体/菜单/创造页） |
| `blockentity/SchematicannonBlockEntity.java` | **880** | 核心：打印状态机、燃料、材料消耗、进度同步、清单导出、打印原点与朝向 |
| `item/SchematicAndQuillItem.java` | 410 | 选区 + 打印目标两种模式，NBT 存点与朝向；右键空气开菜单 |
| `network/ModNetwork.java` | 379 | 7 个自定义包 + 服务端处理 |
| `client/SchematicannonScreen.java` | 356 | 炮的 GUI（纯代码绘制面板） |
| `client/SchematicPreview.java` | 340 | 结构预览 + 选区/目标高亮的几何渲染 |
| `schematic/Schematic.java` | 255 | `.nbt` 读写 / 区域捕获 / 文件枚举 / 文件名清洗 |
| `client/ClientHooks.java` | 206 | 屏幕注册、包回调、tick 驱动预览、**按键处理与朝向同步** |
| `schematic/SchematicTransform.java` | 266 | **打印朝向**：全部委托原版 `StructureTemplate` 做坐标/尺寸变换，`BlockState.mirror+rotate` 同步方块朝向，外加编码 |
| `client/QuillScreen.java` | 170 | 蓝图与笔的菜单界面 |
| `util/NeighbourInventory.java` | 166 | 相邻容器取料与计数（双通道） |
| `client/MaterialListScreen.java` | 163 | 材料清单界面（分页） |
| `blockentity/SchematicTableBlockEntity.java` | 158 | 蓝图桌保存逻辑 + 结果码 |
| `menu/SchematicannonMenu.java` | 155 | 炮的容器/槽位/按钮 id |
| `item/MaterialListItem.java` | 155 | 清单数据 NBT 读写 |
| `menu/QuillMenu.java` | 120 | 蓝图与笔的菜单（无容器槽，按钮全在服务端执行） |
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
├── data/schematicannon/
│   └── recipe/       *.json  (4 个)   # 注意目录名是 recipe 单数，不是 recipes
└── assets/schematicannon/
    ├── blockstates/   schematicannon.json, schematic_table.json
    ├── items/         *.json  (4 个)   # 26.1 新增，缺了会报 Missing item model
    ├── models/block/  schematicannon.json, schematic_table.json
    ├── models/item/   *.json  (4 个)
    ├── lang/          zh_cn.json, en_us.json   (各 104 个键)
    └── textures/block/ *.png (6)
              item/     *.png (2)
```

**26.1 配方目录名是 `recipe`（单数）**，不是老版本的 `recipes`。写错不会报错，配方就是静默不加载。

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
| `player.displayClientMessage(Component, boolean)` | **26.1 已删除**。要发物品栏上方的动作栏提示用 **`Minecraft.getInstance().gui.setOverlayMessage(Component, boolean)`** |
| 物品的「按键回调」 | `Item` **没有** `onKeyPressed` / `onKeyInput` 之类的钩子。按键只能自己注册：`InputEvent.Key`（游戏总线，`getKey()` 是 GLFW 键码、`getAction()` 要自己比 `GLFW_PRESS`） |
| 反编译原版类 | `BlockState.rotate(...)` **没有** `Blackboard` 重载（26.1 只剩 `rotate(Rotation)` / `mirror(Mirror)`）。别信旧版本的写法，先 `javap` 确认 |

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
| `DATA_TRANSFORM` | 14 | 打印朝向编码：低 2 位旋转档位，bit2 为翻转 |
| `DATA_COUNT` | **15** | 同步槽总数 |

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

现有 7 个包：

| 包 | 方向 | 内容 |
| --- | --- | --- |
| `LoadSchematicPayload` | C→S | `BlockPos pos`, `String name` |
| `RequestFileListPayload` | C→S | `BlockPos pos` |
| `SaveSchematicPayload` | C→S | 蓝图桌保存（位置 + 文件名） |
| `QuillStatePayload` | C→S | 蓝图与笔的 `hand` / `mode` / `transform`（**修「只有第一次改有效」用的**，见 §3.9.4） |
| `FileListPayload` | S→C | `String joined`（换行分隔的文件列表） |
| `CannonStatePayload` | S→C | 当前载入的文件名（用于 GUI 记住选择） |
| `PreviewPayload` | S→C | `BlockPos`, `int transform`, `long[] packed`(`BlockPos.asLong`), `int[] states`(`Block.getId`)；空数组 = 清除预览 |

`PREVIEW_MAX_BLOCKS = 40000`，超出的预览被截断（不影响实际打印），并在聊天栏播报 `msg.preview_truncated`。

**自定义数组编解码**别用 `StreamCodec.composite` 硬塞，参考 `PreviewPayload` 用 `StreamCodec.of((buf, value) -> ..., buf -> ...)` 手写。

### 3.6 客户端预览机制

- 选区/目标高亮是**纯客户端本地状态**，每 tick 在 `ClientHooks.onClientTick` 里按玩家手持物重建，**不走网络**。目的是零延迟。
- 蓝图的炮侧预览是**服务端下发**的（`PreviewPayload`），因为只有服务端知道载入了什么、打印到哪了。
- `SchematicPreview.render` 里 `MAX_DRAWN = 8000` 是单帧绘制的方块上限，`SHELL_LIMIT` / `SHELL_EDGE` 控制只画外壳（省性能），`INSET = 0.02F` 是防 z-fighting 的内缩。改这些要同时考虑大结构的卡顿。
- 渲染用的是 `RenderTypes.debugQuads()` + 地图色着色，**不是真实方块模型**，所以看不出纹理和朝向。
- 退出世界时 `onLoggedOut` → `clearAll()`，别删这个监听，否则重进世界会残留幽灵方块。
- 炮侧预览的坐标由**服务端**算好（`buildPreview` 已经套用了 `SchematicTransform`），客户端只负责画，所以客户端**不需要**再知道朝向公式——`PreviewPayload.transform` 只是让客户端能做记录/调试，别在客户端再叠一次变换，会双重旋转。

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

代码注释、`lang/zh_cn.json` 的文案都是中文，保持一致。`en_us.json` 与 `zh_cn.json` **键必须完全一一对应**（各 117 个键），改一个记得改另一个。en_us 之前是用 Python `OrderedDict` 脚本重排的，手工加键时注意别打乱顺序。

### 3.9 朝向功能的四个坑（加旋转/翻转时踩到的）

1. **`InputEvent.Key` 注册在游戏总线**（`NeoForge.EVENT_BUS`），且**必须放在 `FMLEnvironment.getDist() == Dist.CLIENT` 里**——`ClientHooks` 引用的都是客户端类，专用服务端会 `NoClassDefFoundError`。键位目前是 R / T 旋转、F 翻转、G 复位，只在手持「打印目标」模式的蓝图与笔时生效。选键时注意别撞原版：`R` 在无 GUI 时没有绑定（`F3+R` 是重载资源包，带修饰键不冲突），`F` 也没被占用。按键要自己比 `getAction() == GLFW_PRESS`。
2. **朝向改动要主动刷新预览**。预览是服务端下发 + 客户端缓存的，所以按键路径额外调了一次刷新方法，让转动立刻可见而不是等下一次 tick 广播。别指望 `tickTarget` 自己会感知到物品 NBT 变了——它每 tick 重建，但拿到的朝向来自物品栈，改了栈就该重刷。
3. **`mirror` 的轴向语义容易记反**：`Mirror.LEFT_RIGHT` 翻的是 **Z 轴**（NORTH↔SOUTH），`FRONT_BACK` 才翻 X 轴。README 里给玩家的描述是「沿 Z 轴翻转，也就是 NORTH ↔ SOUTH」，改文案时别写反。
4. **⚠️ 客户端改物品 NBT 必须发包给服务端**（本项目真踩过，症状极隐蔽）。

   最初的实现是：R/T/F 按键在 `ClientHooks.onKeyInput` 里直接改客户端那份 `ItemStack` 的 NBT，右键方块时由**服务端**读 `getTransform(stack)` 交给炮。结果服务端读到的是它自己那份**旧**数据 —— 因为客户端从未把改动告诉它。症状就是「只有第一次改有效」：第一次改完恰好和服务端状态一致（或差异不可见），之后就再也对不上。
   
   修法是 `QuillStatePayload`（`playToServer`）：按键改完本地 NBT 后调 `ModNetwork.sendQuillState(hand, mode, transform)`。
   
   **判断这类 bug 的方法**：凡是「客户端改物品 NBT + 服务端读同一物品」的组合，都要问一句「服务端怎么知道？」。物品 NBT 不像方块实体有 `synced` 容器自动同步，客户端改了必须自己发包。
   
   顺带一提，**菜单按钮（`clickMenuButton`）天然没有这个问题**——按钮点击本来就是发给服务端的包，服务端执行后客户端再从同步数据读回来。所以新功能的正确写法是优先走菜单按钮，而不是客户端按键 + 补发包。

5. **⚠️ 同样是「只有第一次生效」，但还有第二个独立原因（v1.0.0 才找到）**。

   上面第 4 条讲的是**客户端→服务端方向**的丢失。还有一条是**炮→客户端方向**的：
   `setTransform` 改了朝向却没有 `broadcastPreview()`，于是客户端缓存的预览一直是旧朝向；
   更糟的是 `bindTarget` 里**先设位置、后设朝向**，而 `setPrintTarget` 会立刻广播一次，
   所以最后一次广播带的是**还没更新**的朝向。两者叠加的表现就是「点一次翻转后，只有第一次选目标翻了」。

   修法两处：`setTransform` 末尾加 `broadcastPreview()` + `setChanged()`；`bindTarget` 与 `useOn`
   的实时重瞄分支都改成**先 `setTransform`、再 `setPrintTarget`**。

   **教训**：凡是「改了 A 再广播，广播包里同时带 A 和 B」的写法，都要确认 A 在广播前已经落定。
   顺序错了不会报错，只会静默发一份旧数据。

### 3.10 蓝图与笔的菜单（`QuillMenu` / `QuillScreen`）

`data/schematicannon/recipe` 之外的交互入口：**右键空气**打开，取代了早期「右键空气一键切换模式」。

- `SchematicAndQuillItem.use` 在**服务端**调 `serverPlayer.openMenu(new SimpleMenuProvider(...))`，客户端返回 `SUCCESS` 占位。菜单的 `MenuConstructor` 里把「哪只手」(`handOrdinal`) 传进 `QuillMenu`。
- `QuillMenu` 没有容器槽，只有玩家背包（`SLOTS_TOP = 118`，快捷栏 176）。所有按钮在 `clickMenuButton` 里由**服务端**执行，id 1–6（见 §4.3）。
- `QuillMenu.stillValid` 返回「玩家还拿着笔」——这样换手/丢掉笔时界面自动关闭，不需要额外监听。
- `QuillScreen` 的标签每 tick 通过 `containerTick` 从**客户端那份 NBT** 读回来刷新。因为服务端写的和客户端本地改的是同一个栈（发包后一致），所以界面显示永远和炮实际用的一致。
- **布局常数要一起改**：`QuillScreen.HEIGHT = 200`、`inventoryLabelY = imageHeight - 94`（= 106）、背包首行 118、快捷栏 176、`QuillMenu` 里的槽位 Y 必须与之一致。改高度时这几个是连锁的。

### 3.11 已知坑：玩家状态丢失（历史 bug，勿回退）

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
- 界面高度现在 **364**（加朝向按钮那行时从 346 改过；更早是 334，再早 312。每次加行都要同步
  `imageHeight` 和菜单里的背包 Y 偏移，漏改会让背包和面板对不上）。

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
  // 9 / 10 曾是 BUTTON_ROTATE / BUTTON_FLIP，已删除。朝向归蓝图与笔，不归炮。

QuillMenu:            // 蓝图与笔菜单，全部在服务端执行
  BUTTON_MODE_SELECT   = 1   // setMode(SELECT)
  BUTTON_MODE_TARGET   = 2   // setMode(TARGET)
  BUTTON_ROTATE_LEFT   = 3   // rotate(stack, false)
  BUTTON_ROTATE_RIGHT  = 4   // rotate(stack, true)
  BUTTON_FLIP          = 5   // flip(stack)
  BUTTON_RESET         = 6   // resetTransform(stack)
```

**朝向只有一个入口**：`QuillMenu`。炮的 GUI 上**不再**有旋转/翻转按钮，`SchematicannonBlockEntity` 上
也**不再**有 `rotateClockwise` / `rotateCounterClockwise` / `flip`（这些方法只为已删除的按钮存在）。
朝向进入炮的唯一路径是 `SchematicAndQuillItem` 把编码后的 `SchematicTransform` 通过 `setTransform`
交给炮。**不要**再把朝向加回炮的菜单——两个入口会互相覆盖，玩家分不清哪边说了算。

`BUTTON_TARGET` 的**双分支反馈**是刻意的：任何「设置类」按钮必须有可观测反馈，否则玩家会以为按钮没反应。

**菜单之间 id 可以重复**：`clickMenuButton` 只在对应菜单类型内分发，所以 `QuillMenu` 从 1 开始编号不会和炮的冲突（现在炮只用到 8，留出了空档）。

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

### 4.5 打印朝向 `SchematicTransform`

```java
IDENTITY                       // 0°，不翻转
new SchematicTransform(int rotationIndex, boolean mirrored)  // rotationIndex 0..3 = 0/90/180/270° 顺时针
new SchematicTransform(Rotation, boolean)

rotateClockwise() / rotateCounterClockwise() / flipped()     // 返回新实例，不可变
applyToOffset(int x, int z, int sizeX, int sizeZ) -> int[]{x', z'}   // 相对坐标变换，y 不变
transformedSizeX(int sizeX, int sizeZ) / transformedSizeZ(...)        // 90°/270° 交换两轴
transformState(BlockState) -> BlockState                     // 同步旋转 facing / axis / rotation / orientation
encode() / decode(int)                                       // 低 2 位旋转，bit2 翻转
describe()                                                   // "0°" / "90°" / "flip+90"
compass()                                                    // "-Z" / "+X" / "+Z" / "-X"
```

> **`applyToOffset` 的签名带上了 `sizeX/sizeZ`**（旧版只有 `x, z`）。因为新版要调用
> `StructureTemplate.getZeroPositionWithTransform` 算「角点修正」，那个函数按原版签名就需要两个尺寸参数。

**⚠️ 这里曾经有一个真 bug，别再手写这套数学（v1.0.0 修复）**

旧实现自己写了坐标公式 `mx = mirrored ? -x : x` 然后旋转。**这是错的，有两处**：

1. **镜像轴写错了。** `Mirror.LEFT_RIGHT` 翻的是 **Z 轴**，旧代码翻的是 X 轴。这直接导致「水平翻转」翻错了方向。
2. **旋转没有 pivot。** 裸的相对坐标旋转只有在结构最小角正好在原点时才成立。旧实现靠 `origin()` 用「变换后的尺寸」去补偿，但**补偿不回来**：`3×5` 转 90° 时，旧的 `applyToOffset` 会吐出 `(-1, 0) ~ (-4, 0)` 这种**负**坐标，而包围盒是 `5×3`，也就是那些方块被写到结构**外面**去了。

**现在的实现：全部委托给原版，一行数学都不自己算。**

```java
// 角点修正：让变换后的结构最小角仍然落在本地原点
StructureTemplate.getZeroPositionWithTransform(PIVOT, mirror(), rotation(), sizeX, sizeZ)
// 单个相对坐标绕原点旋转
StructureTemplate.transform(new BlockPos(x, 0, z), mirror(), rotation(), PIVOT)
// 方块朝向：原版 placeInWorld 里就是这个顺序
state.mirror(mirror()).rotate(rotation())
```

`PIVOT = BlockPos.ZERO`。最终 `applyToOffset` = `transform(...)` + `getZeroPositionWithTransform(...)`，
所以 `原点 + 偏移` 的包围盒恒为 `[0,0]..[变换后尺寸-1]`，永远贴在打印原点上。

**变换顺序（关键，别改）**：**先镜像、后旋转**。这个顺序是从 `StructureTemplate#placeInWorld`
抄来的，原文是 `blockInfo.state.mirror(settings.getMirror()).rotate(settings.getRotation())`。对调会变成
「镜像一个已经转过的结构」，旋转不是 180° 时结果不同。

**方块朝向用 MC 自带方法，不要手写 switch**：

```java
state.mirror(Mirror.LEFT_RIGHT).rotate(Rotation.CLOCKWISE_90)
```

`state.rotate(Rotation)` 已经覆盖了 `Directional` / `RotatedPillar`（`axis`）/ `IntegerProperty ROTATION_16` / `Orientation`（`orientation`）等全部原版朝向属性，手写反而会漏。`Rotation` 与 `Mirror` 的轴向语义：

- `Rotation.CLOCKWISE_90.rotate(dir)` = `dir.getClockWise()`；`Direction` 的枚举序是 `DOWN,UP,NORTH,SOUTH,WEST,EAST`，所以顺时针把 `NORTH(-Z)` → `EAST(+X)`。
- `Mirror.LEFT_RIGHT.mirror(dir)` **只翻转 Z 轴**（`NORTH ↔ SOUTH`），`FRONT_BACK` 才翻转 X 轴。所以「水平翻转」= `LEFT_RIGHT`。

**26.1 原版签名（已从 `StructureTemplate.java` 源码确认，不是猜的）**：

```java
public Vec3i getSize(Rotation)                                        // 仅 90°/270° 交换 X/Z
public static BlockPos transform(BlockPos, Mirror, Rotation, BlockPos pivot)
public static BlockPos getZeroPositionWithTransform(BlockPos, Mirror, Rotation, int sizeX, int sizeZ)
public static BlockPos calculateRelativePosition(StructurePlaceSettings, BlockPos)
public static List<StructureTemplate.StructureBlockInfo> processBlockInfos(..., @Nullable StructureTemplate)
```

**改朝向必须重置打印进度**：旧进度对应的坐标在新朝向下没有意义，继续打会新旧混在一起。`setTransform(...)` 内部会 `running = false` + `resetProgress()`，并播报 `msg.transform_stopped`。这不是 bug。

**`setTransform` 必须 `broadcastPreview()`**：预览把朝向一起带走，改了朝向却不重播，客户端会一直画旧朝向。这是 v1.0.0 修掉的另一个 bug。

**自检脚本**：
- `python3 tools/transformcheck2.py` —— **权威脚本**。把 `StructureTemplate.transform` / `getZeroPositionWithTransform` 逐行抄成 Python，再独立复现一遍新版 Java 逻辑，然后检查：包围盒恒等于 `[0,0]..[尺寸-1]`（7 种尺寸 × 4 旋转 × 2 镜像）、旋转是双射、镜像对合、四连转回原位、镜像确实是 Z 轴操作。同时**审计旧实现**并报告它把 159 个非镜像格子放到了包围盒外。
- `python3 tools/vanillacmp.py` —— 记录旧实现 vs 原版的差异，用于复盘 bug，保留作证据。
- `python3 tools/transformcheck.py` —— **已过时**，它验证的是被删掉的旧公式，只在历史意义上有效，不要再用它判断正确性。


### 4.6 蓝图与笔（`SchematicAndQuillItem`）

```java
KEY_A = "FirstPos";  KEY_B = "SecondPos";  KEY_MODE = "Mode";
KEY_TARGET = "TargetPos";  KEY_BOUND = "BoundCannon";  KEY_TRANSFORM = "Transform";

enum Mode { SELECT, TARGET }                    // 右键空气切换
static Mode getMode(ItemStack) / setMode(...)
static BlockPos getFirstPos/getSecondPos(ItemStack) / clearRegion(ItemStack)
static BlockPos getTargetPos/setTargetPos/clearTarget(ItemStack)
static BlockPos getBoundCannon/setBoundCannon(ItemStack)
static BlockPos cornerAt(BlockPos clicked, @Nullable Direction face)  // 点面 → 面外侧那一格
static BlockPos aimedPos(Player)                // player.pick(24.0D, 0.0F, false) 取准星方块

// 朝向：存在笔上，绑定炮时一起交出去
static SchematicTransform getTransform/setTransform/resetTransform(ItemStack)
static SchematicTransform rotate(ItemStack, boolean clockwise) / flip(ItemStack)
```

**设计要点（历史教训）**：TARGET 模式是**单点**，不是区域。早期复用了区域选择逻辑取最小角，导致玩家以为「还要选区域」。现在的流程是三段式：
① 对空气右键切模式 → ② 右键一个方块定原点 → ③ 右键蓝图炮应用（或潜行右键取消）。
`KEY_BOUND` 记住最近一次瞄准的炮，后续点击**直接重定该炮的原点**，不用走回去再点炮。

**朝向为什么要挂在笔上**：玩家想「先调好方向、再到处挪位置」。如果朝向存在炮上，每次重新瞄准都要回 GUI 调一次，来回折腾。放在笔上之后，`bindTarget` 一次调用同时下发原点和朝向。

### 4.7 `Messages` 播报

```java
Messages.sendNear(Level level, BlockPos pos, Component msg)                  // 默认 24 格半径
Messages.sendNear(Level level, BlockPos pos, Component msg, double r, Player direct)
Messages.loadErrorReason(@Nullable String code)   // "missing" / "invalid" / "path"
Messages.tableResult(int result, String fileName, int blocks, String detail)
```

服务端才有效（`level.isClientSide()` 直接 return）。**注意别在 tick 里无条件播报**——`NOTIFY_COOLDOWN = 200` 就是为此存在；缺料播报另用 `missingReported` Set 做「每种物品只说一次」。

### 4.8 反编译原版类（怀疑 API 签名时用这个）

```bash
JAR=~/.gradle/caches/neoformruntime/artifacts/minecraft_26.1.2_client.jar
mkdir -p /tmp/mcx && cd /tmp/mcx && jar xf "$JAR" net/minecraft/client/gui/GuiGraphicsExtractor.class
javap -p -c net/minecraft/client/gui/GuiGraphicsExtractor.class | less
```

`javap -p -c` 能直接看到硬编码的常量参数（当年就是这样发现 `text(...)` 强制 `dropShadow=true` 的）。只看签名去掉 `-c` 更快。

其他有用位置：

- NeoForge 反编译源码：`~/.gradle/caches/neoformruntime/artifacts/`、`build/` 下的 `neoFormRecompile` 输出
- 原版数据包/贴图：解压官方 client jar 到 `_tools/upstream/`（已在 `.gitignore` 里）

**查原版数据格式的现成做法**（写配方/标签/模型时先抄一份原版，比自己猜快且不会写错）：

```bash
cd /tmp && mkdir -p mcref && cd mcref
jar xf ~/.gradle/caches/neoformruntime/artifacts/minecraft_26.1.2_client.jar \
    data/minecraft/recipe/chest.json data/minecraft/tags/item/logs.json \
    assets/minecraft/items/stone.json
```

原版 jar 里就有完整的 `data/minecraft/recipe/*.json`，是最权威的格式样本。改格式的版本（比如
1.21 把 `{"item": "..."}` 压成裸字符串 `"..."`、`result` 用 `id` 而不是 `item`）在这里能立刻看出来。

### 4.9 合成配方

4 个配方都在 `src/main/resources/data/schematicannon/recipe/`（**单数 `recipe`**）。
用的是原版标签而不是具体木头，所以任何木种都能合成：

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "group": "schematicannon",
  "key": { "L": "#minecraft:logs", "C": "minecraft:coal_block", "R": "minecraft:redstone_block" },
  "pattern": ["LRL", "C C", "CCC"],
  "result": { "id": "schematicannon:schematicannon" }
}
```

26.1 格式要点：

- `key` 的值直接是**字符串**，`"minecraft:coal_block"` 或 `"#minecraft:logs"`；
  老版本的 `{"item": "..."}` / `{"tag": "..."}` 写法已废弃。
- `result` 用 `"id"`（不是 `"item"`），数量用 `"count"`，默认 1。
- 无序合成用 `"ingredients": ["...", "..."]` 平铺数组，没有 `key`/`pattern`。
- `group` 只影响配方书的归类分组，可省略；`category` 影响配方书页签（`misc` / `equipment` / `building` …）。

**配方数量核对技巧**：`RecipeManager` 加载时会打 `Loaded N recipes`。
把 `data/schematicannon/recipe` 临时移走再跑一次客户端，两次 N 的差值就是自己新增的配方数——
比在配方书里翻找可靠（本次 1515 → 1519，正好 4）。

---

## 5. 工具位置

### 5.1 仓库内 `tools/`

| 文件 | 用途 |
| --- | --- |
| **`pack.sh`** | **主入口**：材质更新后的完整打包流程（校验 → 预览 → 编译 → 校验 jar） |
| `tex_preview.py` | 贴图拼版预览，输出 `textures_sheet.png`（10 倍放大 + 棋盘底衬，缺图会画红块标注） |
| `transformcheck2.py` | **打印朝向的权威自检脚本**。原样复刻原版 `StructureTemplate.transform` / `getZeroPositionWithTransform`，独立复现新版 Java 逻辑并全矩阵比对。改 `SchematicTransform` 后必跑 |
| `vanillacmp.py` | 复盘脚本：把**旧实现**与原版逐格对比，记录 bug 证据（16 个镜像配置全错、非镜像 159 格出界）。仅作历史参考 |
| `transformcheck.py` | **已过时**。验证的是被删掉的旧公式，不要再用它判断正确性 |
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
# 注意：run/saves/ 里现有存档叫 "New World"，不是 "testworld"。
# 传了不存在的存档名，客户端会停在主菜单 —— 这**不是**卡死，别等它自己进世界。
./gradlew runClient -PquickPlay="New World"

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
8. **打印朝向（重点验证，本轮重写了实现）**：
   - **炮的 GUI 上不应该再有任何旋转/翻转按钮**（这是本轮按需求删掉的）。底部长按钮只有「打印位置」，下面两行是**只读**文字：打印位置提示 + 当前朝向。
   - **朝向只在蓝图与笔上改**：对空气右键开菜单，点左转/右转/翻转/复位；或用快捷 R / T / F / G。
   - **核心回归测试（这是本轮修掉的 bug）**：连按两次「右转 90°」，每次都要生效，**不能只有第一次生效**。
     同理，连续切 0° → 90° → 180° → 270° → 0°，每一步都变；翻转也一样连点两次要来回切。
     旧版的表现是「点一次翻转后只有第一次选目标生效，后面又不翻」，根因有三个，都已修：
     (a) `applyToOffset` 镜像轴写错（翻了 X，应翻 Z）；(b) 旋转缺 pivot，90° 时方块跑到包围盒外；
     (c) `setTransform` 不重播预览 + `bindTarget` 先设位置后设朝向导致最后一次广播带的是旧朝向。
   - **换成非正方形原理图（例如 3×5）验收**：转 90° 后占地必须变 5×3，且**整体仍贴着打印原点**，
     不能有方块飞到原点负方向去。手写公式的旧版会在这里翻车，委托原版的新版不会。
   - **楼梯/熔炉/原木的朝向跟着一起转**（`state.mirror(...).rotate(...)` 生效的标志）。
   - **水平翻转**是沿 **Z** 轴的后前镜像（NORTH ↔ SOUTH），不是左右翻（X）。
   - 改朝向会**停止并重置**正在跑的打印，聊天栏有明确提示（这是刻意行为）。
   - 用笔右键炮时，位置和朝向一起生效；退出世界再进，朝向保持（`Transform` 已持久化）。
   - 冒烟测试日志里 `Missing item model` 与 `exception` 均为 0。

---

## 7. 快速上手清单

```bash
cd /d/Projects/Create-schematicannon

# 1. 确认 JDK 25 路径存在（否则改 gradle.properties）
ls "D:/Projects/minecraftMods/_tools/jdk25/jdk-25/bin/java.exe"

# 2. 编译 + 打包
bash tools/pack.sh --quick

# 3. 编译期冒烟（JAVA_HOME 必须指向 JDK 21，是启动 gradle 的 JVM，不是 toolchain 的 25）
JAVA_HOME="/d/installation/jdk21" ./gradlew runClient -PquickPlay="New World"

# 4. 首次提交
git add . && git commit -m "Initial commit: schematicannon mod"
```

> 直接跑 `./gradlew runClient` 会报
> `Cannot find a Java installation ... matching: {languageVersion=21}`——
> `downloadAssets` 任务要 JDK 21，而 toolchain 是 25。`pack.sh` 内部已经设好 `JAVA_HOME`，
> 手工敲命令时要自己带上。

改代码前的必读顺序：`ModRegistry` → `SchematicannonMod` → `SchematicannonBlockEntity` → `ModNetwork` → `ClientHooks` → 具体功能类。
