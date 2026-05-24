package com.circulation.circulation_networks.energy.handler;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
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

    public final EnergyAmount receivedValue = EnergyAmount.obtain(0);
    public final EnergyAmount acceptableValue = EnergyAmount.obtain(0);
    @Nullable
    private IEnergyService energyGrid;
    @Nullable
    private AENetworkedPoweredBlockEntity probedTile;
    private boolean probed;
    private boolean fullyInitialized;

    @Override
    public void init(BlockEntity blockEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (!(blockEntity instanceof ControllerBlockEntity) && !(blockEntity instanceof EnergyAcceptorBlockEntity)) {
            clear();
            probed = true;
            return;
        }
        clear();
        AENetworkedPoweredBlockEntity tile = (AENetworkedPoweredBlockEntity) blockEntity;
        var n = tile.getMainNode();
        if (n == null) {
            probed = true;
            return;
        }
        IGrid grid = n.getGrid();
        if (grid == null) {
            probed = true;
            return;
        }
        energyGrid = grid.getEnergyService();
        probedTile = tile;
        probed = true;
    }

    @Override
    public void init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        clear();
        probed = true;
        fullyInitialized = true;
    }

    @Override
    public IEnergyHandler resolveMappedHandler(HandlerResolveContext context) {
        if (energyGrid == null) {
            return this;
        }
        var claimed = AE2HandlerManager.INSTANCE.claim(energyGrid, this);
        if (claimed != this) {
            clear();
            probed = true;
            return claimed;
        }
        completeInit();
        return this;
    }

    private void completeInit() {
        if (!probed || fullyInitialized || probedTile == null) {
            return;
        }
        var e = probedTile.getExternalPowerDemand(PowerUnit.FE, Double.MAX_VALUE);
        EnergyAmountConversionUtils.setFromDoubleFloor(acceptableValue, e);
        fullyInitialized = true;
    }

    @Override
    public void clear() {
        if (fullyInitialized && energyGrid != null) energyGrid.injectPower(receivedValue.doubleValue() / 2, Actionable.MODULATE);
        energyGrid = null;
        probedTile = null;
        acceptableValue.setZero();
        receivedValue.setZero();
        probed = false;
        fullyInitialized = false;
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
        return energyGrid != null;
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        if (acceptableValue.compareTo(0) > 0) return EnergyType.RECEIVE;
        return EnergyType.INVALID;
    }

}
