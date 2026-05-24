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
    private boolean prepared;

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

    private void resetState() {
        hatch = null;
        remainingExtractBudget = 0L;
        remainingReceiveBudget = 0L;
        energyType = EnergyType.INVALID;
    }

    private void scanIntoState(TileEntity tileEntity) {
        resetState();
        if (tileEntity instanceof TileEnergyInputHatch inputHatch) {
            hatch = inputHatch;
            remainingReceiveBudget = getTransferLimit(inputHatch);
            energyType = EnergyType.RECEIVE;
            prepared = true;
            return;
        }
        if (tileEntity instanceof TileEnergyOutputHatch outputHatch) {
            hatch = outputHatch;
            remainingExtractBudget = getTransferLimit(outputHatch);
            energyType = EnergyType.SEND;
        }
        prepared = true;
    }

    @Override
    public void asyncInit(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        scanIntoState(tileEntity);
    }

    @Override
    public boolean shouldRunAsyncInit(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        return true;
    }

    @Override
    public void init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!prepared) {
            scanIntoState(tileEntity);
        }
    }

    @Override
    public void init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        resetState();
        prepared = true;
    }

    @Override
    public void clear() {
        resetState();
        initialized = false;
        prepared = false;
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
