package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.manager.MekanismLongEnergyConversion;
import com.circulation.circulation_networks.network.nodes.HubNode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.circulation.circulation_networks.api.MekanismEnergyPermissions;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import mekanism.common.tile.multiblock.TileEntityInductionPort;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Mekanism energy binding with structure-derived external energy roles. */
public final class MEKHandler implements IEnergyHandler {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.RUNTIME_DYNAMIC,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );

    private final EnergyAmount itemReceiveBudget = EnergyAmount.obtain(0L);
    @Nullable
    private IStrictEnergyHandler send;
    @Nullable
    private IStrictEnergyHandler receive;
    @Nullable
    private BlockEntity blockEntity;
    private Object cacheGeneration;
    private final ObjectArrayList<Face> faces = new ObjectArrayList<>();
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
        requireUnbound();
        Objects.requireNonNull(invalidationSink, "invalidationSink");
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            throw new IllegalArgumentException("Mekanism handler requires a server-level block entity");
        }
        this.blockEntity = blockEntity;
        Object generation = new Object();
        cacheGeneration = generation;
        for (Direction direction : DIRECTIONS) {
            BlockCapabilityCache<IStrictEnergyHandler, Direction> cache = BlockCapabilityCache.create(
                Capabilities.STRICT_ENERGY.block(),
                level,
                blockEntity.getBlockPos(),
                direction,
                () -> cacheGeneration == generation && this.blockEntity == blockEntity,
                invalidationSink::invalidate
            );
            IStrictEnergyHandler handler = cache.getCapability();
            if (handler != null && handler.getEnergyContainerCount() > 0) {
                faces.add(new Face(direction, handler));
            }
        }
        refreshStructure();
        if (energyType == EnergyType.INVALID) {
            throw new IllegalArgumentException("Mekanism block entity has no usable energy capability");
        }
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        requireBlockBinding();
        if (!(blockEntity instanceof TileEntityInductionPort)
            && lastRoleRefreshEpoch != Long.MIN_VALUE && epoch - lastRoleRefreshEpoch < 20L) return HandlerTickResult.UNCHANGED;
        lastRoleRefreshEpoch = epoch;
        EnergyType previous = energyType;
        refreshStructure();
        return previous == energyType ? HandlerTickResult.UNCHANGED : HandlerTickResult.STATE_CHANGED;
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("Mekanism handler does not use end tick callbacks");
    }

    @Override
    public void unbindBlockEntity() {
        lastRoleRefreshEpoch = Long.MIN_VALUE;
        faces.clear();
        send = null;
        receive = null;
        cacheGeneration = null;
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
        long maximumJoules = MekanismLongEnergyConversion.toJoules(requested.asLongClamped());
        long requestedJoules = MekanismLongEnergyConversion.wholeJoules(
            maximumJoules - receive.insertEnergy(maximumJoules, Action.SIMULATE));
        if (requestedJoules == 0L) return requested.setZero();
        long remainder = receive.insertEnergy(requestedJoules, Action.EXECUTE);
        long insertedJoules = Math.max(0L, requestedJoules - remainder);
        EnergyAmount inserted = EnergyAmount.obtain(MekanismLongEnergyConversion.toFe(insertedJoules));
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
        long maximumJoules = MekanismLongEnergyConversion.toJoules(requested.asLongClamped());
        long requestedJoules = MekanismLongEnergyConversion.wholeJoules(
            send.extractEnergy(maximumJoules, Action.SIMULATE));
        if (requestedJoules == 0L) return requested.setZero();
        long extractedJoules = Math.max(0L, send.extractEnergy(requestedJoules, Action.EXECUTE));
        EnergyAmount extracted = EnergyAmount.obtain(MekanismLongEnergyConversion.toFe(extractedJoules));
        extracted.min(requested);
        requested.recycle();
        return extracted;
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (!itemBound) selectFace(false);
        if (send == null || itemBound) {
            return EnergyAmounts.ZERO;
        }
        long extracted = Math.max(0L, send.extractEnergy(Long.MAX_VALUE, Action.SIMULATE));
        return EnergyAmount.obtain(MekanismLongEnergyConversion.toFe(extracted));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (!itemBound) selectFace(true);
        if (receive == null) {
            return EnergyAmounts.ZERO;
        }
        if (itemBound) {
            return EnergyAmount.obtain(itemReceiveBudget);
        }
        long remainder = receive.insertEnergy(Long.MAX_VALUE, Action.SIMULATE);
        return EnergyAmount.obtain(MekanismLongEnergyConversion.toFe(Long.MAX_VALUE - remainder));
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
        itemReceiveBudget.init(MekanismLongEnergyConversion.toFe(Long.MAX_VALUE - remainder));
    }

    private static void clampToMaximum(EnergyAmount amount) {
        amount.init(Math.clamp(amount.asLongClamped(), 0L, MekanismLongEnergyConversion.MAX_FE));
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
    private void refreshStructure() {
        send = null;
        receive = null;
        for (Face face : faces) {
            face.input = true;
            face.output = true;
            if (blockEntity instanceof TileEntityInductionPort port) {
                face.output = port.getActive();
                face.input = !face.output;
            } else {
                if (face.direction != null && blockEntity instanceof ISideConfiguration configurable) {
                    ISlotInfo slot = configurable.getConfig().getSlotInfo(TransmissionType.ENERGY, face.direction);
                    face.input = slot != null && slot.isEnabled() && slot.canInput();
                    face.output = slot != null && slot.isEnabled() && slot.canOutput();
                }
                if (blockEntity instanceof IMekanismStrictEnergyHandler nativeHandler) {
                    boolean known = false, input = false, output = false;
                    for (var container : nativeHandler.getEnergyContainers(face.direction)) {
                        if (container instanceof MekanismEnergyPermissions permissions) {
                            known = true;
                            input |= permissions.cfn_canInsertEnergy();
                            output |= permissions.cfn_canExtractEnergy();
                        } else {
                            // Unknown third-party containers retain their capability's bidirectional contract.
                            input = true;
                            output = true;
                        }
                    }
                    if (known) { face.input &= input; face.output &= output; }
                }
            }
            if (receive == null && face.input) receive = face.handler;
            if (send == null && face.output) send = face.handler;
        }
        energyType = send != null ? receive != null ? EnergyType.STORAGE : EnergyType.SEND
            : receive != null ? EnergyType.RECEIVE : EnergyType.INVALID;
    }

    private void selectFace(boolean input) {
        for (Face face : faces) {
            if (!(input ? face.input : face.output)) continue;
            boolean available = input
                ? face.handler.insertEnergy(Long.MAX_VALUE, Action.SIMULATE) <= Long.MAX_VALUE - 5L
                : face.handler.extractEnergy(Long.MAX_VALUE, Action.SIMULATE) >= 5L;
            if (available) {
                if (input) receive = face.handler;
                else send = face.handler;
                return;
            }
        }
    }

    private static final class Face {
        private final Direction direction;
        private final IStrictEnergyHandler handler;
        private boolean input;
        private boolean output;
        private Face(Direction direction, IStrictEnergyHandler handler) {
            this.direction = direction;
            this.handler = handler;
        }
    }

}
