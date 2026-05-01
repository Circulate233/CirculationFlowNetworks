package com.circulation.circulation_networks.blocks.nodes;

import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.client.render.HubRenderLayout;
import com.circulation.circulation_networks.items.BaseItemTooltipModel;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.CFNBlocks;
import com.circulation.circulation_networks.tiles.MultiblockShellBlockEntity;
import com.circulation.circulation_networks.tiles.nodes.HubBlockEntity;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import com.circulation.circulation_networks.utils.CI18n;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class BlockHub extends BaseNodeBlock {

    private static final Long2ObjectMap<BlockPos[]> positions = new Long2ObjectOpenHashMap<>();
    private List<LocalizedComponent> cachedHubTooltips;

    public BlockHub() {
        super(metalPropertiesNoOcclusion().pushReaction(PushReaction.BLOCK),
            () -> CFNBlockEntityTypes.HUB);
    }

    private static BlockPos[] shellPositions(BlockPos origin) {
        if (!positions.containsKey(origin.asLong())) {
            var o = HubRenderLayout.shellOffsets();
            var list = new BlockPos[o.length];
            positions.put(origin.asLong(), list);
            for (var i = 0; i < o.length; i++) {
                var offset = o[i];
                list[i] = origin.offset(offset.x, offset.y, offset.z);
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
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected List<LocalizedComponent> buildTooltips(ItemStack stack) {
        if (cachedHubTooltips == null) {
            String[] tooltipKeys = BaseItemTooltipModel.moveFirstTooltipKeyToEnd(
                BaseItemTooltipModel.resolveTooltipKeys(getDescriptionId(), CI18n::hasKey)
            );
            if (tooltipKeys.length == 0) {
                cachedHubTooltips = Collections.emptyList();
            } else {
                List<LocalizedComponent> result = new ObjectArrayList<>(tooltipKeys.length);
                for (String key : tooltipKeys) {
                    result.add(LocalizedComponent.of(key));
                }
                cachedHubTooltips = result;
            }
        }
        return cachedHubTooltips;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        if (origin.getY() < level.getMinBuildHeight() || origin.getY() >= level.getMaxBuildHeight()) {
            return null;
        }
        if (!canReplaceOccupiedPosition(level, origin, context)) {
            return null;
        }
        for (BlockPos shellPos : shellPositions(origin)) {
            if (shellPos.getY() < level.getMinBuildHeight() || shellPos.getY() >= level.getMaxBuildHeight())
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
            if (be instanceof HubBlockEntity hub) {
                IHubNode node = hub.getNode();
                if (node != null) {
                    node.setOwner(player.getUUID());
                }
            }
        }
        if (!level.isClientSide()) {
            var shellState = CFNBlocks.blockMultiblockShell.defaultBlockState();
            for (BlockPos shellPos : shellPositions(pos)) {
                level.setBlock(shellPos, shellState, 3);
                var shellBE = level.getBlockEntity(shellPos);
                if (shellBE instanceof MultiblockShellBlockEntity shell) {
                    shell.setOriginPos(pos);
                }
            }
        }
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            var be = level.getBlockEntity(pos);
            if (be instanceof HubBlockEntity hub) {
                var inv = hub.getPlugins();
                for (int i = 0; i < inv.getSlots(); i++) {
                    var plugin = inv.getStackInSlot(i);
                    if (!plugin.isEmpty()) {
                        level.addFreshEntity(new ItemEntity(level,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            plugin.copy()));
                    }
                }
            }
            for (BlockPos shellPos : shellPositions(pos)) {
                if (level.getBlockState(shellPos).getBlock() instanceof MultiblockShellBlock) {
                    level.removeBlock(shellPos, false);
                }
            }
            positions.remove(pos.asLong());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
