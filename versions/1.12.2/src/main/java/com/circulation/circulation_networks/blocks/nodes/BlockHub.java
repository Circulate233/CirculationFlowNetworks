package com.circulation.circulation_networks.blocks.nodes;

import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.client.render.HubRenderLayout;
import com.circulation.circulation_networks.items.BaseItemTooltipModel;
import com.circulation.circulation_networks.registry.CFNBlocks;
import com.circulation.circulation_networks.tiles.TileEntityMultiblockShell;
import com.circulation.circulation_networks.tiles.nodes.TileEntityHub;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import com.circulation.circulation_networks.utils.CI18n;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
@ParametersAreNonnullByDefault
public final class BlockHub extends BaseNodeBlock {

    private static final Long2ObjectMap<BlockPos[]> positions = new Long2ObjectOpenHashMap<>();
    private List<LocalizedComponent> cachedHubTooltips;

    public BlockHub() {
        super("hub");
        this.setNodeTileClass(TileEntityHub.class);
    }

    private static BlockPos[] shellPositions(BlockPos origin) {
        if (!positions.containsKey(origin.toLong())) {
            var o = HubRenderLayout.shellOffsets();
            var list = new BlockPos[o.length];
            positions.put(origin.toLong(), list);
            for (var i = 0; i < o.length; i++) {
                var offset = o[i];
                list[i] = origin.add(offset.x, offset.y, offset.z);
            }
            return list;
        }
        return positions.get(origin.toLong());
    }

    private static boolean canReplaceOccupiedPosition(World world, BlockPos pos) {
        IBlockState occupiedState = world.getBlockState(pos);
        if (occupiedState.getBlock() instanceof MultiblockShellBlock) {
            return MultiblockShellBlock.canBeReplacedAt(world, pos);
        }
        return occupiedState.getBlock().isReplaceable(world, pos);
    }

    @Override
    public @NotNull EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected List<LocalizedComponent> buildTooltips(ItemStack stack) {
        if (cachedHubTooltips == null) {
            String[] tooltipKeys = BaseItemTooltipModel.moveFirstTooltipKeyToEnd(
                BaseItemTooltipModel.resolveTooltipKeys(getTranslationKey(), CI18n::hasKey)
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

    @Override
    public boolean canPlaceBlockAt(World worldIn, BlockPos pos) {
        if (!canReplaceOccupiedPosition(worldIn, pos)) return false;
        for (BlockPos shellPos : shellPositions(pos)) {
            if (shellPos.getY() < 0 || shellPos.getY() > 255) return false;
            if (!canReplaceOccupiedPosition(worldIn, shellPos)) return false;
        }
        return super.canPlaceBlockAt(worldIn, pos);
    }

    @NotNull
    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    @Override
    public boolean hasGui() {
        return true;
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        if (worldIn.getBlockState(pos).getBlock() != this) {
            return;
        }
        if (placer instanceof EntityPlayer player && !worldIn.isRemote) {
            var te = worldIn.getTileEntity(pos);
            if (te instanceof TileEntityHub hub) {
                IHubNode node = hub.getNode();
                if (node != null) {
                    node.setOwner(player.getUniqueID());
                }
            }
        }
        if (!worldIn.isRemote) {
            var shellState = CFNBlocks.blockMultiblockShell.getDefaultState();
            for (BlockPos shellPos : shellPositions(pos)) {
                worldIn.setBlockState(shellPos, shellState, 3);
                var shellTE = worldIn.getTileEntity(shellPos);
                if (shellTE instanceof TileEntityMultiblockShell shell) {
                    shell.setOriginPos(pos);
                }
            }
        }
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        var te = worldIn.getTileEntity(pos);
        if (te instanceof TileEntityHub hub) {
            var inv = hub.getPlugins();
            for (int i = 0; i < inv.getSlots(); i++) {
                var plugin = inv.getStackInSlot(i);
                if (!plugin.isEmpty()) {
                    worldIn.spawnEntity(new EntityItem(worldIn,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        plugin.copy()));
                }
            }
        }
        for (BlockPos shellPos : shellPositions(pos)) {
            if (worldIn.getBlockState(shellPos).getBlock() instanceof MultiblockShellBlock) {
                worldIn.setBlockToAir(shellPos);
            }
        }
        positions.remove(pos.toLong());
        super.breakBlock(worldIn, pos, state);
    }

}
