package com.circulation.circulation_networks.mixins.mc;

import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.proxy.CommonProxy;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(Chunk.class)
public class MixinChunk {

    @Shadow
    @Final
    private Map<BlockPos, TileEntity> tileEntities;

    @Shadow
    @Final
    private World world;

    @Inject(method = "addTileEntity(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;validate()V", shift = At.Shift.AFTER))
    private void addTileEntity(BlockPos pos, TileEntity tileEntity, CallbackInfo ci) {
        if (world.isRemote) return;
        NetworkManager.INSTANCE.addNode(
            tileEntity.getCapability(CommonProxy.nodeCapability, null));
        EnergyMachineManager.INSTANCE.addMachine(tileEntity);
    }

    @Inject(method = "addTileEntity(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;invalidate()V", shift = At.Shift.AFTER))
    private void addTileEntityRemove(BlockPos pos, TileEntity tileEntity, CallbackInfo ci) {
        if (world.isRemote) return;
        NetworkManager.INSTANCE.removeNode(
            tileEntity.getCapability(CommonProxy.nodeCapability, null));
        EnergyMachineManager.INSTANCE.removeMachine(this.tileEntities.get(pos));
    }

    @Inject(method = "removeTileEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;invalidate()V", shift = At.Shift.AFTER))
    private void removeTileEntity(BlockPos pos, CallbackInfo ci, @Local(name = "tileentity") TileEntity tileEntity) {
        if (world.isRemote) return;
        NetworkManager.INSTANCE.removeNode(
            tileEntity.getCapability(CommonProxy.nodeCapability, null));
        EnergyMachineManager.INSTANCE.removeMachine(tileEntity);
    }
}