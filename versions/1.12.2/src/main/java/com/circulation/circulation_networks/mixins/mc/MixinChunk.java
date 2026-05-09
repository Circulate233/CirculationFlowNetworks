package com.circulation.circulation_networks.mixins.mc;

import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.utils.BlockEntityLifecycleHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.state.IBlockState;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(Chunk.class)
public class MixinChunk {

    @Shadow
    @Final
    private Map<BlockPos, TileEntity> tileEntities;

    @Shadow
    @Final
    private World world;

    @Inject(method = "addTileEntity(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.AFTER, remap = false))
    private void addTileEntity(BlockPos pos, TileEntity tileEntityIn, CallbackInfo ci) {
        BlockEntityLifecycleHooks.postValidate(world, pos, tileEntityIn);
    }

    @Inject(method = "addTileEntity(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;invalidate()V", shift = At.Shift.AFTER))
    private void addTileEntityRemove(BlockPos pos, TileEntity tileEntityIn, CallbackInfo ci) {
        BlockEntityLifecycleHooks.postInvalidate(world, pos, this.tileEntities.get(pos));
    }

    @Inject(method = "removeTileEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;invalidate()V", shift = At.Shift.AFTER))
    private void removeTileEntity(BlockPos pos, CallbackInfo ci, @Local(name = "tileentity") TileEntity tileentity) {
        BlockEntityLifecycleHooks.postInvalidate(world, pos, tileentity);
    }

    @Inject(method = "setBlockState", at = @At("TAIL"))
    private void setBlockState(BlockPos pos, IBlockState state, CallbackInfoReturnable<IBlockState> cir) {
        if (!this.world.isRemote && cir.getReturnValue() != null) {
            PocketNodeManager.INSTANCE.onHostBlockStateChanged(this.world, pos);
        }
    }
}
