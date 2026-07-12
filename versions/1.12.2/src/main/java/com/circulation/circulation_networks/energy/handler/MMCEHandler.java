package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import hellfirepvp.modularmachinery.common.tiles.TileEnergyInputHatch;
import hellfirepvp.modularmachinery.common.tiles.TileEnergyOutputHatch;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class MMCEHandler implements IEnergyHandler {

    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );

    @Nullable
    private TileEnergyHatch hatch;
    private long remainingExtractBudget;
    private long remainingReceiveBudget;
    private EnergyType energyType = EnergyType.INVALID;
    private long activeEpoch = Long.MIN_VALUE;

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

    private void clearTickState() {
        remainingExtractBudget = 0L;
        remainingReceiveBudget = 0L;
    }

    private void bindHatch(TileEntity tileEntity) {
        hatch = null;
        energyType = EnergyType.INVALID;
        if (tileEntity instanceof TileEnergyInputHatch inputHatch) {
            hatch = inputHatch;
            energyType = EnergyType.RECEIVE;
            return;
        }
        if (tileEntity instanceof TileEnergyOutputHatch outputHatch) {
            hatch = outputHatch;
            energyType = EnergyType.SEND;
        }
    }

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    @Override
    public void bindBlockEntity(TileEntity tileEntity, HandlerInvalidationSink invalidationSink) {
        if (hatch != null) {
            throw new IllegalStateException("MMCE handler is already bound");
        }
        Objects.requireNonNull(invalidationSink, "invalidationSink");
        bindHatch(Objects.requireNonNull(tileEntity, "tileEntity"));
        if (hatch == null) {
            throw new IllegalArgumentException("MMCE handler requires an energy input or output hatch");
        }
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        if (hatch == null) {
            throw new IllegalStateException("MMCE handler has no block-entity binding");
        }
        if (epoch <= activeEpoch) {
            throw new IllegalArgumentException("MMCE handler epoch must increase: previous " + activeEpoch + ", got " + epoch);
        }
        activeEpoch = epoch;
        if (hatch instanceof TileEnergyInputHatch inputHatch) {
            remainingReceiveBudget = getTransferLimit(inputHatch);
        } else if (hatch instanceof TileEnergyOutputHatch outputHatch) {
            remainingExtractBudget = getTransferLimit(outputHatch);
        } else {
            throw new IllegalStateException("MMCE handler bound to an unsupported hatch type");
        }
        return HandlerTickResult.UNCHANGED;
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("MMCE handler uses begin-only tick lifecycle");
    }

    @Override
    public void unbindBlockEntity() {
        clearTickState();
        activeEpoch = Long.MIN_VALUE;
        hatch = null;
        energyType = EnergyType.INVALID;
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        throw new IllegalStateException("MMCE does not support item energy bindings");
    }

    @Override
    public void unbindItem() {
        throw new UnsupportedOperationException("MMCE does not support item energy bindings");
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
