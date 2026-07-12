package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import crazypants.enderio.base.power.IPowerStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class EIOBackendHandler implements IEnergyHandler {

    private static final HandlerBindingPolicy POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.STATIC,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );
    private final IPowerStorage storage;

    public EIOBackendHandler(IPowerStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    private static UnsupportedOperationException owned() {
        return new UnsupportedOperationException("Ender IO shared backend is manager-owned");
    }

    private static int clamp(long value, long firstLimit, long secondLimit) {
        if (value <= 0L || firstLimit <= 0L || secondLimit <= 0L) return 0;
        long result = Math.min(value, Math.min(firstLimit, secondLimit));
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return POLICY;
    }

    @Override
    public void bindBlockEntity(TileEntity value, HandlerInvalidationSink sink) {
        throw owned();
    }

    @Override
    public void unbindBlockEntity() {
        throw owned();
    }

    @Override
    public void bindItem(ItemStack value, @Nullable HubNode.HubMetadata metadata) {
        throw owned();
    }

    @Override
    public void unbindItem() {
        throw owned();
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        throw new IllegalStateException("Ender IO shared backend uses a static tick lifecycle");
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("Ender IO shared backend uses a static tick lifecycle");
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maximum, @Nullable HubNode.HubMetadata metadata) {
        long before = storage.getEnergyStoredL();
        int requested = clamp(maximum.asLongClamped(), storage.getMaxEnergyStoredL() - before, storage.getMaxInput());
        if (requested <= 0) return EnergyAmounts.ZERO;
        storage.addEnergy(requested);
        long after = storage.getEnergyStoredL();
        return EnergyAmount.obtain(Math.max(0L, after - before));
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maximum, @Nullable HubNode.HubMetadata metadata) {
        long before = storage.getEnergyStoredL();
        int requested = clamp(maximum.asLongClamped(), before, storage.getMaxOutput());
        if (requested <= 0) return EnergyAmounts.ZERO;
        storage.addEnergy(-requested);
        if (storage.isCreative()) return EnergyAmount.obtain(requested);
        long after = storage.getEnergyStoredL();
        return EnergyAmount.obtain(Math.max(0L, before - after));
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata metadata) {
        return EnergyAmount.obtain(clamp(Integer.MAX_VALUE, storage.getEnergyStoredL(), storage.getMaxOutput()));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata metadata) {
        return EnergyAmount.obtain(clamp(Integer.MAX_VALUE, storage.getMaxEnergyStoredL() - storage.getEnergyStoredL(), storage.getMaxInput()));
    }

    @Override
    public boolean canExtract(IEnergyHandler value, @Nullable HubNode.HubMetadata metadata) {
        EnergyAmount amount = canExtractValue(metadata);
        try {
            return amount.isPositive();
        } finally {
            amount.recycle();
        }
    }

    @Override
    public boolean canReceive(IEnergyHandler value, @Nullable HubNode.HubMetadata metadata) {
        EnergyAmount amount = canReceiveValue(metadata);
        try {
            return amount.isPositive();
        } finally {
            amount.recycle();
        }
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata metadata) {
        return EnergyType.STORAGE;
    }
}
