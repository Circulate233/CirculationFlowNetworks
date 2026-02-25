package com.circulation.circulation_networks.tiles;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

//TODO:黑名单未完成
public class TileEntityPhaseInterrupter extends BaseTileEntity {

    private transient final BlockPos.MutableBlockPos min = new BlockPos.MutableBlockPos();
    private transient final BlockPos.MutableBlockPos max = new BlockPos.MutableBlockPos();
    private int scope;

    @Override
    public boolean hasGui() {
        return false;
    }

    public void setScope(int scope) {
        this.min.setPos(this.getPos().getX() - scope, this.getPos().getY() - scope, this.getPos().getZ() - scope);
        this.max.setPos(this.getPos().getX() + scope, this.getPos().getY() + scope, this.getPos().getZ() + scope);
        this.scope = scope;
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("scope", this.scope);
        return compound;
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound compound) {
        super.readFromNBT(compound);
        setScope(compound.getInteger("scope"));
    }

    public boolean checkScope(BlockPos pos) {
        return min.getX() <= pos.getX() && min.getY() <= pos.getY() && min.getZ() <= pos.getZ()
            && max.getX() >= pos.getX() && max.getY() >= pos.getY() && max.getZ() >= pos.getZ();
    }

}
