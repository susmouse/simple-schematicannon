# VENDOR SPEC — schematicannon NeoForge 26.1.2 port

## Background

`D:/Projects/minecraftMods/schematicannon-port` is a port of the upstream mod
`https://github.com/michiel1106/Create-schematicannon` (branch `mc1.20.1/dev`).

The upstream code lives **verbatim** in
`D:/Projects/minecraftMods/schematicannon-port/src/main/java/com/bikerboys/schematicannon/**`
and must stay as close to verbatim as possible. It is Minecraft 1.20.1 / Forge 47.

The upstream mod depends on four third-party libraries that **do not exist for Minecraft 26.1.2**:

| library | root package | classes used by upstream |
| --- | --- | --- |
| Catnip (Create's client helper lib) | `net.createmod.catnip.*` | 50 |
| Ponder (Create's tutorial lib) | `net.createmod.ponder.*` | 4 |
| Flywheel (instanced rendering engine) | `dev.engine_room.flywheel.*` | 22 |
| Registrate (registration + datagen DSL) | `com.tterrag.registrate.*` | 25 |

**Your job is to vendor (inline-implement) one of these libraries inside this project, keeping
the original package and class names**, so the upstream sources compile unchanged.

## Hard rules

1. Keep package names and class names **exactly** as upstream imports them.
   The authoritative list of names is:
   ```bash
   grep -rho "^import \(net\.createmod\|dev\.engine_room\|com\.tterrag\)[^;]*" \
       D:/Projects/minecraftMods/schematicannon-port/src/main/java/com/bikerboys | sort -u
   ```
   and the authoritative list of *members* is every usage in
   `D:/Projects/minecraftMods/schematicannon-port/src/main/java/com/bikerboys`.
   Grep before you write. **Only the members that upstream actually references must exist** —
   you are porting a dependency, not recreating it.
2. **No `net.minecraftforge.*` imports.** They do not exist in 26.1.2. Use `net.neoforged.*`.
3. `net.minecraft.resources.ResourceLocation` was **removed** in 26.1 → use
   `net.minecraft.resources.Identifier` (`Identifier.fromNamespaceAndPath(ns, path)`,
   `Identifier.parse(str)`, `id.getNamespace()`, `id.getPath()`).
4. Your vendored packages must compile **standalone**: no `import com.bikerboys.*`.
   If an upstream type seems required, redesign the signature (generic, functional interface,
   `Object`) and record it in your report.
5. Do not add third-party dependencies. The classpath is Minecraft + NeoForge only
   (plus its bundled deps: JOML `org.joml`, Gson, ASM, Mixin, slf4j, `com.mojang.*`).
6. Prefer **correct and simple over clever**. Behaviour that is genuinely unreachable on
   26.1.2 (e.g. an old instanced-rendering backend) may be implemented as a well documented
    no-op — but the *type, method names and return types* must still be right so callers compile
   and server-side/game logic keeps working.
7. Comment every degraded/no-op implementation with `// PORT:` explaining what was lost.

## Reference sources (use these, do not guess APIs)

* Minecraft 26.1.2 + NeoForge patched decompiled source: `D:/Projects/minecraftMods/_tools/mcsrc`
* NeoForge 26.1.2.112 source: `D:/Projects/minecraftMods/_tools/nfsrc`
* Upstream (1.20.1) sources of the whole mod: `D:/Projects/minecraftMods/_tools/upstream/src/main/java`
* The already-ported sibling project (same game version, hand written) is a useful API example:
  `D:/Projects/minecraftMods/schematicannon/src/main/java/com/schematicannon`
* `D:/Projects/minecraftMods/.workbuddy/memory/2026-09-29.md` lists 26.1 API differences already hit.

Known 26.1 API facts that matter to you:

* GUI rendering: `GuiGraphics` was replaced by `net.minecraft.client.gui.GuiGraphicsExtractor`.
  Widgets/screens: `render(...)` is gone; screens override
  `extractBackground(GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick)` and
  `extractContents(...)`; widgets expose `extractRenderState(...)`. See
  `_tools/mcsrc/net/minecraft/client/gui/components/AbstractWidget.java` and
  `_tools/mcsrc/net/minecraft/client/gui/screens/Screen.java`.
  `GuiGraphicsExtractor` has `fill`, `text(Font, Component, x, y, color)`, `blit*`, `item`,
  `enableScissor`, `pose()` (Matrix3x2fStack).
* `@EventBusSubscriber` only takes `modid()` / `value(Dist[])`; there is no `bus()` argument.
* Networking uses NeoForge payloads (`RegisterPayloadHandlersEvent`), not `SimpleChannel`.
* Item/block entity persistence uses `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)`.

## Compile loop (must use this)

```bash
D:/Projects/minecraftMods/_tools/portcheck.sh <dir-or-file> [more...]
```

It runs the JDK 25 `javac` against the real MC/NeoForge classpath (from
`D:/Projects/minecraftMods/schematicannon-port/compile-classpath.txt`).
If that file is missing, regenerate it with:

```bash
cd D:/Projects/minecraftMods/schematicannon-port && JAVA_HOME="D:\\installation\\jdk21" ./gradlew dumpCompileClasspath
```

It prints `[exit=0]` on success. **Do not stop while your scope still has errors** except for
errors caused by another agent's not-yet-written package.

## Deliverable

1. The vendored Java sources under
   `D:/Projects/minecraftMods/schematicannon-port/src/main/java/<your packages>`.
2. A short report file at `D:/Projects/minecraftMods/schematicannon-port/port-notes/<your-scope>.md`
   in GitHub Markdown listing: classes done, member-by-member API notes, every `// PORT:`
   degradation, and anything left unresolved.
