package com.circulation.circulation_networks.mixins.mc;

import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.utils.BlockEntityLifecycleHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = LevelChunk.class, remap = false)
public abstract class MixinLevelChunk extends ChunkAccess {

    @Shadow
    @Final
    private Level level;

    public MixinLevelChunk(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, PalettedContainerFactory containerFactory, long inhabitedTime, LevelChunkSection @Nullable [] sections, @Nullable BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, containerFactory, inhabitedTime, sections, blendingData);
    }

    @Shadow
    public abstract Map<BlockPos, BlockEntity> getBlockEntities();

    @Inject(method = "addAndRegisterBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshBlockEntities(Ljava/util/Collection;)V", shift = At.Shift.AFTER))
    public void addAndRegisterBlockEntity(BlockEntity blockEntity, CallbackInfo ci) {
        if (blockEntity != null) {
            BlockEntityLifecycleHooks.onValidate(this.level, blockEntity.getBlockPos(), blockEntity);
        }
    }

    @Inject(method = "registerAllBlockEntitiesAfterLevelLoad", at = @At("TAIL"))
    private void registerAllBlockEntitiesAfterLevelLoad(CallbackInfo ci) {
        for (BlockEntity blockEntity : this.getBlockEntities().values()) {
            if (blockEntity != null && !blockEntity.isRemoved()) {
                BlockEntityLifecycleHooks.onValidate(this.level, blockEntity.getBlockPos(), blockEntity);
            }
        }
    }

    @Inject(method = "setBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;setRemoved()V", shift = At.Shift.BEFORE))
    private void setBlockEntity(BlockEntity blockEntity, CallbackInfo ci, @Local(name = "previousEntry") BlockEntity previousEntry, @Local(name = "pos") BlockPos pos) {
        BlockEntityLifecycleHooks.onInvalidate(this.level, pos, previousEntry);
    }

    @Inject(method = "removeBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;setRemoved()V", shift = At.Shift.BEFORE))
    private void removeBlockEntity(BlockPos blockPos, CallbackInfo ci, @Local(name = "removeThis") BlockEntity removeThis) {
        BlockEntityLifecycleHooks.onInvalidate(this.level, blockPos, removeThis);
    }

    @Inject(method = "setBlockState", at = @At("TAIL"))
    private void setBlockState(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        if (!this.level.isClientSide() && cir.getReturnValue() != null) {
            PocketNodeManager.INSTANCE.onHostBlockStateChanged(this.level, pos);
        }
    }
}
