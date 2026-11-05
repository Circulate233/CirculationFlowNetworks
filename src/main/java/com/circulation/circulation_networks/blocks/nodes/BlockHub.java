package com.circulation.circulation_networks.blocks.nodes;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.client.render.HubRenderLayout;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityHub;
import com.circulation.circulation_networks.utils.MultiblockStructure;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BlockHub extends BaseNodeBlock {

    private static final Long2ObjectMap<BlockPos[]> positions = new Long2ObjectOpenHashMap<>();

    public BlockHub(ResourceKey<Block> id) {
        super(metalPropertiesNoOcclusion().setId(id).pushReaction(PushReaction.BLOCK),
            () -> CFNBlockEntityTypes.HUB);
    }

    private static BlockPos[] shellPositions(BlockPos origin) {
        if (!positions.containsKey(origin.asLong())) {
            var o = HubRenderLayout.shellOffsets();
            var list = new BlockPos[o.length];
            positions.put(origin.asLong(), list);
            for (var i = 0; i < o.length; i++) {
                var offset = o[i];
                list[i] = origin.offset(offset.x(), offset.y(), offset.z());
            }
            return list;
        }
        return positions.get(origin.asLong());
    }

    private static boolean canReplaceOccupiedPosition(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockPlaceContext context) {
        BlockState occupiedState = level.getBlockState(pos);
        if (occupiedState.getBlock() instanceof MultiblockShellBlock) {
            return MultiblockShellBlock.canBeReplacedAt(level, pos);
        }
        return occupiedState.canBeReplaced(context);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return CFNConfig.NODE.rendering.animatedSpecialModels
            ? RenderShape.INVISIBLE
            : RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        if (level.isOutsideBuildHeight(origin)) {
            return null;
        }
        if (!canReplaceOccupiedPosition(level, origin, context)) {
            return null;
        }
        for (BlockPos shellPos : shellPositions(origin)) {
            if (level.isOutsideBuildHeight(shellPos))
                return null;
            if (!canReplaceOccupiedPosition(level, shellPos, context)) return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public boolean hasGui() {
        return true;
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        if (placer instanceof Player player && !level.isClientSide()) {
            var be = level.getBlockEntity(pos);
            if (be instanceof BlockEntityHub hub) {
                IHubNode node = hub.getNode();
                if (node != null) {
                    node.setOwner(player.getUUID());
                }
            }
        }
        MultiblockStructure.place(level, pos, shellPositions(pos));
    }

    @Override
    public @NotNull BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos,
                                                 @NotNull BlockState state, @NotNull Player player) {
        if (!level.isClientSide()) {
            var be = level.getBlockEntity(pos);
            if (be instanceof BlockEntityHub hub) {
                var inv = hub.getPlugins();
                for (var plugin : inv) {
                    if (!plugin.isEmpty()) {
                        level.addFreshEntity(new ItemEntity(level,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            plugin.copy()));
                    }
                }
            }
            MultiblockStructure.remove(level, pos, shellPositions(pos));
        }
        positions.remove(pos.asLong());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(@NotNull BlockState state, @NotNull ServerLevel level,
                                               @NotNull BlockPos pos, boolean movedByPiston) {
        MultiblockStructure.remove(level, pos, shellPositions(pos));
        positions.remove(pos.asLong());
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
