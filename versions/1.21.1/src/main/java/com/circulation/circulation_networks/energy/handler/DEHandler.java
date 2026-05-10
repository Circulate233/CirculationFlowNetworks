package com.circulation.circulation_networks.energy.handler;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.capability.CapabilityOP;
import com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyPylon;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class DEHandler implements IEnergyHandler {

    private static final Direction[] DIRECTIONS = Direction.values();

    @Nullable
    private IOPStorage send;
    @Nullable
    private IOPStorage receive;
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

    private static boolean hasEnergy(IOPStorage storage) {
        return storage.getOPStored() > 0L;
    }

    private static boolean hasRoom(IOPStorage storage) {
        return storage.getOPStored() < storage.getMaxOPStored();
    }

    private static boolean canExtractNow(IOPStorage storage) {
        return hasEnergy(storage) && storage.canExtract();
    }

    private static boolean canReceiveNow(IOPStorage storage) {
        return hasRoom(storage) && storage.canReceive();
    }

    private void bindRole(IOPStorage storage, @Nullable Direction direction, boolean needSendScan, boolean needReceiveScan) {
        if (needSendScan && send == null && canExtractNow(storage)) {
            send = storage;
            sendDirection = direction;
            sendProven = true;
            sendDirty = false;
        }
        if (needReceiveScan && receive == null && canReceiveNow(storage)) {
            receive = storage;
            receiveDirection = direction;
            receiveProven = true;
            receiveDirty = false;
        }
    }

    private void bindHint(BlockEntity blockEntity) {
        if (sendProven && !sendDirty && sendDirection != null) {
            IOPStorage storage = CapabilityOP.fromBlockEntity(blockEntity, sendDirection);
            if (storage != null && hasEnergy(storage)) {
                send = storage;
            } else if (storage == null) {
                sendDirty = true;
                sendProven = false;
            }
        }
        if (receiveProven && !receiveDirty && receiveDirection != null) {
            IOPStorage storage = CapabilityOP.fromBlockEntity(blockEntity, receiveDirection);
            if (storage != null && hasRoom(storage)) {
                receive = storage;
            } else if (storage == null) {
                receiveDirty = true;
                receiveProven = false;
            }
        }
    }

    private void bindDirection(BlockEntity blockEntity, Direction direction, boolean needSendScan, boolean needReceiveScan) {
        if (!needSendScan && !needReceiveScan) {
            return;
        }
        IOPStorage storage = CapabilityOP.fromBlockEntity(blockEntity, direction);
        if (storage != null) {
            bindRole(storage, direction, needSendScan, needReceiveScan);
        }
    }

    @Override
    public IEnergyHandler init(BlockEntity blockEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return this;
        }
        initialized = true;
        if (blockEntity.getLevel() == null) {
            return this;
        }
        if (blockEntity instanceof TileEnergyPylon) {
            for (Direction direction : DIRECTIONS) {
                IOPStorage storage = CapabilityOP.fromBlockEntity(blockEntity, direction);
                if (storage != null) {
                    send = hasEnergy(storage) ? storage : null;
                    receive = hasRoom(storage) ? storage : null;
                    sendDirection = direction;
                    receiveDirection = direction;
                    sendProven = send != null;
                    receiveProven = receive != null;
                    sendDirty = send == null;
                    receiveDirty = receive == null;
                    return this;
                }
            }
            return this;
        }
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
        IOPStorage storage = itemStack.getCapability(CapabilityOP.ITEM);
        if (storage != null && canReceiveNow(storage)) {
            receive = storage;
        }
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
        if (send == null) {
            return EnergyAmounts.ZERO;
        }
        long extracted = send.extractOP(maxExtract.asLongClamped(), false);
        if (extracted == 0L && maxExtract.isPositive()) {
            sendDirty = true;
            sendProven = false;
        }
        return EnergyAmount.obtain(extracted);
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (receive == null) {
            return EnergyAmounts.ZERO;
        }
        long received = receive.receiveOP(maxReceive.asLongClamped(), false);
        if (received == 0L && maxReceive.isPositive()) {
            receiveDirty = true;
            receiveProven = false;
        }
        return EnergyAmount.obtain(received);
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return send == null ? EnergyAmounts.ZERO : EnergyAmount.obtain(send.getOPStored());
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return receive == null ? EnergyAmounts.ZERO : EnergyAmount.obtain(Math.max(0L, receive.getMaxOPStored() - receive.getOPStored()));
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        if (energyType == null) {
            boolean canReceive = receive != null;
            if (send != null) {
                return energyType = canReceive ? EnergyType.STORAGE : EnergyType.SEND;
            } else if (canReceive) {
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
