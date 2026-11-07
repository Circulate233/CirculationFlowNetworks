package com.circulation.circulation_networks.energy.handler;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.capability.CapabilityOP;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.EnergyFaceSelector;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Draconic Evolution OP binding with periodically refreshed structural roles. */
public final class DEHandler implements IEnergyHandler {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.RUNTIME_DYNAMIC,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );

    private final EnergyFaceSelector<IOPStorage> faces = new EnergyFaceSelector<>(
        IOPStorage::canExtract, IOPStorage::canReceive,
        storage -> storage.extractOP(Long.MAX_VALUE, true),
        storage -> storage.receiveOP(Long.MAX_VALUE, true));
    @Nullable
    private IOPStorage send;
    @Nullable
    private IOPStorage receive;
    @Nullable
    private BlockEntity blockEntity;
    private boolean itemBound;
    private EnergyType energyType = EnergyType.INVALID;
    // Budget backoff can skip the exact periodic tick; refresh on the next eligible callback.
    private long lastRoleRefreshEpoch = Long.MIN_VALUE;

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    @Override
    public void bindBlockEntity(BlockEntity blockEntity, HandlerInvalidationSink invalidationSink) {
        if (this.blockEntity != null || itemBound) {
            throw new IllegalStateException("Draconic Evolution handler is already bound");
        }
        this.blockEntity = Objects.requireNonNull(blockEntity, "blockEntity");
        Objects.requireNonNull(invalidationSink, "invalidationSink");
        for (Direction direction : DIRECTIONS) {
            bindCapability(blockEntity, direction, invalidationSink);
        }
        bindCapability(blockEntity, null, invalidationSink);
        energyType = structuralType();
        if (energyType == EnergyType.INVALID) {
            throw new IllegalArgumentException("Draconic Evolution block entity has no usable OP capability");
        }
    }

    private boolean bindCapability(BlockEntity blockEntity,
                                   @Nullable Direction direction,
                                   HandlerInvalidationSink invalidationSink) {
        var capability = blockEntity.getCapability(CapabilityOP.OP, direction);
        if (!capability.isPresent()) {
            return false;
        }
        capability.addListener(ignored -> invalidationSink.invalidate());
        IOPStorage storage = capability.orElseThrow(IllegalStateException::new);
        faces.add(storage);
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
        requireBlockBinding();
        if (lastRoleRefreshEpoch != Long.MIN_VALUE && epoch - lastRoleRefreshEpoch < 20L) return HandlerTickResult.UNCHANGED;
        lastRoleRefreshEpoch = epoch;
        EnergyType previous = energyType;
        energyType = faces.role();
        return previous == energyType ? HandlerTickResult.UNCHANGED : HandlerTickResult.STATE_CHANGED;
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("Draconic Evolution handler does not use end tick callbacks");
    }

    @Override
    public void unbindBlockEntity() {
        lastRoleRefreshEpoch = Long.MIN_VALUE;
        faces.clear();
        send = null;
        receive = null;
        energyType = EnergyType.INVALID;
        blockEntity = null;
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (blockEntity != null || itemBound) {
            throw new IllegalStateException("Draconic Evolution handler is already bound");
        }
        itemBound = true;
        var capability = itemStack.getCapability(CapabilityOP.OP);
        if (capability.isPresent()) {
            IOPStorage storage = capability.orElseThrow(IllegalStateException::new);
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
        return send == null
            ? EnergyAmounts.ZERO
            : EnergyAmount.obtain(Math.max(0L, send.extractOP(maxExtract.asLongClamped(), false)));
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        return receive == null
            ? EnergyAmounts.ZERO
            : EnergyAmount.obtain(Math.max(0L, receive.receiveOP(maxReceive.asLongClamped(), false)));
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (blockEntity != null) {
            long budget = faces.budget(false);
            send = faces.sender();
            return EnergyAmount.obtain(budget);
        }
        return send == null ? EnergyAmounts.ZERO
            : EnergyAmount.obtain(Math.max(0L, send.extractOP(Long.MAX_VALUE, true)));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (blockEntity != null) {
            long budget = faces.budget(true);
            receive = faces.receiver();
            return EnergyAmount.obtain(budget);
        }
        return receive == null ? EnergyAmounts.ZERO
            : EnergyAmount.obtain(Math.max(0L, receive.receiveOP(Long.MAX_VALUE, true)));
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return send != null && send.canExtract() && send.getOPStored() > 0L;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return receive != null && receive.canReceive() && receive.getOPStored() < receive.getMaxOPStored();
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
            throw new IllegalStateException("Draconic Evolution handler has no block-entity binding");
        }
    }
}
