package com.schematicannon.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * How a schematic is oriented when it is printed: a mirror applied first, then a rotation about the
 * vertical axis - the same order vanilla uses when you place a structure with the structure block.
 *
 * <p>Two things are transformed:
 * <ul>
 *   <li>every block's relative coordinate, so the structure grows in the direction the player aimed;</li>
 *   <li>every block's {@code facing} / {@code axis} / {@code rotation} / {@code orientation} property,
 *       so stairs, furnaces and logs do not end up facing the wrong way.</li>
 * </ul>
 *
 * <h2>Why this delegates to {@code StructureTemplate}</h2>
 *
 * <p>Earlier revisions of this class wrote the coordinate math out by hand. That was wrong, and the
 * bug is worth recording so nobody reintroduces it. The mirror was implemented as "negate X", but
 * vanilla's {@link Mirror#LEFT_RIGHT} negates <b>Z</b>; {@link Mirror#FRONT_BACK} is the one that
 * negates X. On top of that, rotating raw local coordinates without a pivot only produces the right
 * answer when the structure's minimum corner sits exactly at the origin, so the error flipped sign
 * with the block's position - which is what made the flip appear to work on the first click and then
 * silently stop matching on later ones.
 *
 * <p>Rather than re-derive the arithmetic, this class now calls vanilla's own routines:
 * <ul>
 *   <li>{@link StructureTemplate#getZeroPositionWithTransform(BlockPos, Mirror, Rotation, int, int)}
 *       gives the shift that keeps the transformed structure's minimum corner on the origin,</li>
 *   <li>{@link StructureTemplate#transform(BlockPos, Mirror, Rotation, BlockPos)} rotates a single
 *       local coordinate about that origin,</li>
 *   <li>{@link StructureTemplate#getSize(Rotation)} reports the transformed footprint,</li>
 *   <li>{@link BlockState#mirror(Mirror)} then {@link BlockState#rotate(Rotation)} is the exact pair
 *       and order used inside {@code StructureTemplate#placeInWorld}.</li>
 * </ul>
 *
 * <p>Directions in vanilla run {@code NORTH = -Z}, {@code SOUTH = +Z}, {@code EAST = +X},
 * {@code WEST = -X}, which matches the enum constants {@code DOWN, UP, NORTH, SOUTH, WEST, EAST}.
 * A clockwise turn seen from above therefore sends north to east, exactly like
 * {@link Direction#getClockWise()} does.
 */
public final class SchematicTransform {

    /** Quarter turns, clockwise seen from above. {@code index} is the enum ordinal. */
    public static final Rotation[] ROTATIONS = {
            Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90
    };

    /**
     * Mirror applied before the rotation. The cannon offers a single horizontal flip, which in
     * vanilla terms is {@link Mirror#LEFT_RIGHT} - it swaps east and west, leaving the schematic's
     * north/south extent alone. Vanilla's constant names are confusingly axis-based for a
     * world-axis mirror, so the mapping is spelled out rather than inferred from the name.
     */
    public static final Mirror[] MIRRORS = {Mirror.NONE, Mirror.LEFT_RIGHT};

    public static final SchematicTransform IDENTITY = new SchematicTransform(0, false);

    /** Vanilla's pivot for an origin-anchored structure: rotations happen about the minimum corner. */
    private static final BlockPos PIVOT = BlockPos.ZERO;

    private final int rotationIndex;
    private final boolean mirrored;

    public SchematicTransform(int rotationIndex, boolean mirrored) {
        this.rotationIndex = Math.floorMod(rotationIndex, ROTATIONS.length);
        this.mirrored = mirrored;
    }

    public SchematicTransform(Rotation rotation, boolean mirrored) {
        this(rotation.ordinal(), mirrored);
    }

    public int rotationIndex() {
        return rotationIndex;
    }

    public boolean mirrored() {
        return mirrored;
    }

    public Rotation rotation() {
        return ROTATIONS[rotationIndex];
    }

    public Mirror mirror() {
        return MIRRORS[mirrored ? 1 : 0];
    }

    public boolean isIdentity() {
        return rotationIndex == 0 && !mirrored;
    }

    // ------------------------------------------------------------- composition

    /** One quarter turn clockwise. */
    public SchematicTransform rotateClockwise() {
        return new SchematicTransform(rotationIndex + 1, mirrored);
    }

    /** One quarter turn counter clockwise. */
    public SchematicTransform rotateCounterClockwise() {
        return new SchematicTransform(rotationIndex - 1, mirrored);
    }

    /** Toggles the horizontal mirror. */
    public SchematicTransform flipped() {
        return new SchematicTransform(rotationIndex, !mirrored);
    }

    // --------------------------------------------------------------- geometry

    /**
     * The shift that keeps the transformed structure's minimum corner at the local origin, so a
     * caller working with {@code origin + localOffset} gets a bounding box that still starts at the
     * origin instead of running off to negative coordinates.
     *
     * <p>This is {@code StructureTemplate.getZeroPositionWithTransform} evaluated at the origin, and
     * is exact rather than approximate - it accounts for both the mirror stop and the rotation stop,
     * which is why it must be applied for every axis and not just the rotated ones.
     */
    private int[] cornerShift(int sizeX, int sizeZ) {
        BlockPos shift = StructureTemplate.getZeroPositionWithTransform(PIVOT, mirror(), rotation(), sizeX, sizeZ);
        return new int[]{shift.getX(), shift.getZ()};
    }

    /**
     * Rotates a relative X/Z offset about the origin, mirroring first, then shifts it so the
     * transformed footprint still starts at {@code (0, 0)}.
     *
     * <p>Mirror-then-rotate is vanilla's order. Swapping them would mirror the already rotated
     * structure, which is a different result whenever the rotation is not a half turn.
     */
    public int[] applyToOffset(int x, int z, int sizeX, int sizeZ) {
        BlockPos rotated = StructureTemplate.transform(new BlockPos(x, 0, z), mirror(), rotation(), PIVOT);
        int[] shift = cornerShift(sizeX, sizeZ);
        return new int[]{rotated.getX() + shift[0], rotated.getZ() + shift[1]};
    }

    /**
     * How many blocks of each axis the transformed schematic occupies. Vanilla reports the swap
     * through {@code getSize(Rotation)}, so this asks it directly rather than testing for a quarter
     * turn by hand.
     */
    public int transformedSizeX(int sizeX, int sizeZ) {
        return transformedSize(sizeX, sizeZ).getX();
    }

    public int transformedSizeZ(int sizeX, int sizeZ) {
        return transformedSize(sizeX, sizeZ).getZ();
    }

    /**
     * The transformed footprint. A mirror never changes it, so only the rotation matters, but the
     * helper is written generically to keep a single source of truth.
     *
     * <p>An empty schematic is short-circuited: {@code getSize(Rotation)} is an instance method that
     * reads the template's own size, and this class has no template to hand, so the swap is applied
     * to the supplied dimensions instead - which is exactly equivalent, because the swap is a pure
     * exchange of the two numbers.
     */
    private Vec3i transformedSize(int sizeX, int sizeZ) {
        if (sizeX <= 0 || sizeZ <= 0) {
            return new Vec3i(Math.max(sizeX, 0), 0, Math.max(sizeZ, 0));
        }
        return switch (rotation()) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(sizeZ, 0, sizeX);
            default -> new Vec3i(sizeX, 0, sizeZ);
        };
    }

    // ------------------------------------------------------------------ states

    /**
     * Turns a block state to match the transform.
     *
     * <p>Property rotation is delegated entirely to vanilla's {@link BlockState#rotate(Rotation)} and
     * {@link BlockState#mirror(Mirror)}. Those forward to {@code BlockBehaviour#rotate} /
     * {@code #mirror}, whose default bodies are no-ops returning the state untouched; blocks that
     * actually own a directional property override them and handle every one of their own properties
     * in a single call - {@code HorizontalDirectionalBlock} turns {@code facing},
     * {@code RotatedPillarBlock} turns {@code axis}, {@code StairBlock} turns both {@code facing} and
     * {@code shape}. Because the delegate does the whole job, calling it a second time would turn
     * those properties twice, so it is called exactly once.
     *
     * <p>The pair and the order below are lifted from {@code StructureTemplate#placeInWorld}, which
     * writes {@code blockInfo.state.mirror(settings.getMirror()).rotate(settings.getRotation())} -
     * mirror first, then rotate. Blocks with no directional property (stone, planks, chests) simply
     * come back unchanged.
     */
    public BlockState transformState(BlockState state) {
        if (state == null || isIdentity()) {
            return state;
        }
        try {
            return state.mirror(mirror()).rotate(rotation());
        } catch (Exception ignored) {
            // A modded block whose rotate() is broken must never break printing.
            return state;
        }
    }

    // ---------------------------------------------------------------- encoding

    /** Compact form for the network and for save data: rotation in the low bits, mirror as bit 2. */
    public int encode() {
        return (rotationIndex & 0x3) | (mirrored ? 0x4 : 0);
    }

    public static SchematicTransform decode(int packed) {
        return new SchematicTransform(packed & 0x3, (packed & 0x4) != 0);
    }

    /** Human readable, used by the chat and tooltip messages. */
    public String describe() {
        int degrees = rotationIndex * 90;
        if (mirrored) {
            return degrees == 0 ? "flip" : "flip+" + degrees;
        }
        return degrees + "\u00b0";
    }

    /**
     * Which world axis the schematic's own <b>north</b> now points down, in the {@code +X} / {@code -Z}
     * style players already read coordinates in. Together with {@link #describe()} this answers the
     * only question that matters before hitting start: which way will it come out?
     */
    public String compass() {
        Direction north = rotation().rotate(Direction.NORTH);
        if (mirrored) {
            // The mirror runs along Z, which is the axis NORTH/SOUTH live on, so it swaps those two.
            north = north.getAxis() == Direction.Axis.Z ? north.getOpposite() : north;
        }
        return switch (north) {
            case EAST -> "+X";
            case SOUTH -> "+Z";
            case WEST -> "-X";
            default -> "-Z";
        };
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof SchematicTransform t
                && t.rotationIndex == rotationIndex && t.mirrored == mirrored;
    }

    @Override
    public int hashCode() {
        return rotationIndex * 31 + (mirrored ? 1 : 0);
    }

    @Override
    public String toString() {
        return "SchematicTransform[" + describe() + "]";
    }
}
