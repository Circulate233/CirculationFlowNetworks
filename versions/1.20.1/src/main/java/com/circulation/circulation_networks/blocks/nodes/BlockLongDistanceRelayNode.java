package com.circulation.circulation_networks.blocks.nodes;

import com.circulation.circulation_networks.handlers.LongDistanceRelayInteraction;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.tiles.nodes.LongDistanceRelayNodeBlockEntity;
import com.circulation.circulation_networks.utils.MultiblockStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public final class BlockLongDistanceRelayNode extends PedestalRequiredNodeBlock {
    public BlockLongDistanceRelayNode() {
        super(metalPropertiesNoOcclusion().pushReaction(PushReaction.BLOCK),
            () -> CFNBlockEntityTypes.LONG_DISTANCE_RELAY_NODE);
    }

    private static BlockPos[] shells(BlockPos origin) {
        return new BlockPos[] {origin.above(), origin.above(2)};
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        BlockState state = super.getStateForPlacement(context);
        if (state == null || !state.canSurvive(level, origin)
            || !MultiblockStructure.canReplace(level, origin, context)) return null;
        for (BlockPos shell : shells(origin)) {
            if (!MultiblockStructure.canReplace(level, shell, context)) return null;
        }
        return state;
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockState(pos).is(this)) MultiblockStructure.place(level, pos, shells(pos));
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) MultiblockStructure.remove(level, pos, shells(pos));
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) return InteractionResult.PASS;
        if (player.getMainHandItem().getItem() == CFNItems.circulationConfigurator) {
            return super.use(state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LongDistanceRelayNodeBlockEntity relay) {
            relay.syncNodeAfterNetworkInit();
            player.displayClientMessage(Component.translatable(LongDistanceRelayInteraction.select(player, relay.getNode())), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
