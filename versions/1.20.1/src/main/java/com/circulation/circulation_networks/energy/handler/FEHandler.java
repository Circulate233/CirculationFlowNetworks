package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Forge Energy binding with a role fixed from capability structure. */
public final class FEHandler implements IEnergyHandler {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.STATIC,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );

    @Nullable
    private IEnergyStorage send;
    @Nullable
    private IEnergyStorage receive;
    @Nullable
    private BlockEntity blockEntity;
    private boolean itemBound;
    private EnergyType energyType = EnergyType.INVALID;

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    @Override
    public void bindBlockEntity(BlockEntity blockEntity, HandlerInvalidationSink invalidationSink) {
        if (this.blockEntity != null || itemBound) {
            throw new IllegalStateException("FE handler is already bound");
        }
        this.blockEntity = Objects.requireNonNull(blockEntity, "blockEntity");
        Objects.requireNonNull(invalidationSink, "invalidationSink");
        boolean foundCapability = false;
        for (Direction direction : DIRECTIONS) {
            foundCapability |= bindCapability(blockEntity, direction, invalidationSink);
        }
        if (!foundCapability) {
            bindCapability(blockEntity, null, invalidationSink);
        }
        energyType = structuralType();
        if (energyType == EnergyType.INVALID) {
            throw new IllegalArgumentException("FE block entity has no usable energy capability");
        }
    }

    private boolean bindCapability(BlockEntity blockEntity,
                                   @Nullable Direction direction,
                                   HandlerInvalidationSink invalidationSink) {
        var capability = blockEntity.getCapability(ForgeCapabilities.ENERGY, direction);
        if (!capability.isPresent()) {
            return false;
        }
        capability.addListener(ignored -> invalidationSink.suspendUntilRebind());
        IEnergyStorage storage = capability.orElseThrow(IllegalStateException::new);
        if (send == null && storage.canExtract()) {
            send = storage;
        }
        if (receive == null && storage.canReceive()) {
            receive = storage;
        }
        return true;
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        throw new IllegalStateException("STATIC FE handler does not receive tick callbacks");
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("STATIC FE handler does not receive tick callbacks");
    }

    @Override
    public void unbindBlockEntity() {
        send = null;
        receive = null;
        energyType = EnergyType.INVALID;
        blockEntity = null;
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (blockEntity != null || itemBound) {
            throw new IllegalStateException("FE handler is already bound");
        }
        itemBound = true;
        var capability = itemStack.getCapability(ForgeCapabilities.ENERGY);
        if (capability.isPresent()) {
            IEnergyStorage storage = capability.orElseThrow(IllegalStateException::new);
            if (storage.canReceive()) {
                receive = storage;
                energyType = EnergyType.RECEIVE;
            }
        }
    }

    @Override
    public void unbindItem() {
        send = null;
        receive = null;
        energyType = EnergyType.INVALID;
        itemBound = false;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) {
            return EnergyAmounts.ZERO;
        }
        return EnergyAmount.obtain(send.extractEnergy((int) maxExtract.asLongClamped(), false));
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (receive == null) {
            return EnergyAmounts.ZERO;
        }
        return EnergyAmount.obtain(receive.receiveEnergy((int) maxReceive.asLongClamped(), false));
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return send == null
            ? EnergyAmounts.ZERO
            : EnergyAmount.obtain(Math.max(0, send.extractEnergy(Integer.MAX_VALUE, true)));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return receive == null
            ? EnergyAmounts.ZERO
            : EnergyAmount.obtain(Math.max(0, receive.receiveEnergy(Integer.MAX_VALUE, true)));
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return send != null && send.extractEnergy(1, true) > 0;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return receive != null && receive.receiveEnergy(1, true) > 0;
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return energyType;
    }

    private EnergyType structuralType() {
        if (send != null) {
            return receive != null ? EnergyType.STORAGE : EnergyType.SEND;
        }
        return receive != null ? EnergyType.RECEIVE : EnergyType.INVALID;
    }

    private void requireBlockBinding() {
        if (blockEntity == null) {
            throw new IllegalStateException("FE handler has no block-entity binding");
        }
    }
}
