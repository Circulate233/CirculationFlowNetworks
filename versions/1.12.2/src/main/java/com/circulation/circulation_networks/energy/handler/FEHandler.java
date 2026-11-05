package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class FEHandler implements IEnergyHandler {

    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.RUNTIME_DYNAMIC,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );

    @Nullable
    private IEnergyStorage send;
    @Nullable
    private IEnergyStorage receive;
    @Nullable
    private IEnergyStorage directStorage;
    @Nullable
    private EnumFacing sendFacing;
    @Nullable
    private EnumFacing receiveFacing;
    @Nullable
    private TileEntity blockEntity;
    private long activeEpoch = Long.MIN_VALUE;
    private boolean itemBound;
    private boolean supportsSend;
    private boolean supportsReceive;
    private boolean refreshPending;
    private boolean refreshFailed;
    private EnergyType energyType = EnergyType.INVALID;

    public static boolean supports(TileEntity tileEntity) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            IEnergyStorage storage = tileEntity.getCapability(CapabilityEnergy.ENERGY, facing);
            if (storage != null && (storage.canExtract() || storage.canReceive())) {
                return true;
            }
        }
        IEnergyStorage unsided = tileEntity.getCapability(CapabilityEnergy.ENERGY, null);
        return unsided != null && (unsided.canExtract() || unsided.canReceive())
            || tileEntity instanceof IEnergyStorage storage && (storage.canExtract() || storage.canReceive());
    }

    private static EnergyType structuralType(boolean send, boolean receive) {
        if (send) {
            return receive ? EnergyType.STORAGE : EnergyType.SEND;
        }
        return receive ? EnergyType.RECEIVE : EnergyType.INVALID;
    }

    private void discoverStructure(TileEntity tileEntity) {
        send = null;
        receive = null;
        directStorage = unsidedStorage(tileEntity);
        sendFacing = null;
        receiveFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            IEnergyStorage storage = tileEntity.getCapability(CapabilityEnergy.ENERGY, facing);
            if (storage == null) {
                continue;
            }
            if (send == null && storage.canExtract()) {
                send = storage;
                sendFacing = facing;
            }
            if (receive == null && storage.canReceive()) {
                receive = storage;
                receiveFacing = facing;
            }
        }
        if (directStorage != null) {
            if (send == null && directStorage.canExtract()) {
                send = directStorage;
            }
            if (receive == null && directStorage.canReceive()) {
                receive = directStorage;
            }
        }
        supportsSend = send != null;
        supportsReceive = receive != null;
        energyType = structuralType(supportsSend, supportsReceive);
    }

    private boolean refreshCachedCapabilities() {
        IEnergyStorage refreshedSend = supportsSend ? storage(sendFacing) : null;
        IEnergyStorage refreshedReceive = null;
        if (supportsReceive) {
            refreshedReceive = supportsSend && sendFacing == receiveFacing
                ? refreshedSend
                : storage(receiveFacing);
        }
        boolean sendValid = !supportsSend || refreshedSend != null && refreshedSend.canExtract();
        boolean receiveValid = !supportsReceive || refreshedReceive != null && refreshedReceive.canReceive();
        send = sendValid && supportsSend ? refreshedSend : null;
        receive = receiveValid && supportsReceive ? refreshedReceive : null;
        return sendValid && receiveValid;
    }

    private boolean rediscoverRequiredCapabilities() {
        TileEntity boundBlockEntity = Objects.requireNonNull(blockEntity, "blockEntity");
        send = null;
        receive = null;
        directStorage = unsidedStorage(boundBlockEntity);
        sendFacing = null;
        receiveFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            IEnergyStorage storage = boundBlockEntity.getCapability(CapabilityEnergy.ENERGY, facing);
            if (storage == null) {
                continue;
            }
            if (supportsSend && send == null && storage.canExtract()) {
                send = storage;
                sendFacing = facing;
            }
            if (supportsReceive && receive == null && storage.canReceive()) {
                receive = storage;
                receiveFacing = facing;
            }
        }
        if (directStorage != null) {
            if (supportsSend && send == null && directStorage.canExtract()) {
                send = directStorage;
            }
            if (supportsReceive && receive == null && directStorage.canReceive()) {
                receive = directStorage;
            }
        }
        return (!supportsSend || send != null) && (!supportsReceive || receive != null);
    }

    @Nullable
    private IEnergyStorage storage(@Nullable EnumFacing facing) {
        return facing == null
            ? unsidedStorage(Objects.requireNonNull(blockEntity, "blockEntity"))
            : Objects.requireNonNull(blockEntity, "blockEntity").getCapability(CapabilityEnergy.ENERGY, facing);
    }

    private static IEnergyStorage unsidedStorage(TileEntity tile) {
        IEnergyStorage capability = tile.getCapability(CapabilityEnergy.ENERGY, null);
        return capability != null ? capability : tile instanceof IEnergyStorage storage ? storage : null;
    }

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    @Override
    public void bindBlockEntity(TileEntity tileEntity, HandlerInvalidationSink invalidationSink) {
        if (blockEntity != null || itemBound) {
            throw new IllegalStateException("FE handler is already bound");
        }
        blockEntity = Objects.requireNonNull(tileEntity, "tileEntity");
        Objects.requireNonNull(invalidationSink, "invalidationSink");
        discoverStructure(tileEntity);
    }

    /**
     * Arms a lazy capability refresh instead of resolving capabilities for every bound machine on every tick.
     * 1.12.2 has no reliable capability invalidation event, so the refresh cannot be event-driven; it is instead
     * deferred to the first physical read of this tick, which never happens for a machine that no route selects.
     * A refresh failure observed lazily is reported through the ordinary zero-budget path and is escalated to
     * {@link HandlerTickResult#SUSPEND_UNTIL_REBIND} on the following tick.
     */
    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        if (blockEntity == null) {
            throw new IllegalStateException("FE handler has no block-entity binding");
        }
        if (epoch <= activeEpoch) {
            throw new IllegalArgumentException("FE handler epoch must increase: previous " + activeEpoch + ", got " + epoch);
        }
        activeEpoch = epoch;
        if (energyType == EnergyType.INVALID || refreshFailed) {
            send = null;
            receive = null;
            energyType = EnergyType.INVALID;
            refreshPending = false;
            refreshFailed = false;
            return HandlerTickResult.RETRY_BINDING;
        }
        if (epoch % 20L == 0L) {
            EnergyType previous = energyType;
            discoverStructure(blockEntity);
            refreshPending = true;
            if (energyType != previous) return HandlerTickResult.STATE_CHANGED;
        }
        refreshPending = true;
        return HandlerTickResult.UNCHANGED;
    }

    /**
     * Resolves the armed capability refresh at most once per tick.
     *
     * @return {@code true} when this handler currently owns every capability its structural role requires
     */
    private boolean ensureRefreshed() {
        if (!refreshPending) {
            return !refreshFailed;
        }
        refreshPending = false;
        if (refreshCachedCapabilities() || rediscoverRequiredCapabilities()) {
            return true;
        }
        send = null;
        receive = null;
        refreshFailed = true;
        return false;
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("FE handler uses begin-only tick lifecycle");
    }

    @Override
    public void unbindBlockEntity() {
        send = null;
        receive = null;
        directStorage = null;
        sendFacing = null;
        receiveFacing = null;
        blockEntity = null;
        activeEpoch = Long.MIN_VALUE;
        supportsSend = false;
        supportsReceive = false;
        refreshPending = false;
        refreshFailed = false;
        energyType = EnergyType.INVALID;
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (blockEntity != null || itemBound) {
            throw new IllegalStateException("FE handler is already bound");
        }
        IEnergyStorage storage = Objects.requireNonNull(itemStack, "itemStack")
                                        .getCapability(CapabilityEnergy.ENERGY, null);
        itemBound = true;
        if (storage != null && storage.canReceive()) {
            receive = storage;
            energyType = EnergyType.RECEIVE;
        } else {
            energyType = EnergyType.INVALID;
        }
    }

    @Override
    public void unbindItem() {
        send = null;
        receive = null;
        itemBound = false;
        refreshPending = false;
        refreshFailed = false;
        energyType = EnergyType.INVALID;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        return !ensureRefreshed() || send == null ? EnergyAmounts.ZERO
            : EnergyAmount.obtain(send.extractEnergy((int) Math.min(Integer.MAX_VALUE, maxExtract.asLongClamped()), false));
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        return !ensureRefreshed() || receive == null ? EnergyAmounts.ZERO
            : EnergyAmount.obtain(Math.max(0, receive.receiveEnergy((int) Math.min(Integer.MAX_VALUE, maxReceive.asLongClamped()), false)));
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return !ensureRefreshed() ? EnergyAmounts.ZERO : EnergyAmount.obtain(selectBudget(false));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return !ensureRefreshed() ? EnergyAmounts.ZERO : EnergyAmount.obtain(selectBudget(true));
    }

    private int selectBudget(boolean input) {
        IEnergyStorage selected = input ? receive : send;
        int available = budget(selected, input);
        if (available > 0 || blockEntity == null) return available;
        for (EnumFacing face : EnumFacing.VALUES) {
            IEnergyStorage candidate = blockEntity.getCapability(CapabilityEnergy.ENERGY, face);
            if (candidate == selected) continue;
            available = budget(candidate, input);
            if (available > 0) {
                if (input) { receive = candidate; receiveFacing = face; }
                else { send = candidate; sendFacing = face; }
                return available;
            }
        }
        IEnergyStorage candidate = unsidedStorage(blockEntity);
        available = candidate == selected ? 0 : budget(candidate, input);
        if (available > 0) {
            directStorage = candidate;
            if (input) { receive = candidate; receiveFacing = null; }
            else { send = candidate; sendFacing = null; }
        }
        return available;
    }

    private static int budget(@Nullable IEnergyStorage storage, boolean input) {
        if (storage == null) return 0;
        return Math.max(0, input ? storage.canReceive() ? storage.receiveEnergy(Integer.MAX_VALUE, true) : 0
            : storage.canExtract() ? storage.extractEnergy(Integer.MAX_VALUE, true) : 0);
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        EnergyAmount amount = canExtractValue(hubMetadata);
        try {
            return amount.isPositive();
        } finally {
            amount.recycle();
        }
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        EnergyAmount amount = canReceiveValue(hubMetadata);
        try {
            return amount.isPositive();
        } finally {
            amount.recycle();
        }
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return energyType;
    }
}
