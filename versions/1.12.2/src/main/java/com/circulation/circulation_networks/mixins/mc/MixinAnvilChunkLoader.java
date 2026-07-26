package com.circulation.circulation_networks.mixins.mc;

import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnvilChunkLoader.class)
public abstract class MixinAnvilChunkLoader {

    @Unique
    private static final String CFN_ENERGY_PRIORITY_KEY = "circulation_networks:energy_priority";

    @Redirect(
        method = "writeChunkToNBT(Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/World;Lnet/minecraft/nbt/NBTTagCompound;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/tileentity/TileEntity;writeToNBT(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;"
        ),
        require = 1
    )
    private NBTTagCompound cfn$saveEnergyPriority(TileEntity blockEntity, NBTTagCompound compound) {
        NBTTagCompound result = blockEntity.writeToNBT(compound);
        int priority = CFNBlockEntityEx.cfn_cast(blockEntity).cfn_getEnergyPriority();
        if (priority == 0) {
            result.removeTag(CFN_ENERGY_PRIORITY_KEY);
        } else {
            result.setInteger(CFN_ENERGY_PRIORITY_KEY, priority);
        }
        return result;
    }
}
