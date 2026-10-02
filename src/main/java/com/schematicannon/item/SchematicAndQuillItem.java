package com.schematicannon.item;

import com.schematicannon.blockentity.SchematicannonBlockEntity;
import com.schematicannon.schematic.SchematicTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Blueprint and Quill: right click two corners of a structure to mark the region.
 *
 * <p>Right clicking the air switches between the two modes:
 * <ul>
 *   <li>{@link Mode#SELECT} - mark a region, then save it from the blueprint table;</li>
 *   <li>{@link Mode#TARGET} - mark a <b>single point</b>, then right click a cannon to make it print
 *       from there instead of above itself. The schematic already knows its own size, so one corner
 *       is all the cannon needs.</li>
 * </ul>
 */
public class SchematicAndQuillItem extends Item {

    public static final String KEY_A = "FirstPos";
    public static final String KEY_B = "SecondPos";
    public static final String KEY_MODE = "Mode";
    /** The single print origin picked in {@link Mode#TARGET}. */
    public static final String KEY_TARGET = "TargetPos";
    /** Last cannon this quill aimed, so later clicks re-aim it without walking back. */
    public static final String KEY_BOUND = "BoundCannon";
    /** Orientation the quill hands to the cannon: rotation in the low bits, mirror as bit 2. */
    public static final String KEY_TRANSFORM = "Transform";

    public enum Mode {
        SELECT, TARGET
    }

    public SchematicAndQuillItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    // -------------------------------------------------------------------- modes

    public static Mode getMode(ItemStack stack) {
        return stack.isEmpty() ? Mode.SELECT : readMode(tag(stack));
    }

    private static Mode readMode(CompoundTag tag) {
        return tag.getInt(KEY_MODE).orElse(0) == 1 ? Mode.TARGET : Mode.SELECT;
    }

    public static void setMode(ItemStack stack, Mode mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag ->
                tag.putInt(KEY_MODE, mode == Mode.TARGET ? 1 : 0));
    }

    /**
     * Right clicking the air opens the control panel, where the mode and the print orientation are
     * chosen from buttons instead of a hidden keyboard shortcut.
     *
     * <p>The menu is opened on the <b>server</b> (the return value below is what triggers it), so the
     * client screen and the server's copy of the item leave the same state behind. The earlier design
     * toggled the mode straight from {@code use} and handled orientation with client side keys, which
     * desynced: the server kept a stale transform and only the first change ever seemed to apply.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        int ordinal = hand == InteractionHand.OFF_HAND ? 1 : 0;
        serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, p) -> new com.schematicannon.menu.QuillMenu(id, inventory, ordinal),
                Component.translatable("schematicannon.quill.menu.title")));
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ targeting

    /**
     * Applies the picked point to a cannon.
     *
     * <p>With no point picked the click is a reset: the cannon goes back to printing above itself.
     * With nothing to reset either, it only explains the two step flow instead of failing silently.
     */
    private static void bindTarget(SchematicannonBlockEntity cannon, ItemStack stack, @Nullable Player player) {
        BlockPos point = getTargetPos(stack);
        if (point == null) {
            if (cannon.getPrintTarget() == null) {
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("schematicannon.quill.target_hint"));
                }
                return;
            }
            cannon.setPrintTarget(null);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("schematicannon.quill.target_cleared"));
            }
            return;
        }
        // Orientation first, then the position. Both trigger a preview broadcast, and doing it in this
        // order means the last broadcast already carries the new orientation - the position packet is
        // the one the player sees settle.
        //
        // Do not swap these: setPrintTarget broadcasts immediately, so applying the transform after
        // it would leave the client showing the preview built from the previous orientation.
        cannon.setTransform(getTransform(stack), player);
        cannon.setPrintTarget(point);
        setBoundCannon(stack, cannon.getBlockPos());
        if (player != null) {
            player.sendSystemMessage(Component.translatable("schematicannon.quill.target_set",
                    point.getX(), point.getY(), point.getZ()));
        }
    }

    // ----------------------------------------------------------------- selection

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Corner and mode state are tracked on both sides: the server owns it, but the client keeps a
        // mirror so the ghost box can react on the very next frame without a round trip.
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        boolean server = !context.getLevel().isClientSide();
        Level level = context.getLevel();

        if (getMode(stack) == Mode.TARGET) {
            // Clicking a cannon applies (or clears) the picked origin.
            if (level.getBlockEntity(clicked) instanceof SchematicannonBlockEntity cannon) {
                if (server) {
                    bindTarget(cannon, stack, context.getPlayer());
                }
                return InteractionResult.CONSUME;
            }

            BlockPos point = cornerAt(clicked, context.getClickedFace());
            if (context.isSecondaryUseActive()) {
                // Sneak-right-click clears everything.
                clearTarget(stack);
                announce(context, "schematicannon.quill.cleared", server);
                return InteractionResult.CONSUME;
            }
            setTargetPos(stack, point);

            // A quill that already aimed a cannon keeps aiming it: later clicks re-aim live instead
            // of forcing a walk back to the cannon for every adjustment.
            BlockPos bound = getBoundCannon(stack);
            if (bound != null && level.getBlockEntity(bound) instanceof SchematicannonBlockEntity cannon) {
                if (server) {
                    // Same order as bindTarget: orientation before position, so the final preview
                    // broadcast carries both.
                    cannon.setTransform(getTransform(stack), null);
                    cannon.setPrintTarget(point);
                }
                announce(context, "schematicannon.quill.target_set",
                        new Object[]{point.getX(), point.getY(), point.getZ()}, server);
                return InteractionResult.CONSUME;
            }
            announce(context, "schematicannon.quill.target_pick",
                    new Object[]{point.getX(), point.getY(), point.getZ()}, server);
            return InteractionResult.CONSUME;
        }

        BlockPos first = getFirstPos(stack);
        BlockPos second = getSecondPos(stack);

        if (context.isSecondaryUseActive()) {
            // Sneak-right-click clears everything.
            clearRegion(stack);
            announce(context, "schematicannon.quill.cleared", server);
            return InteractionResult.CONSUME;
        }

        // A complete region releases itself so the next click is a fresh first corner.
        if (first != null && second != null) {
            clearRegion(stack);
            first = null;
        }

        // Clicking the anchored corner again releases it instead of collapsing the box to one block.
        if (first != null && first.equals(clicked)) {
            setFirst(stack, null);
            announce(context, "schematicannon.quill.cleared", server);
            return InteractionResult.CONSUME;
        }

        if (first == null) {
            BlockPos anchor = cornerAt(clicked, context.getClickedFace());
            setFirst(stack, anchor);
            announce(context, "schematicannon.quill.first",
                    new Object[]{anchor.getX(), anchor.getY(), anchor.getZ()}, server);
            return InteractionResult.CONSUME;
        }

        BlockPos corner = cornerAt(clicked, context.getClickedFace());
        setSecond(stack, corner);
        announce(context, "schematicannon.quill.second",
                new Object[]{Math.abs(corner.getX() - first.getX()) + 1,
                        Math.abs(corner.getY() - first.getY()) + 1,
                        Math.abs(corner.getZ() - first.getZ()) + 1}, server);
        return InteractionResult.CONSUME;
    }

    private static void announce(UseOnContext context, String key, boolean server) {
        announce(context, key, new Object[0], server);
    }

    private static void announce(UseOnContext context, String key, Object[] args, boolean server) {
        if (server && context.getPlayer() != null) {
            context.getPlayer().sendSystemMessage(Component.translatable(key, args));
        }
    }

    // ------------------------------------------------------------------- corners

    /**
     * The block touching the clicked face, i.e. one step out of the block into the air.
     *
     * <p>Both corners use this same rule, and so does the live preview, so the box you see while
     * aiming is exactly the box you get. Nothing is clamped onto the first corner's plane: the region
     * is the plain cuboid between the two air blocks, which is what lets it be a real 3D volume.
     */
    public static BlockPos cornerAt(BlockPos clicked, @Nullable Direction face) {
        if (face == null) {
            return clicked;
        }
        return clicked.offset(face.getStepX(), face.getStepY(), face.getStepZ());
    }

    @Nullable
    public static BlockPos aimedPos(Player player) {
        HitResult hit = player.pick(24.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return block.getBlockPos().relative(block.getDirection());
    }

    private static void setFirst(ItemStack stack, @Nullable BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (pos == null) {
                tag.remove(KEY_A);
            } else {
                tag.putIntArray(KEY_A, new int[]{pos.getX(), pos.getY(), pos.getZ()});
            }
        });
    }

    private static void setSecond(ItemStack stack, @Nullable BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (pos == null) {
                tag.remove(KEY_B);
            } else {
                tag.putIntArray(KEY_B, new int[]{pos.getX(), pos.getY(), pos.getZ()});
            }
        });
    }

    // -------------------------------------------------------------------- target

    /** The print origin picked in {@link Mode#TARGET}, or null when nothing is picked yet. */
    @Nullable
    public static BlockPos getTargetPos(ItemStack stack) {
        return stack.isEmpty() ? null : read(tag(stack), KEY_TARGET);
    }

    public static void setTargetPos(ItemStack stack, @Nullable BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (pos == null) {
                tag.remove(KEY_TARGET);
            } else {
                tag.putIntArray(KEY_TARGET, new int[]{pos.getX(), pos.getY(), pos.getZ()});
            }
        });
    }

    /** Forgets the picked origin and the cannon it was last applied to. */
    public static void clearTarget(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(KEY_TARGET);
            tag.remove(KEY_BOUND);
        });
    }

    // --------------------------------------------------------------- orientation

    /** Orientation this quill will hand over. Kept on the item so the player can set it up first. */
    public static SchematicTransform getTransform(ItemStack stack) {
        return stack.isEmpty() ? SchematicTransform.IDENTITY
                : SchematicTransform.decode(tag(stack).getInt(KEY_TRANSFORM).orElse(0));
    }

    public static SchematicTransform setTransform(ItemStack stack, SchematicTransform transform) {
        int encoded = (transform == null ? SchematicTransform.IDENTITY : transform).encode();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(KEY_TRANSFORM, encoded));
        return transform;
    }

    /** Turns the stored orientation one quarter turn. */
    public static SchematicTransform rotate(ItemStack stack, boolean clockwise) {
        SchematicTransform next = clockwise ? getTransform(stack).rotateClockwise()
                : getTransform(stack).rotateCounterClockwise();
        return setTransform(stack, next);
    }
    /** Mirrors the stored orientation horizontally. */
    public static SchematicTransform flip(ItemStack stack) {
        return setTransform(stack, getTransform(stack).flipped());
    }

    /** Back to no rotation, no mirror. */
    public static void resetTransform(ItemStack stack) {
        setTransform(stack, SchematicTransform.IDENTITY);
    }

    /**
     * Which hand holds a quill, for {@link com.schematicannon.network.ModNetwork#sendQuillState}.
     * Returns -1 when neither hand does.
     */
    public static int handOf(Player player) {
        if (player.getMainHandItem().getItem() instanceof SchematicAndQuillItem) {
            return 0;
        }
        return player.getOffhandItem().getItem() instanceof SchematicAndQuillItem ? 1 : -1;
    }

    @Nullable
    public static BlockPos getBoundCannon(ItemStack stack) {
        return stack.isEmpty() ? null : read(tag(stack), KEY_BOUND);
    }

    public static void setBoundCannon(ItemStack stack, @Nullable BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (pos == null) {
                tag.remove(KEY_BOUND);
            } else {
                tag.putIntArray(KEY_BOUND, new int[]{pos.getX(), pos.getY(), pos.getZ()});
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        if (getMode(stack) == Mode.TARGET) {
            lines.accept(Component.translatable("schematicannon.tooltip.mode_target"));
            BlockPos point = getTargetPos(stack);
            if (point != null) {
                lines.accept(Component.translatable("schematicannon.tooltip.target_point",
                        point.getX(), point.getY(), point.getZ()));
            } else {
                lines.accept(Component.translatable("schematicannon.tooltip.target_none"));
            }
            SchematicTransform transform = getTransform(stack);
            lines.accept(Component.translatable("schematicannon.tooltip.transform",
                    transform.describe(), transform.compass()));
            lines.accept(Component.translatable("schematicannon.tooltip.transform_keys"));
            return;
        }
        lines.accept(Component.translatable("schematicannon.tooltip.mode_select"));        BlockPos a = getFirstPos(stack);
        BlockPos b = getSecondPos(stack);
        if (a != null && b != null) {
            int x = Math.abs(b.getX() - a.getX()) + 1;
            int y = Math.abs(b.getY() - a.getY()) + 1;
            int z = Math.abs(b.getZ() - a.getZ()) + 1;
            lines.accept(Component.translatable("schematicannon.tooltip.size", x, y, z, x * y * z));
        } else if (a != null) {
            lines.accept(Component.translatable("schematicannon.quill.first_set"));
        } else {
            lines.accept(Component.translatable("schematicannon.quill.usage"));
        }
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    @Nullable
    private static BlockPos read(CompoundTag tag, String key) {
        int[] arr = tag.getIntArray(key).orElse(null);
        if (arr == null || arr.length < 3) {
            return null;
        }
        return new BlockPos(arr[0], arr[1], arr[2]);
    }

    @Nullable
    public static BlockPos getFirstPos(ItemStack stack) {
        return stack.isEmpty() ? null : read(tag(stack), KEY_A);
    }

    @Nullable
    public static BlockPos getSecondPos(ItemStack stack) {
        return stack.isEmpty() ? null : read(tag(stack), KEY_B);
    }

    public static void clearRegion(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(KEY_A);
            tag.remove(KEY_B);
        });
    }
}
