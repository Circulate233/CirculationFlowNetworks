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
    private static final int ROLE_UNKNOWN = 0;
    private static final int ROLE_SUPPORTED = 1;
    private static final int ROLE_UNSUPPORTED = 2;

    @Nullable
    private IEnergyStorage send;
    @Nullable
    private IEnergyStorage receive;
    @Nullable
    private Direction sendDirection;
    @Nullable
    private Direction receiveDirection;
    private int sendState = ROLE_UNKNOWN;
    private int receiveState = ROLE_UNKNOWN;
    private boolean initialized;
    private EnergyType energyType;

    private static boolean hasEnergy(IEnergyStorage storage) {
        return storage.getEnergyStored() > 0;
    }

    private static boolean hasRoom(IEnergyStorage storage) {
        return storage.getEnergyStored() < storage.getMaxEnergyStored();
    }

    private static boolean canExtractEffectively(IEnergyStorage storage) {
        return storage.extractEnergy(1, true) > 0;
    }

    private static boolean canReceiveEffectively(IEnergyStorage storage) {
        return storage.receiveEnergy(1, true) > 0;
    }

    private void bindHint(BlockEntity blockEntity) {
        if (sendState == ROLE_SUPPORTED && sendDirection != null) {
            var optional = blockEntity.getCapability(ForgeCapabilities.ENERGY, sendDirection);
            if (optional.isPresent()) {
                IEnergyStorage storage = optional.orElseThrow(IllegalStateException::new);
                if (hasEnergy(storage)) {
                    send = storage;
                }
            } else {
                sendState = ROLE_UNKNOWN;
            }
        }
        if (receiveState == ROLE_SUPPORTED && receiveDirection != null) {
            var optional = blockEntity.getCapability(ForgeCapabilities.ENERGY, receiveDirection);
            if (optional.isPresent()) {
                IEnergyStorage storage = optional.orElseThrow(IllegalStateException::new);
                if (hasRoom(storage)) {
                    receive = storage;
                }
            } else {
                receiveState = ROLE_UNKNOWN;
            }
        }
    }

    private int bindDirection(BlockEntity blockEntity, Direction direction, boolean needSendScan, boolean needReceiveScan) {
        if (!needSendScan && !needReceiveScan) {
            return 0;
        }
        var optional = blockEntity.getCapability(ForgeCapabilities.ENERGY, direction);
        if (!optional.isPresent()) {
            return 0;
        }
        IEnergyStorage storage = optional.orElseThrow(IllegalStateException::new);
        int attempted = 0;
        boolean hasEnergy = hasEnergy(storage);
        boolean hasRoom = hasRoom(storage);
        if (needSendScan && hasEnergy) {
            attempted |= 1;
        }
        if (needReceiveScan && hasRoom) {
            attempted |= 2;
        }
        if (needSendScan && send == null && hasEnergy(storage) && canExtractEffectively(storage)) {
            send = storage;
            sendDirection = direction;
            sendState = ROLE_SUPPORTED;
        }
        if (needReceiveScan && receive == null && hasRoom(storage) && canReceiveEffectively(storage)) {
            receive = storage;
            receiveDirection = direction;
            receiveState = ROLE_SUPPORTED;
        }
        return attempted;
    }

    @Override
    public void init(BlockEntity blockEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return;
        }
        initialized = true;
        bindHint(blockEntity);
        boolean needSendScan = send == null && sendState == ROLE_UNKNOWN;
        boolean needReceiveScan = receive == null && receiveState == ROLE_UNKNOWN;
        boolean attemptedSend = false;
        boolean attemptedReceive = false;
        for (Direction direction : DIRECTIONS) {
            if (!needSendScan && !needReceiveScan) break;
            int attempted = bindDirection(blockEntity, direction, needSendScan, needReceiveScan);
            attemptedSend |= (attempted & 1) != 0;
            attemptedReceive |= (attempted & 2) != 0;
            needSendScan = send == null && sendState == ROLE_UNKNOWN;
            needReceiveScan = receive == null && receiveState == ROLE_UNKNOWN;
        }
        if (send == null && sendState == ROLE_UNKNOWN && attemptedSend) {
            sendState = ROLE_UNSUPPORTED;
        }
        if (receive == null && receiveState == ROLE_UNKNOWN && attemptedReceive) {
            receiveState = ROLE_UNSUPPORTED;
        }
    }

    @Override
    public void init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        var optional = itemStack.getCapability(ForgeCapabilities.ENERGY);
        optional.ifPresent(ies -> {
            if (hasRoom(ies) && canReceiveEffectively(ies)) {
                this.receive = ies;
            }
        });
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
            sendState = ROLE_UNKNOWN;
        }
        return EnergyAmount.obtain(extracted);
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (receive == null) return EnergyAmounts.ZERO;
        int received = receive.receiveEnergy((int) maxReceive.asLongClamped(), false);
        if (received == 0 && maxReceive.isPositive()) {
            receiveState = ROLE_UNKNOWN;
        }
        return EnergyAmount.obtain(received);
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return send == null ? EnergyAmounts.ZERO : EnergyAmount.obtain(send.extractEnergy(Integer.MAX_VALUE, true));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return receive == null ? EnergyAmounts.ZERO : EnergyAmount.obtain(Math.max(0, receive.receiveEnergy(Integer.MAX_VALUE, true)));
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
