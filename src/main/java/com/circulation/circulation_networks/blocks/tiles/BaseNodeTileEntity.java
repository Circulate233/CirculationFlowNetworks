package com.circulation.circulation_networks.blocks.tiles;

import com.circulation.circulation_networks.api.INodeTileEntity;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.proxy.CommonProxy;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseNodeTileEntity extends TileEntity implements INodeTileEntity {

    private INode node;

    @Override
    public @NotNull INode getNode() {
        return node;
    }

    protected abstract INode createNode();

    @Override
    public final void invalidate() {
        super.invalidate();
        onInvalidate();
    }

    @Override
    public final void validate() {
        super.validate();
        onValidate();
    }

    protected void onInvalidate() {

    }

    protected void onValidate() {
        if (!this.world.isRemote) {
            if (node == null)
                node = createNode();
            node.setActive(true);
        }
    }

    @Override
    public @NotNull World getWorld() {
        return world;
    }

    @Override
    public @NotNull BlockPos getNodePos() {
        return this.pos;
    }

    @Override
    public World getNodeWorld() {
        return this.world;
    }

    @Override
    public final double getLinkScope() {
        return node.getLinkScope();
    }

    @Override
    public boolean hasCapability(@NotNull Capability<?> capability, @Nullable EnumFacing facing) {
        return (capability == CommonProxy.nodeCapability && node != null) || super.hasCapability(capability, facing);
    }

    @Override
    public @Nullable <T> T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == CommonProxy.nodeCapability && node != null ? CommonProxy.nodeCapability.cast(node) : super.getCapability(capability, facing);
    }

}
