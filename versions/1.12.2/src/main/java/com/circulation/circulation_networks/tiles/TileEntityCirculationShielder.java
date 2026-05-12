package com.circulation.circulation_networks.tiles;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
import com.circulation.circulation_networks.container.CFNBaseContainer;
import com.circulation.circulation_networks.container.ContainerCirculationShielder;
import com.circulation.circulation_networks.gui.GuiCirculationShielder;
import com.circulation.circulation_networks.handlers.CirculationShielderRenderingHandler;
import com.circulation.circulation_networks.manager.CirculationShielderManager;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

public class TileEntityCirculationShielder extends BlockTickTileEntity implements ICirculationShielderBlockEntity {

    private transient final BlockPos.MutableBlockPos min = new BlockPos.MutableBlockPos();
    private transient final BlockPos.MutableBlockPos max = new BlockPos.MutableBlockPos();
    private int scope;
    private boolean redstoneMode = false;
    private boolean showingRange = false;
    private boolean cachedActive;
    private long cachedActiveTick = Long.MIN_VALUE;

    public int getScope() {
        return scope;
    }

    public void setScope(int scope) {
        int maxScope = Math.max(0, getMaxScope());
        int clamped = Math.max(0, Math.min(maxScope, scope));
        this.min.setPos(this.getPos().getX() - clamped, this.getPos().getY() - clamped, this.getPos().getZ() - clamped);
        this.max.setPos(this.getPos().getX() + clamped, this.getPos().getY() + clamped, this.getPos().getZ() + clamped);
        this.scope = clamped;
    }

    @Override
    public int getMaxScope() {
        return CFNConfig.SHIELDER.maxScope;
    }

    public boolean isShowingRange() {
        return showingRange;
    }

    public void setShowingRange(boolean showingRange) {
        this.showingRange = showingRange;
    }

    @Override
    public BlockPos getBEPos() {
        return getPos();
    }

    @Override
    public boolean hasGui() {
        return true;
    }

    @Override
    @NotNull
    public CFNBaseContainer getContainer(EntityPlayer player) {
        return new ContainerCirculationShielder(player, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiContainer getGui(EntityPlayer player) {
        return new GuiCirculationShielder(player, this);
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("scope", this.scope);
        compound.setBoolean("RedstoneMode", this.redstoneMode);
        compound.setBoolean("CachedActive", this.cachedActive);
        return compound;
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound compound) {
        super.readFromNBT(compound);
        setScope(compound.getInteger("scope"));
        this.redstoneMode = compound.getBoolean("RedstoneMode");
        this.cachedActive = compound.getBoolean("CachedActive");
        refreshActiveCache();
    }

    public boolean checkScope(BlockPos pos) {
        return min.getX() <= pos.getX() && min.getY() <= pos.getY() && min.getZ() <= pos.getZ()
            && max.getX() >= pos.getX() && max.getY() >= pos.getY() && max.getZ() >= pos.getZ();
    }

    public boolean isActive() {
        return cachedActive;
    }

    public boolean isReceivingRedstoneSignal() {
        if (world == null) {
            return false;
        }
        return world.isBlockPowered(pos);
    }

    private void refreshActiveCache() {
        if (world == null) {
            cachedActive = false;
            cachedActiveTick = Long.MIN_VALUE;
            return;
        }
        boolean oldActive = cachedActive;
        cachedActive = redstoneMode == world.isBlockPowered(pos);
        cachedActiveTick = world.getTotalWorldTime();
        if (!world.isRemote && oldActive != cachedActive) {
            CirculationShielderManager.INSTANCE.refreshActiveState(this, world.provider.getDimension());
        }
    }

    @Override
    public void serverUpdate() {
        long time = world.getTotalWorldTime();
        if (cachedActiveTick == Long.MIN_VALUE || time - cachedActiveTick >= 5) {
            refreshActiveCache();
        }
    }

    public boolean getRedstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(boolean mode) {
        this.redstoneMode = mode;
        markDirty();
        refreshActiveCache();
    }

    @Override
    public void validate() {
        super.validate();
        setScope(scope);
        if (world.isRemote) {
            clientRegister();
        } else {
            CirculationShielderManager.INSTANCE.register(this, world.provider.getDimension());
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null) {
            if (world.isRemote) {
                clientUnregister();
            } else {
                CirculationShielderManager.INSTANCE.unregister(this, world.provider.getDimension());
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void clientRegister() {
        CirculationShielderRenderingHandler.INSTANCE.addShielder(this);
    }

    @SideOnly(Side.CLIENT)
    private void clientUnregister() {
        CirculationShielderRenderingHandler.INSTANCE.removeShielder(this);
    }
}
