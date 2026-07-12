package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Forge Energy binding with a role frozen from capability structure. */
public final class FEHandler implements IEnergyHandler {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.STATIC,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );

    private final ObjectArrayList<BlockCapabilityCache<IEnergyStorage, Direction>> capabilityCaches =
        new ObjectArrayList<>(DIRECTIONS.length);
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
        requireUnbound();
        Objects.requireNonNull(invalidationSink, "invalidationSink");
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            throw new IllegalArgumentException("FE handler requires a server-level block entity");
        }
        this.blockEntity = blockEntity;
        for (Direction direction : DIRECTIONS) {
            BlockCapabilityCache<IEnergyStorage, Direction> cache = BlockCapabilityCache.create(
                Capabilities.EnergyStorage.BLOCK,
                level,
                blockEntity.getBlockPos(),
                direction,
                () -> this.blockEntity == blockEntity,
                invalidationSink::suspendUntilRebind
            );
            capabilityCaches.add(cache);
            IEnergyStorage storage = cache.getCapability();
            if (storage == null) {
                continue;
            }
            if (send == null && storage.canExtract()) {
                send = storage;
            }
            if (receive == null && storage.canReceive()) {
                receive = storage;
            }
        }
        energyType = roleOf(send != null, receive != null);
        if (energyType == EnergyType.INVALID) {
            throw new IllegalArgumentException("FE block entity has no usable energy capability");
        }
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
        capabilityCaches.clear();
        blockEntity = null;
        energyType = EnergyType.INVALID;
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        requireUnbound();
        Objects.requireNonNull(itemStack, "itemStack");
        itemBound = true;
        IEnergyStorage storage = itemStack.getCapability(Capabilities.EnergyStorage.ITEM);
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
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        Objects.requireNonNull(maxReceive, "maxReceive");
        if (receive == null) {
            return EnergyAmounts.ZERO;
        }
        return EnergyAmount.obtain(receive.receiveEnergy((int) maxReceive.asLongClamped(), false));
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        Objects.requireNonNull(maxExtract, "maxExtract");
        if (send == null) {
            return EnergyAmounts.ZERO;
        }
        return EnergyAmount.obtain(send.extractEnergy((int) maxExtract.asLongClamped(), false));
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
        if (send == null || !send.canExtract()) {
            return false;
        }
        EnergyAmount available = canExtractValue(hubMetadata);
        try {
            return available.isPositive();
        } finally {
            available.recycle();
        }
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        if (receive == null || !receive.canReceive()) {
            return false;
        }
        EnergyAmount available = canReceiveValue(hubMetadata);
        try {
            return available.isPositive();
        } finally {
            available.recycle();
        }
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return energyType;
    }

    private static EnergyType roleOf(boolean canSend, boolean canReceive) {
        if (canSend) {
            return canReceive ? EnergyType.STORAGE : EnergyType.SEND;
        }
        return canReceive ? EnergyType.RECEIVE : EnergyType.INVALID;
    }

    private void requireUnbound() {
        if (blockEntity != null || itemBound) {
            throw new IllegalStateException("FE handler is already bound");
        }
    }

    private void requireBlockBinding() {
        if (blockEntity == null) {
            throw new IllegalStateException("FE handler has no block-entity binding");
        }
    }
}
