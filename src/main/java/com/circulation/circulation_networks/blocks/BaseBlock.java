package com.circulation.circulation_networks.blocks;

import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BaseBlock extends Block implements EntityBlock {

    protected BaseBlock(Properties properties) {
        super(properties);
    }

    protected static BlockBehaviour.Properties metalProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 6.0f);
    }

    protected static BlockBehaviour.Properties metalPropertiesNoOcclusion() {
        return metalProperties().noOcclusion();
    }

    protected List<LocalizedComponent> buildTooltips(ItemStack stack) {
        return new ObjectArrayList<>();
    }

    public abstract boolean hasGui();

    @Override
    protected @NotNull List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder builder) {
        return ObjectLists.singleton(new ItemStack(this));
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        if (!hasGui()) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MenuProvider menuProvider) {
            return menuProvider;
        }
        return null;
    }

    @Override
    protected @NotNull InteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level,
                                                   @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
                                                   @NotNull BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND
            && !player.isShiftKeyDown()
            && stack.getItem() == CFNItems.circulationConfigurator) {
            if (level.isClientSide()) {
                if (API.getNodeAt(level, pos) != null) {
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.SUCCESS;
            } else if (PocketNodeManager.INSTANCE.removePocketNode(level, pos, true)) {
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull BlockHitResult hit) {
        MenuProvider menuProvider = state.getMenuProvider(level, pos);
        if (menuProvider == null) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(menuProvider, pos);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return null;
    }
}
