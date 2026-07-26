package com.circulation.circulation_networks.mixins.mc;

import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = BlockEntity.class, remap = false)
public abstract class MixinBlockEntity implements CFNBlockEntityEx {

    @Unique
    private static final String CFN_ENERGY_PRIORITY_KEY = "circulation_networks:energy_priority";

    @Unique
    private EnergyMachineManager.MachineHandlerRuntime cfn$machineHandlerRuntime;

    @Unique
    private int cfn$energyThrottleTimer;

    @Unique
    private int cfn$energyLastThrottleTimer;

    @Unique
    private int cfn$energyPriority;

    @Shadow
    public abstract Level getLevel();

    @Shadow
    public abstract BlockPos getBlockPos();

    @Shadow
    public abstract boolean isRemoved();

    @Shadow
    public abstract void setChanged();

    @Inject(
        method = "saveWithoutMetadata(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
        at = @At("RETURN"),
        require = 1
    )
    private void cfn$saveEnergyPriority(HolderLookup.Provider registries,
                                        CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag tag = callback.getReturnValue();
        if (cfn$energyPriority == 0) {
            tag.remove(CFN_ENERGY_PRIORITY_KEY);
        } else {
            tag.putInt(CFN_ENERGY_PRIORITY_KEY, cfn$energyPriority);
        }
    }

    @Inject(
        method = "loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
        at = @At("TAIL"),
        require = 1
    )
    private void cfn$loadEnergyPriority(CompoundTag tag,
                                        HolderLookup.Provider registries,
                                        CallbackInfo callback) {
        cfn_loadEnergyPriority(tag.getInt(CFN_ENERGY_PRIORITY_KEY));
    }

    @Override
    @Unique
    public Level cfn_getWorld() {
        return getLevel();
    }

    @Override
    @Unique
    public int cfn_getDimensionId() {
        var world = getLevel();
        if (world == null) {
            throw new IllegalStateException("Cannot resolve dimension for a block entity without a world");
        }
        return world.dimension().location().hashCode();
    }

    @Override
    @Unique
    public String cfn_getTypeName() {
        return getClass().getName();
    }

    @Override
    @Unique
    public BlockPos cfn_getBlockPos() {
        return getBlockPos();
    }

    @Override
    @Unique
    public boolean cfn_isRemoved() {
        return isRemoved();
    }

    @Override
    @Unique
    public @Nullable IEnergyHandlerManager cfn_getEnergyManager(@Nullable IEnergyHandlerManager excludedManager) {
        var blockEntity = (BlockEntity) (Object) this;
        return excludedManager == null
            ? RegistryEnergyHandler.getEnergyManager(blockEntity)
            : RegistryEnergyHandler.getEnergyManagerExcluding(blockEntity, excludedManager);
    }

    @Override
    @Unique
    public boolean cfn_isEnergyBlacklisted() {
        return RegistryEnergyHandler.isBlack((BlockEntity) (Object) this);
    }

    @Override
    @Unique
    public boolean cfn_isSupplyBlacklisted() {
        return RegistryEnergyHandler.isSupplyBlack((BlockEntity) (Object) this);
    }

    @Override
    @Unique
    public void cfn_bindEnergyHandler(IEnergyHandler handler, HandlerInvalidationSink invalidationSink) {
        handler.bindBlockEntity((BlockEntity) (Object) this, invalidationSink);
    }

    @Override
    @Unique
    public @Nullable EnergyMachineManager.MachineHandlerRuntime cfn_getMachineHandlerRuntime() {
        return cfn$machineHandlerRuntime;
    }

    @Override
    @Unique
    public void cfn_installMachineHandlerRuntime(EnergyMachineManager.MachineHandlerRuntime runtime) {
        if (cfn$machineHandlerRuntime != null) {
            throw new IllegalStateException("Machine handler runtime was overwritten without removal");
        }
        cfn$machineHandlerRuntime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    @Unique
    public @Nullable EnergyMachineManager.MachineHandlerRuntime cfn_removeMachineHandlerRuntime() {
        var runtime = cfn$machineHandlerRuntime;
        cfn$machineHandlerRuntime = null;
        return runtime;
    }

    @Override
    @Unique
    public int cfn_getEnergyPriority() {
        return cfn$energyPriority;
    }

    @Override
    @Unique
    public void cfn_setEnergyPriority(int priority) {
        if (cfn$energyPriority == priority) {
            return;
        }
        cfn$energyPriority = priority;
        setChanged();
    }

    @Override
    @Unique
    public void cfn_loadEnergyPriority(int priority) {
        cfn$energyPriority = priority;
    }

    @Override
    @Unique
    public int cfn_getEnergyThrottleTimer() {
        return cfn$energyThrottleTimer;
    }

    @Override
    @Unique
    public void cfn_setEnergyThrottleTimer(int timer) {
        if (timer < 0 || timer > CFNBlockEntityEx.MAX_STORED_ENERGY_THROTTLE_TIMER) {
            throw new IllegalArgumentException("Energy throttle timer must be between 0 and "
                + CFNBlockEntityEx.MAX_STORED_ENERGY_THROTTLE_TIMER + ": " + timer);
        }
        cfn$energyThrottleTimer = timer;
    }

    @Override
    @Unique
    public int cfn_getEnergyLastThrottleTimer() {
        return cfn$energyLastThrottleTimer;
    }

    @Override
    @Unique
    public void cfn_setEnergyLastThrottleTimer(int timer) {
        if (timer != 0 && timer != 1 && timer != 2 && timer != 4 && timer != 8 && timer != 16
            && timer != CFNBlockEntityEx.SATURATED_ENERGY_THROTTLE_STAGE) {
            throw new IllegalArgumentException("Invalid previous energy throttle stage: " + timer);
        }
        cfn$energyLastThrottleTimer = timer;
    }

}
