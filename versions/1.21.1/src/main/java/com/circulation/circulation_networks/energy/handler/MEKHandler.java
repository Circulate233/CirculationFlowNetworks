package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.EnergyAmountConversionUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** Mekanism energy binding whose block role is frozen from capability presence. */
public final class MEKHandler implements IEnergyHandler {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.STATIC,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );
    private static final double FE_TO_MEK_RATIO = 2.5D;
    private static final BigInteger MAX_DIRECT_DOUBLE_TRANSFER = BigDecimal.valueOf(Double.MAX_VALUE).toBigInteger();

    private final ObjectArrayList<BlockCapabilityCache<IStrictEnergyHandler, Direction>> capabilityCaches =
        new ObjectArrayList<>(DIRECTIONS.length);
    private final EnergyAmount itemReceiveBudget = EnergyAmount.obtain(0L);
    @Nullable
    private IStrictEnergyHandler send;
    @Nullable
    private IStrictEnergyHandler receive;
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
            throw new IllegalArgumentException("Mekanism handler requires a server-level block entity");
        }
        this.blockEntity = blockEntity;
        for (Direction direction : DIRECTIONS) {
            BlockCapabilityCache<IStrictEnergyHandler, Direction> cache = BlockCapabilityCache.create(
                Capabilities.STRICT_ENERGY.block(),
                level,
                blockEntity.getBlockPos(),
                direction,
                () -> this.blockEntity == blockEntity,
                invalidationSink::suspendUntilRebind
            );
            capabilityCaches.add(cache);
            IStrictEnergyHandler handler = cache.getCapability();
            if (handler != null && handler.getEnergyContainerCount() > 0 && send == null) {
                send = handler;
                receive = handler;
            }
        }
        energyType = send == null ? EnergyType.INVALID : EnergyType.STORAGE;
        if (energyType == EnergyType.INVALID) {
            throw new IllegalArgumentException("Mekanism block entity has no usable energy capability");
        }
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        throw new IllegalStateException("STATIC Mekanism handler does not receive tick callbacks");
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("STATIC Mekanism handler does not receive tick callbacks");
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
        receive = itemStack.getCapability(Capabilities.STRICT_ENERGY.item());
        if (receive == null || receive.getEnergyContainerCount() == 0) {
            energyType = EnergyType.INVALID;
            return;
        }
        refreshItemReceiveBudget();
        energyType = EnergyType.RECEIVE;
    }

    @Override
    public void unbindItem() {
        send = null;
        receive = null;
        itemReceiveBudget.setZero();
        itemBound = false;
        energyType = EnergyType.INVALID;
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        Objects.requireNonNull(maxReceive, "maxReceive");
        if (receive == null) {
            return EnergyAmounts.ZERO;
        }
        EnergyAmount requested = EnergyAmount.obtain(maxReceive);
        clampToMaximum(requested);
        if (itemBound) {
            requested.min(itemReceiveBudget);
        }
        if (requested.isZero()) {
            return requested;
        }
        long requestedJoules = (long) (EnergyAmountConversionUtils.toDoubleClamped(requested) * FE_TO_MEK_RATIO);
        long remainder = receive.insertEnergy(requestedJoules, Action.EXECUTE);
        long insertedJoules = Math.max(0L, requestedJoules - remainder);
        EnergyAmount inserted = EnergyAmountConversionUtils.obtainFromDoubleFloor(insertedJoules / FE_TO_MEK_RATIO);
        inserted.min(requested);
        requested.recycle();
        if (itemBound) {
            itemReceiveBudget.subtract(inserted);
        }
        return inserted;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        Objects.requireNonNull(maxExtract, "maxExtract");
        if (send == null || itemBound) {
            return EnergyAmounts.ZERO;
        }
        EnergyAmount requested = EnergyAmount.obtain(maxExtract);
        clampToMaximum(requested);
        if (requested.isZero()) {
            return requested;
        }
        long requestedJoules = (long) (EnergyAmountConversionUtils.toDoubleClamped(requested) * FE_TO_MEK_RATIO);
        long extractedJoules = Math.max(0L, send.extractEnergy(requestedJoules, Action.EXECUTE));
        EnergyAmount extracted = EnergyAmountConversionUtils.obtainFromDoubleFloor(extractedJoules / FE_TO_MEK_RATIO);
        extracted.min(requested);
        requested.recycle();
        return extracted;
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null || itemBound) {
            return EnergyAmounts.ZERO;
        }
        long extracted = Math.max(0L, send.extractEnergy(Long.MAX_VALUE, Action.SIMULATE));
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(extracted / FE_TO_MEK_RATIO);
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (receive == null) {
            return EnergyAmounts.ZERO;
        }
        if (itemBound) {
            return EnergyAmount.obtain(itemReceiveBudget);
        }
        long remainder = receive.insertEnergy(Long.MAX_VALUE, Action.SIMULATE);
        return EnergyAmountConversionUtils.obtainFromDoubleFloor((Long.MAX_VALUE - remainder) / FE_TO_MEK_RATIO);
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        EnergyAmount available = canExtractValue(hubMetadata);
        try {
            return available.isPositive();
        } finally {
            available.recycle();
        }
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
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

    private void refreshItemReceiveBudget() {
        if (receive == null) {
            itemReceiveBudget.setZero();
            return;
        }
        long remainder = receive.insertEnergy(Long.MAX_VALUE, Action.SIMULATE);
        EnergyAmountConversionUtils.setFromDoubleFloor(
            itemReceiveBudget,
            (Long.MAX_VALUE - remainder) / FE_TO_MEK_RATIO
        );
    }

    private static void clampToMaximum(EnergyAmount amount) {
        if (amount.isPositive() && amount.asBigInteger().compareTo(MAX_DIRECT_DOUBLE_TRANSFER) > 0) {
            amount.init(MAX_DIRECT_DOUBLE_TRANSFER);
        }
    }

    private void requireUnbound() {
        if (blockEntity != null || itemBound) {
            throw new IllegalStateException("Mekanism handler is already bound");
        }
    }

    private void requireBlockBinding() {
        if (blockEntity == null) {
            throw new IllegalStateException("Mekanism handler has no block-entity binding");
        }
    }
}
