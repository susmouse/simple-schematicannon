package com.schematicannon.block;

import com.mojang.serialization.MapCodec;
import com.schematicannon.ModRegistry;
import com.schematicannon.blockentity.SchematicTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class SchematicTableBlock extends Block implements EntityBlock {

    public static final MapCodec<SchematicTableBlock> CODEC = simpleCodec(SchematicTableBlock::new);

    public SchematicTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SchematicTableBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SchematicTableBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SchematicTableBlockEntity be) {
            player.openMenu(be);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack destroyedWith) {
        if (!level.isClientSide() && blockEntity instanceof SchematicTableBlockEntity be) {
            net.minecraft.world.Containers.dropContents(level, pos, be.getInventory());
        }
        super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
    }
}
