package com.circulation.circulation_networks.energy.handler;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.blockentity.grid.AENetworkPowerBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.networking.EnergyAcceptorBlockEntity;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.energy.manager.AE2HandlerManager;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.EnergyAmountConversionUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class AE2Handler implements IEnergyHandler {

    private static final double AE_TO_FE = 2.0D;

    @Nullable
    private BlockEntity blockEntity;
    @Nullable
    private IAEPowerStorage receive;

    @Override
    public IEnergyHandler init(BlockEntity blockEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (!(blockEntity instanceof ControllerBlockEntity) && !(blockEntity instanceof EnergyAcceptorBlockEntity)) {
            return this;
        }
        if (blockEntity instanceof IAEPowerStorage storage) {
            this.blockEntity = blockEntity;
            receive = storage;
        }
        return this;
    }

    @Override
    public IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        return this;
    }

    @Override
    public void clear() {
        blockEntity = null;
        receive = null;
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (receive == null) return EnergyAmounts.ZERO;
        double requestAe = EnergyAmountConversionUtils.toDoubleClamped(maxReceive) / AE_TO_FE;
        double remainderAe = receive.injectAEPower(requestAe, Actionable.MODULATE);
        double acceptedAe = Math.max(0.0D, requestAe - remainderAe);
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(acceptedAe * AE_TO_FE);
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
        if (receive == null) return EnergyAmounts.ZERO;
        double demandAe = Math.max(0.0D, receive.getAEMaxPower() - receive.getAECurrentPower());
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(demandAe * AE_TO_FE);
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return false;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return receive != null && receive.getAEMaxPower() > receive.getAECurrentPower();
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        if (receive == null || blockEntity == null) {
            return EnergyType.INVALID;
        }
        com.circulation.circulation_networks.api.IGrid currentGrid = EnergyMachineManager.INSTANCE.getCurrentHandlerGrid();
        @Nullable IGrid aeGrid = null;
        if (blockEntity instanceof AENetworkPowerBlockEntity aeBlockEntity) {
            aeGrid = aeBlockEntity.getMainNode().getGrid();
        }
        if (currentGrid == null || aeGrid == null) {
            return EnergyType.INVALID;
        }
        return AE2HandlerManager.INSTANCE.claim(currentGrid, aeGrid) ? EnergyType.RECEIVE : EnergyType.INVALID;
    }
}
