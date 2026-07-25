package com.circulation.circulation_networks.mixins.mc;

import com.circulation.circulation_networks.api.CFNBlockEntityEx;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;

@Mixin(TileEntity.class)
public abstract class MixinBlockEntity implements CFNBlockEntityEx {

    @Unique
    private EnergyMachineManager.MachineHandlerRuntime cfn$machineHandlerRuntime;

    @Unique
    private int cfn$energyThrottleTimer;

    @Unique
    private int cfn$energyLastThrottleTimer;

    @Shadow
    public abstract World getWorld();

    @Shadow
    public abstract BlockPos getPos();

    @Shadow
    public abstract boolean isInvalid();

    @Override
    @Unique
    public World cfn_getWorld() {
        return getWorld();
    }

    @Override
    @Unique
    public int cfn_getDimensionId() {
        var world = getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot resolve dimension for a block entity without a world");
        }
        return world.provider.getDimension();
    }

    @Override
    @Unique
    public String cfn_getTypeName() {
        return getClass().getName();
    }

    @Override
    @Unique
    public BlockPos cfn_getBlockPos() {
        return getPos();
    }

    @Override
    @Unique
    public boolean cfn_isRemoved() {
        return isInvalid();
    }

    @Override
    @Unique
    public @Nullable IEnergyHandlerManager cfn_getEnergyManager(@Nullable IEnergyHandlerManager excludedManager) {
        var blockEntity = (TileEntity) (Object) this;
        return excludedManager == null
            ? RegistryEnergyHandler.getEnergyManager(blockEntity)
            : RegistryEnergyHandler.getEnergyManagerExcluding(blockEntity, excludedManager);
    }

    @Override
    @Unique
    public boolean cfn_isEnergyBlacklisted() {
        return RegistryEnergyHandler.isBlack((TileEntity) (Object) this);
    }

    @Override
    @Unique
    public boolean cfn_isSupplyBlacklisted() {
        return RegistryEnergyHandler.isSupplyBlack((TileEntity) (Object) this);
    }

    @Override
    @Unique
    public void cfn_bindEnergyHandler(IEnergyHandler handler, HandlerInvalidationSink invalidationSink) {
        handler.bindBlockEntity((TileEntity) (Object) this, invalidationSink);
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
