package com.schematicannon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client side ghost renderer for two independent things:
 *
 * <ul>
 *   <li>the pending structure of every loaded cannon, pushed by the server
 *       ({@code ModNetwork.PreviewPayload}) whenever the cannon state changes. Cubes whose target
 *       block already matches are skipped, so the ghost shrinks by itself while the cannon prints;</li>
 *   <li>the live Blueprint-and-Quill selection, recomputed locally every frame.</li>
 * </ul>
 */
public final class SchematicPreview {

    /** Cubes drawn per frame, hard cap to keep the frame time sane on huge schematics. */
    private static final int MAX_DRAWN = 8000;
    /** Blocks further away than this from the camera are skipped. */
    private static final double MAX_RANGE = 96.0D;
    private static final int ALPHA = 80;
    /** Slight inset so touching cubes stay distinguishable and do not z-fight. */
    private static final float INSET = 0.02F;
    private static final int FALLBACK_COLOR = 0x9AA0B0;

    /** Selections above this volume degrade to a hollow shell, so big areas stay cheap to draw. */
    private static final int SHELL_LIMIT = 40000;
    private static final int SHELL_EDGE = 2;
    /** Refuse to outline anything bigger than this - it would just be noise. */
    public static final long MAX_SELECTION = 4_000_000L;

    private static final Map<BlockPos, Preview> PREVIEWS = new HashMap<>();
    private static Level lastLevel;
    private static boolean enabled = true;

    /** The anchored first corner of the live quill selection, or null when nothing is selected. */
    private static BlockPos selectionFirst;
    /** The block under the crosshair, i.e. the far corner of the live selection box. */
    private static BlockPos selectionHead;
    private static int selectionColor = 0x4A80FF;

    /** The print origin committed by the quill in target mode. */
    private static BlockPos targetPoint;
    /** The block under the crosshair in target mode, i.e. what the next click would commit. */
    private static BlockPos targetAim;
    private static int targetColor = 0x5BE08A;
    private static int targetAimColor = 0xFFD84A;

    private SchematicPreview() {
    }

    // ------------------------------------------------------------------- state

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static void clearAll() {
        PREVIEWS.clear();
        selectionFirst = null;
        selectionHead = null;
        targetPoint = null;
        targetAim = null;
    }

    public static int cannonCount() {
        return PREVIEWS.size();
    }

    public static long selectionVolume() {
        if (selectionFirst == null || selectionHead == null) {
            return 0L;
        }
        long x = Math.abs((long) selectionHead.getX() - selectionFirst.getX()) + 1L;
        long y = Math.abs((long) selectionHead.getY() - selectionFirst.getY()) + 1L;
        long z = Math.abs((long) selectionHead.getZ() - selectionFirst.getZ()) + 1L;
        return x * y * z;
    }

    /** Installs (or, with an empty array, removes) the preview for one cannon. */
    public static void set(BlockPos pos, long[] packed, int[] stateIds, int transform) {
        if (packed.length == 0 || stateIds.length != packed.length) {
            PREVIEWS.remove(pos);
            return;
        }
        int n = packed.length;
        List<Cube> cubes = new ArrayList<>(n);
        Set<Long> occupied = new HashSet<>(Math.max(16, n * 2));
        for (int i = 0; i < n; i++) {
            BlockState state = Block.stateById(stateIds[i]);
            if (state == null || state.isAir()) {
                continue;
            }
            BlockPos target = BlockPos.of(packed[i]);
            cubes.add(new Cube(target.getX(), target.getY(), target.getZ(), state));
            occupied.add(packed[i]);
        }
        if (cubes.isEmpty()) {
            PREVIEWS.remove(pos);
            return;
        }
        PREVIEWS.put(pos, new Preview(cubes.toArray(new Cube[0]), occupied, transform));
    }

