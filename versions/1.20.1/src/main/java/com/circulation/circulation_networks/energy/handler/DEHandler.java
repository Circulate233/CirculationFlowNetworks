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
    private static final int ROLE_UNKNOWN = 0;
    private static final int ROLE_SUPPORTED = 1;
    private static final int ROLE_UNSUPPORTED = 2;

    @Nullable
    private IOPStorage send;
    @Nullable
    private IOPStorage receive;
    @Nullable
    private Direction sendDirection;
    @Nullable
    private Direction receiveDirection;
    private int sendState = ROLE_UNKNOWN;
    private int receiveState = ROLE_UNKNOWN;
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

    private int bindRole(IOPStorage storage, @Nullable Direction direction, boolean needSendScan, boolean needReceiveScan) {
        int attempted = 0;
        boolean hasEnergy = hasEnergy(storage);
        boolean hasRoom = hasRoom(storage);
        if (needSendScan && hasEnergy) {
            attempted |= 1;
        }
        if (needReceiveScan && hasRoom) {
            attempted |= 2;
        }
        if (needSendScan && send == null && canExtractNow(storage)) {
            send = storage;
            sendDirection = direction;
            sendState = ROLE_SUPPORTED;
        }
        if (needReceiveScan && receive == null && canReceiveNow(storage)) {
            receive = storage;
            receiveDirection = direction;
            receiveState = ROLE_SUPPORTED;
        }
        return attempted;
    }

    private void bindHint(BlockEntity blockEntity) {
        if (sendState == ROLE_SUPPORTED && sendDirection != null) {
            var optional = blockEntity.getCapability(CapabilityOP.OP, sendDirection);
            if (optional.isPresent()) {
                IOPStorage storage = optional.orElseThrow(IllegalStateException::new);
                if (hasEnergy(storage)) {
                    send = storage;
                }
            } else {
                sendState = ROLE_UNKNOWN;
            }
        }
        if (receiveState == ROLE_SUPPORTED && receiveDirection != null) {
            var optional = blockEntity.getCapability(CapabilityOP.OP, receiveDirection);
            if (optional.isPresent()) {
                IOPStorage storage = optional.orElseThrow(IllegalStateException::new);
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
        var optional = blockEntity.getCapability(CapabilityOP.OP, direction);
        if (!optional.isPresent()) {
            return 0;
        }
        IOPStorage storage = optional.orElseThrow(IllegalStateException::new);
        return bindRole(storage, direction, needSendScan, needReceiveScan);
    }

    @Override
    public void init(BlockEntity blockEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return;
        }
        initialized = true;
        if (blockEntity instanceof TileEnergyPylon) {
            for (Direction direction : DIRECTIONS) {
                var optional = blockEntity.getCapability(CapabilityOP.OP, direction);
                if (optional.isPresent()) {
                    IOPStorage storage = optional.orElseThrow(IllegalStateException::new);
                    send = hasEnergy(storage) ? storage : null;
                    receive = hasRoom(storage) ? storage : null;
                    sendDirection = direction;
                    receiveDirection = direction;
                    sendState = send != null ? ROLE_SUPPORTED : ROLE_UNKNOWN;
                    receiveState = receive != null ? ROLE_SUPPORTED : ROLE_UNKNOWN;
                    energyType = EnergyType.STORAGE;
                    return;
                }
            }
            energyType = EnergyType.INVALID;
            return;
        }
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
        itemStack.getCapability(CapabilityOP.OP).ifPresent(storage -> {
            if (canReceiveNow(storage)) {
                receive = storage;
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
        if (send == null) {
            return EnergyAmounts.ZERO;
        }
        long extracted = send.extractOP(maxExtract.asLongClamped(), false);
        if (extracted == 0L && maxExtract.isPositive()) {
            sendState = ROLE_UNKNOWN;
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
            receiveState = ROLE_UNKNOWN;
        }
        return EnergyAmount.obtain(received);
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return send == null ? EnergyAmounts.ZERO : EnergyAmount.obtain(send.extractOP(Long.MAX_VALUE, true));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        return receive == null ? EnergyAmounts.ZERO : EnergyAmount.obtain(Math.max(0L, receive.receiveOP(Long.MAX_VALUE, true)));
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
