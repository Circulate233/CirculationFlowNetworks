package com.circulation.circulation_networks.energy.handler;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.networking.EnergyAcceptorBlockEntity;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.energy.manager.AE2HandlerManager;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.EnergyAmountConversionUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class AE2Handler implements IEnergyHandler {

    @Nullable
    private IGrid grid;
    public final EnergyAmount receivedValue = EnergyAmount.obtain(0);
    public final EnergyAmount acceptableValue = EnergyAmount.obtain(0);
    private boolean init;

    @Override
    public IEnergyHandler init(BlockEntity blockEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (!(blockEntity instanceof ControllerBlockEntity) && !(blockEntity instanceof EnergyAcceptorBlockEntity)) {
            return this;
        }
        AENetworkedPoweredBlockEntity tile = (AENetworkedPoweredBlockEntity) blockEntity;
        init = true;
        var n = tile.getMainNode();
        if (n == null) {
            return this;
        }
        grid = n.getGrid();
        if (grid == null) {
            return this;
        }
        var a = AE2HandlerManager.INSTANCE.claim(grid, this);
        if (a == this) {
            var e = tile.getExternalPowerDemand(PowerUnit.FE, Double.MAX_VALUE);
            EnergyAmountConversionUtils.setFromDoubleFloor(acceptableValue, e);
            return this;
        } else {
            this.recycle();
            return a;
        }
    }

    @Override
    public IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        init = true;
        return this;
    }

    @Override
    public void clear() {
        if (grid != null) {
            grid.getEnergyService().injectPower(receivedValue.doubleValue() / 2, Actionable.MODULATE);
        }
        grid = null;
        init = false;
        acceptableValue.setZero();
        receivedValue.setZero();
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (acceptableValue.compareTo(maxReceive) >= 0) {
            receivedValue.add(maxReceive);
            acceptableValue.subtract(maxReceive);
            return EnergyAmount.obtain(maxReceive);
        }
        receivedValue.add(acceptableValue);
        try {
            return EnergyAmount.obtain(acceptableValue);
        } finally {
            acceptableValue.setZero();
        }
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return EnergyAmount.obtain(acceptableValue);
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return false;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return grid != null;
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        if (acceptableValue.compareTo(0) > 0) return EnergyType.RECEIVE;
        return EnergyType.INVALID;
    }

    @Override
    public void recycle() {
        if (init) IEnergyHandler.super.recycle();
    }
}