    /**
     * Refreshes the live quill selection. {@code first} is the anchored corner, {@code head} the
     * block under the crosshair, {@code color} the colour of the slot holding the quill.
     */
    public static void tickSelection(boolean active, BlockPos first, BlockPos head, int color) {
        selectionColor = color;
        if (active && first != null && head != null && !first.equals(head)) {
            selectionFirst = first;
            selectionHead = head;
        } else {
            selectionFirst = null;
            selectionHead = null;
        }
    }

    /**
     * Refreshes the quill's print target markers. {@code point} is the committed origin (green) and
     * {@code aim} the block currently under the crosshair (yellow).
     */
    public static void tickTarget(@Nullable BlockPos point, @Nullable BlockPos aim, int color, int aimColor) {
        targetColor = color;
        targetAimColor = aimColor;
        targetPoint = point;
        targetAim = aim;
    }

    // ------------------------------------------------------------------ render

    public static void render(final SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }
        if (level != lastLevel) {
            // New world / dimension: stale coordinates are meaningless.
            lastLevel = level;
            clearAll();
        }
        if (!enabled) {
            return;
        }
        boolean hasCannons = !PREVIEWS.isEmpty();
        boolean hasSelection = selectionFirst != null && selectionHead != null
                && selectionVolume() <= MAX_SELECTION;
        boolean hasTarget = targetPoint != null || targetAim != null;
        if (!hasCannons && !hasSelection && !hasTarget) {
            return;
        }

        final Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        final PoseStack poseStack = event.getPoseStack();
        final int[] budget = {MAX_DRAWN};

