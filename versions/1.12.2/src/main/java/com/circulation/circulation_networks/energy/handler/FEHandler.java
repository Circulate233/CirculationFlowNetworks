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
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );

    @Nullable
    private IEnergyStorage send;
    @Nullable
    private IEnergyStorage receive;
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
    private EnergyType energyType = EnergyType.INVALID;

    public static boolean supports(TileEntity tileEntity) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            IEnergyStorage storage = tileEntity.getCapability(CapabilityEnergy.ENERGY, facing);
            if (storage != null && (storage.canExtract() || storage.canReceive())) {
                return true;
            }
        }
        return tileEntity instanceof IEnergyStorage storage && (storage.canExtract() || storage.canReceive());
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
        if (tileEntity instanceof IEnergyStorage storage) {
            if (send == null && storage.canExtract()) {
                send = storage;
            }
            if (receive == null && storage.canReceive()) {
                receive = storage;
            }
        }
        supportsSend = send != null;
        supportsReceive = receive != null;
        energyType = structuralType(supportsSend, supportsReceive);
    }

    private boolean refreshCachedCapabilities() {
        IEnergyStorage refreshedSend = capability(sendFacing);
        IEnergyStorage refreshedReceive = sendFacing == receiveFacing
            ? refreshedSend
            : capability(receiveFacing);
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
        if (boundBlockEntity instanceof IEnergyStorage storage) {
            if (supportsSend && send == null && storage.canExtract()) {
                send = storage;
            }
            if (supportsReceive && receive == null && storage.canReceive()) {
                receive = storage;
            }
        }
        return (!supportsSend || send != null) && (!supportsReceive || receive != null);
    }

    @Nullable
    private IEnergyStorage capability(@Nullable EnumFacing facing) {
        if (blockEntity == null) {
            return null;
        }
        if (facing == null && blockEntity instanceof IEnergyStorage storage) {
            return storage;
        }
        return facing == null ? null : blockEntity.getCapability(CapabilityEnergy.ENERGY, facing);
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

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        if (blockEntity == null) {
            throw new IllegalStateException("FE handler has no block-entity binding");
        }
        if (epoch <= activeEpoch) {
            throw new IllegalArgumentException("FE handler epoch must increase: previous " + activeEpoch + ", got " + epoch);
        }
        activeEpoch = epoch;
        if (energyType == EnergyType.INVALID
            || !refreshCachedCapabilities() && !rediscoverRequiredCapabilities()) {
            send = null;
            receive = null;
            energyType = EnergyType.INVALID;
            return HandlerTickResult.SUSPEND_UNTIL_REBIND;
        }
        return HandlerTickResult.UNCHANGED;
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("FE handler uses begin-only tick lifecycle");
    }

    @Override
    public void unbindBlockEntity() {
        send = null;
        receive = null;
        sendFacing = null;
        receiveFacing = null;
        blockEntity = null;
        activeEpoch = Long.MIN_VALUE;
        supportsSend = false;
        supportsReceive = false;
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
        energyType = EnergyType.INVALID;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        return send == null ? EnergyAmounts.ZERO
            : EnergyAmount.obtain(send.extractEnergy((int) maxExtract.asLongClamped(), false));
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        return receive == null ? EnergyAmounts.ZERO
            : EnergyAmount.obtain(Math.max(0, receive.receiveEnergy((int) maxReceive.asLongClamped(), false)));
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return send == null ? EnergyAmounts.ZERO : EnergyAmount.obtain(send.extractEnergy(Integer.MAX_VALUE, true));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return receive == null ? EnergyAmounts.ZERO
            : EnergyAmount.obtain(Math.max(0, receive.receiveEnergy(Integer.MAX_VALUE, true)));
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
