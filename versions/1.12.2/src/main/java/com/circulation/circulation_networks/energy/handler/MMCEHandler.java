package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import hellfirepvp.modularmachinery.common.tiles.TileEnergyInputHatch;
import hellfirepvp.modularmachinery.common.tiles.TileEnergyOutputHatch;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.jetbrains.annotations.Nullable;

public class MMCEHandler implements IEnergyHandler {

    @Nullable
    private TileEnergyHatch hatch;
    private long remainingExtractBudget;
    private long remainingReceiveBudget;
    private EnergyType energyType = EnergyType.INVALID;
    private boolean initialized;

    public MMCEHandler() {
    }

    private static long getTransferLimit(TileEnergyHatch hatch) {
        var tier = hatch.getTier();
        if (tier == null) {
            return Long.MAX_VALUE;
        }
        long transferLimit = tier.transferLimit;
        return transferLimit > 0L ? transferLimit : Long.MAX_VALUE;
    }

    private static long getCurrentEnergy(TileEnergyHatch hatch) {
        return Math.max(0L, hatch.getCurrentEnergy());
    }

    private static long getRemainingCapacity(TileEnergyHatch hatch) {
        return Math.max(0L, hatch.getMaxEnergy() - hatch.getCurrentEnergy());
    }

    @Override
    public IEnergyHandler init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return this;
        }
        initialized = true;
        if (tileEntity instanceof TileEnergyInputHatch inputHatch) {
            hatch = inputHatch;
            remainingReceiveBudget = getTransferLimit(inputHatch);
            remainingExtractBudget = 0L;
            energyType = EnergyType.RECEIVE;
        } else if (tileEntity instanceof TileEnergyOutputHatch outputHatch) {
            hatch = outputHatch;
            remainingExtractBudget = getTransferLimit(outputHatch);
            remainingReceiveBudget = 0L;
            energyType = EnergyType.SEND;
        } else {
            hatch = null;
            remainingExtractBudget = 0L;
            remainingReceiveBudget = 0L;
            energyType = EnergyType.INVALID;
        }
        return this;
    }

    @Override
    public IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        hatch = null;
        remainingExtractBudget = 0L;
        remainingReceiveBudget = 0L;
        energyType = EnergyType.INVALID;
        return this;
    }

    @Override
    public void clear() {
        hatch = null;
        remainingExtractBudget = 0L;
        remainingReceiveBudget = 0L;
        energyType = EnergyType.INVALID;
        initialized = false;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (hatch == null || remainingExtractBudget <= 0L) {
            return EnergyAmounts.ZERO;
        }
        long currentEnergy = getCurrentEnergy(hatch);
        if (currentEnergy <= 0L) {
            return EnergyAmounts.ZERO;
        }
        long transferable = Math.min(maxExtract.asLongClamped(), Math.min(currentEnergy, remainingExtractBudget));
        if (transferable <= 0L) {
            return EnergyAmounts.ZERO;
        }
        hatch.setCurrentEnergy(currentEnergy - transferable);
        remainingExtractBudget -= transferable;
        return EnergyAmount.obtain(transferable);
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (hatch == null || remainingReceiveBudget <= 0L) {
            return EnergyAmounts.ZERO;
        }
        long remainingCapacity = getRemainingCapacity(hatch);
        if (remainingCapacity <= 0L) {
            return EnergyAmounts.ZERO;
        }
        long transferable = Math.min(maxReceive.asLongClamped(), Math.min(remainingCapacity, remainingReceiveBudget));
        if (transferable <= 0L) {
            return EnergyAmounts.ZERO;
        }
        hatch.setCurrentEnergy(getCurrentEnergy(hatch) + transferable);
        remainingReceiveBudget -= transferable;
        return EnergyAmount.obtain(transferable);
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (hatch == null || remainingExtractBudget <= 0L) {
            return EnergyAmounts.ZERO;
        }
        return EnergyAmount.obtain(Math.min(getCurrentEnergy(hatch), remainingExtractBudget));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (hatch == null || remainingReceiveBudget <= 0L) {
            return EnergyAmounts.ZERO;
        }
        return EnergyAmount.obtain(Math.min(getRemainingCapacity(hatch), remainingReceiveBudget));
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return energyType;
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return hatch != null && remainingExtractBudget > 0L && getCurrentEnergy(hatch) > 0L;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return hatch != null && remainingReceiveBudget > 0L && getRemainingCapacity(hatch) > 0L;
    }
}
