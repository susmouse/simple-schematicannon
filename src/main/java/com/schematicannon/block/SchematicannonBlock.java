package com.schematicannon.block;

import com.mojang.serialization.MapCodec;
import com.schematicannon.ModRegistry;
import com.schematicannon.blockentity.SchematicannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class SchematicannonBlock extends Block implements EntityBlock {

    public static final MapCodec<SchematicannonBlock> CODEC = simpleCodec(SchematicannonBlock::new);

    public SchematicannonBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SchematicannonBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SchematicannonBlockEntity(ModRegistry.SCHEMATICANNON_BE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() && type == ModRegistry.SCHEMATICANNON_BE.get()) {
            return (level1, pos, state1, blockEntity) -> {
                if (blockEntity instanceof SchematicannonBlockEntity be) {
                    SchematicannonBlockEntity.serverTick(level1, pos, state1, be);
                }
            };
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof SchematicannonBlockEntity be) {
                player.openMenu(be);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack destroyedWith) {
        if (!level.isClientSide() && blockEntity instanceof SchematicannonBlockEntity be) {
            net.minecraft.world.Containers.dropContents(level, pos, be.getInventory());
        }
        super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
    }
}
