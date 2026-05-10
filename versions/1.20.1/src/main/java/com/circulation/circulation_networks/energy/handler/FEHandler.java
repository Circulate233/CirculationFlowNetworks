package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;

public class FEHandler implements IEnergyHandler {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int PROBE_AMOUNT = 1;

    @Nullable
    private IEnergyStorage send;
    @Nullable
    private IEnergyStorage receive;
    @Nullable
    private Direction sendDirection;
    @Nullable
    private Direction receiveDirection;
    private boolean sendProven;
    private boolean receiveProven;
    private boolean sendDirty = true;
    private boolean receiveDirty = true;
    private boolean initialized;
    private EnergyType energyType;

    public FEHandler() {
    }

    private static boolean hasEnergy(IEnergyStorage storage) {
        return storage.getEnergyStored() > 0;
    }

    private static boolean hasRoom(IEnergyStorage storage) {
        return storage.getEnergyStored() < storage.getMaxEnergyStored();
    }

    private static boolean canExtractEffectively(IEnergyStorage storage) {
        return storage.extractEnergy(PROBE_AMOUNT, true) > 0;
    }

    private static boolean canReceiveEffectively(IEnergyStorage storage) {
        return storage.receiveEnergy(PROBE_AMOUNT, true) > 0;
    }

    private void bindHint(BlockEntity blockEntity) {
        if (sendProven && !sendDirty && sendDirection != null) {
            var optional = blockEntity.getCapability(ForgeCapabilities.ENERGY, sendDirection);
            if (optional.isPresent()) {
                IEnergyStorage storage = optional.orElse(null);
                if (storage != null && hasEnergy(storage)) {
                    send = storage;
                }
            } else {
                sendDirty = true;
                sendProven = false;
            }
        }
        if (receiveProven && !receiveDirty && receiveDirection != null) {
            var optional = blockEntity.getCapability(ForgeCapabilities.ENERGY, receiveDirection);
            if (optional.isPresent()) {
                IEnergyStorage storage = optional.orElse(null);
                if (storage != null && hasRoom(storage)) {
                    receive = storage;
                }
            } else {
                receiveDirty = true;
                receiveProven = false;
            }
        }
    }

    private void bindDirection(BlockEntity blockEntity, Direction direction, boolean needSendScan, boolean needReceiveScan) {
        if (!needSendScan && !needReceiveScan) {
            return;
        }
        var optional = blockEntity.getCapability(ForgeCapabilities.ENERGY, direction);
        if (!optional.isPresent()) {
            return;
        }
        IEnergyStorage storage = optional.orElse(null);
        if (storage == null) {
            return;
        }
        if (needSendScan && send == null && hasEnergy(storage) && canExtractEffectively(storage)) {
            send = storage;
            sendDirection = direction;
            sendProven = true;
            sendDirty = false;
        }
        if (needReceiveScan && receive == null && hasRoom(storage) && canReceiveEffectively(storage)) {
            receive = storage;
            receiveDirection = direction;
            receiveProven = true;
            receiveDirty = false;
        }
    }

    @Override
    public IEnergyHandler init(BlockEntity blockEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return this;
        }
        initialized = true;
        bindHint(blockEntity);
        boolean needSendScan = send == null && (sendDirty || !sendProven);
        boolean needReceiveScan = receive == null && (receiveDirty || !receiveProven);
        for (Direction direction : DIRECTIONS) {
            if (!needSendScan && !needReceiveScan) break;
            bindDirection(blockEntity, direction, needSendScan, needReceiveScan);
            needSendScan = send == null && (sendDirty || !sendProven);
            needReceiveScan = receive == null && (receiveDirty || !receiveProven);
        }
        return this;
    }

    @Override
    public IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        var optional = itemStack.getCapability(ForgeCapabilities.ENERGY);
        optional.ifPresent(ies -> {
            if (hasRoom(ies) && canReceiveEffectively(ies)) {
                this.receive = ies;
            }
        });
        return this;
    }

    @Override
    public void clear() {
        send = null;
        receive = null;
        energyType = null;
        initialized = false;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        int extracted = send.extractEnergy((int) maxExtract.asLongClamped(), false);
        if (extracted == 0 && maxExtract.isPositive()) {
            sendDirty = true;
            sendProven = false;
        }
        return EnergyAmount.obtain(extracted);
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (receive == null) return EnergyAmounts.ZERO;
        int received = receive.receiveEnergy((int) maxReceive.asLongClamped(), false);
        if (received == 0 && maxReceive.isPositive()) {
            receiveDirty = true;
            receiveProven = false;
        }
        return EnergyAmount.obtain(received);
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return send == null ? EnergyAmounts.ZERO : EnergyAmount.obtain(send.getEnergyStored());
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return receive == null ? EnergyAmounts.ZERO : EnergyAmount.obtain(Math.max(0, receive.getMaxEnergyStored() - receive.getEnergyStored()));
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        if (energyType == null) {
            boolean receive = this.receive != null;
            if (send != null) {
                return energyType = receive ? EnergyType.STORAGE : EnergyType.SEND;
            } else if (receive) {
                return energyType = EnergyType.RECEIVE;
            }
            return energyType = EnergyType.INVALID;
        }
        return energyType;
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return send != null;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return receive != null;
    }
}
