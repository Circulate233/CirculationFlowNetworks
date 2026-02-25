package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.api.INodeTileEntity;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.proxy.CommonProxy;
import com.circulation.circulation_networks.tiles.BaseTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;

public abstract class BaseNodeTileEntity extends BaseTileEntity implements INodeTileEntity {

    private INode node;

    @Override
    public @NotNull INode getNode() {
        return node;
    }

    @Nonnull
    protected abstract INode createNode();

    @Override
    public final void invalidate() {
        super.invalidate();
        if (!this.world.isRemote) {
            onInvalidate();
        } else {
            onClientInvalidate();
        }
    }

    @Override
    public final void validate() {
        super.validate();
        if (!this.world.isRemote) {
            onValidate();
        } else {
            onClientValidate();
        }
    }

    protected void onInvalidate() {

    }

    @SideOnly(Side.CLIENT)
    protected void onClientInvalidate() {

    }

    protected void onValidate() {
        if (node == null) {
            var n = createNode();
            if ((node = NetworkManager.INSTANCE.getNodeFromPos(world, pos)) == null) {
                node = n;
            } else if (node.getClass() != n.getClass()) {
                NetworkManager.INSTANCE.removeNode(world.provider.getDimension(), pos);
                node = n;
            }
        }
        node.setActive(true);
    }

    @SideOnly(Side.CLIENT)
    protected void onClientValidate() {

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
    public boolean hasCapability(@NotNull Capability<?> capability, @Nullable EnumFacing facing) {
        if (world.isRemote) return capability == CommonProxy.nodeCapability || super.hasCapability(capability, facing);
        return (capability == CommonProxy.nodeCapability && node != null) || super.hasCapability(capability, facing);
    }

    @Override
    public @Nullable <T> T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == CommonProxy.nodeCapability && node != null ? CommonProxy.nodeCapability.cast(node) : super.getCapability(capability, facing);
    }

}