        poseStack.pushPose();
        poseStack.translate(-cam.x(), -cam.y(), -cam.z());
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
            if (budget[0] > 0) {
                for (Preview preview : PREVIEWS.values()) {
                    drawCannon(pose, buffer, preview, budget, cam, level);
                }
            }
            if (hasSelection && budget[0] > 0) {
                drawSelection(pose, buffer, budget, cam);
            }
            if (hasTarget && budget[0] > 0) {
                drawTarget(pose, buffer, budget, cam);
            }
        });
        poseStack.popPose();
    }

    private static void drawCannon(PoseStack.Pose pose, VertexConsumer buffer, Preview preview,
                                   int[] budget, Vec3 cam, Level level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Cube cube : preview.cubes()) {
            if (budget[0] <= 0 || !inRange(cube, cam)) {
                continue;
            }
            cursor.set(cube.x(), cube.y(), cube.z());
            if (level.getBlockState(cursor) == cube.state()) {
                continue; // already printed
            }
            budget[0]--;
            drawCube(pose, buffer, cube.x(), cube.y(), cube.z(),
                    colorOf(level, cursor, cube.state()), preview.occupied());
        }
    }

    private static void drawSelection(PoseStack.Pose pose, VertexConsumer buffer, int[] budget, Vec3 cam) {
        long volume = selectionVolume();
        boolean shell = volume > SHELL_LIMIT;
        int x0 = Math.min(selectionFirst.getX(), selectionHead.getX());
        int y0 = Math.min(selectionFirst.getY(), selectionHead.getY());
        int z0 = Math.min(selectionFirst.getZ(), selectionHead.getZ());
        int x1 = Math.max(selectionFirst.getX(), selectionHead.getX());
        int y1 = Math.max(selectionFirst.getY(), selectionHead.getY());
        int z1 = Math.max(selectionFirst.getZ(), selectionHead.getZ());
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (budget[0] <= 0) {
                        return;
                    }
                    if (shell && !onShell(x, y, z, x0, y0, z0, x1, y1, z1)) {
                        continue;
                    }
                    Cube probe = new Cube(x, y, z, null);
                    if (!inRange(probe, cam)) {
                        continue;
                    }
                    budget[0]--;
                    // null occupied -> every face is drawn, which reads as a solid wireframe box.
                    drawCube(pose, buffer, x, y, z, selectionColor, null);
                }
            }
        }
    }

    /** One green cube for the committed origin, one yellow cube for where the next click lands. */
    private static void drawTarget(PoseStack.Pose pose, VertexConsumer buffer, int[] budget, Vec3 cam) {
        if (targetAim != null && inRange(new Cube(targetAim.getX(), targetAim.getY(), targetAim.getZ(), null), cam)) {
            budget[0]--;
            drawCube(pose, buffer, targetAim.getX(), targetAim.getY(), targetAim.getZ(), targetAimColor, null);
        }
        if (targetPoint == null || targetPoint.equals(targetAim)) {
            return;
        }
        if (inRange(new Cube(targetPoint.getX(), targetPoint.getY(), targetPoint.getZ(), null), cam)) {
            // Drawn twice so the committed origin reads as a brighter, more solid marker.
            budget[0]--;
            drawCube(pose, buffer, targetPoint.getX(), targetPoint.getY(), targetPoint.getZ(), targetColor, null);
            drawCube(pose, buffer, targetPoint.getX(), targetPoint.getY(), targetPoint.getZ(), targetColor, null);
        }
    }

    private static boolean inRange(Cube cube, Vec3 cam) {
        double dx = cube.x() + 0.5D - cam.x();
        double dy = cube.y() + 0.5D - cam.y();
        double dz = cube.z() + 0.5D - cam.z();
        return dx * dx + dy * dy + dz * dz <= MAX_RANGE * MAX_RANGE;
    }

    private static boolean onShell(int x, int y, int z, int x0, int y0, int z0, int x1, int y1, int z1) {
        int dx = Math.min(x - x0, x1 - x);
        int dy = Math.min(y - y0, y1 - y);
        int dz = Math.min(z - z0, z1 - z);
        return dx < SHELL_EDGE || dy < SHELL_EDGE || dz < SHELL_EDGE;
    }

    private static int colorOf(Level level, BlockPos cursor, BlockState state) {
        try {
            int col = state.getMapColor(level, cursor).col & 0xFFFFFF;
            return col == 0 ? FALLBACK_COLOR : col;
        } catch (Exception e) {
            return FALLBACK_COLOR;
        }
    }

    /** Six translucent quads, skipping the faces that touch another cube of the same preview. */
    private static void drawCube(PoseStack.Pose pose, VertexConsumer buffer,
                                 int x, int y, int z, int color, Set<Long> occupied) {
        float x0 = x + INSET;
        float x1 = x + 1.0F - INSET;
        float y0 = y + INSET;
        float y1 = y + 1.0F - INSET;
        float z0 = z + INSET;
        float z1 = z + 1.0F - INSET;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        if (occupied == null || !occupied.contains(BlockPos.asLong(x, y - 1, z))) {
            quad(pose, buffer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b);
        }
        if (occupied == null || !occupied.contains(BlockPos.asLong(x, y + 1, z))) {
            quad(pose, buffer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b);
        }
        if (occupied == null || !occupied.contains(BlockPos.asLong(x, y, z - 1))) {
            quad(pose, buffer, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b);
        }
        if (occupied == null || !occupied.contains(BlockPos.asLong(x, y, z + 1))) {
            quad(pose, buffer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b);
        }
        if (occupied == null || !occupied.contains(BlockPos.asLong(x - 1, y, z))) {
            quad(pose, buffer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b);
        }
        if (occupied == null || !occupied.contains(BlockPos.asLong(x + 1, y, z))) {
            quad(pose, buffer, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float cx, float cy, float cz,
                             float dx, float dy, float dz,
                             int r, int g, int b) {
        buffer.addVertex(pose, ax, ay, az).setColor(r, g, b, ALPHA);
        buffer.addVertex(pose, bx, by, bz).setColor(r, g, b, ALPHA);
        buffer.addVertex(pose, cx, cy, cz).setColor(r, g, b, ALPHA);
        buffer.addVertex(pose, dx, dy, dz).setColor(r, g, b, ALPHA);
    }

    private record Cube(int x, int y, int z, BlockState state) {
    }

    private record Preview(Cube[] cubes, Set<Long> occupied, int transform) {
    }
}
