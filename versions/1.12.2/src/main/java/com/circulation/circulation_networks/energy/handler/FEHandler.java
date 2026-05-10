package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;

public class FEHandler implements IEnergyHandler {

    private static final int ROLE_UNKNOWN = 0;
    private static final int ROLE_SUPPORTED = 1;
    private static final int ROLE_UNSUPPORTED = 2;

    @Nullable
    private IEnergyStorage send;
    @Nullable
    private IEnergyStorage receive;
    @Nullable
    private EnumFacing sendFacing;
    @Nullable
    private EnumFacing receiveFacing;
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

    private void bindHint(TileEntity tileEntity) {
        if (sendState == ROLE_SUPPORTED && sendFacing != null) {
            IEnergyStorage storage = tileEntity.getCapability(CapabilityEnergy.ENERGY, sendFacing);
            if (storage != null && hasEnergy(storage)) {
                send = storage;
            } else if (storage == null) {
                sendState = ROLE_UNKNOWN;
            }
        }
        if (receiveState == ROLE_SUPPORTED && receiveFacing != null) {
            IEnergyStorage storage = tileEntity.getCapability(CapabilityEnergy.ENERGY, receiveFacing);
            if (storage != null && hasRoom(storage)) {
                receive = storage;
            } else if (storage == null) {
                receiveState = ROLE_UNKNOWN;
            }
        }
    }

    private int bindSide(TileEntity tileEntity, EnumFacing facing, boolean needSendScan, boolean needReceiveScan) {
        if (!needSendScan && !needReceiveScan) {
            return 0;
        }
        IEnergyStorage storage = tileEntity.getCapability(CapabilityEnergy.ENERGY, facing);
        if (storage == null) {
            return 0;
        }
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
            sendFacing = facing;
            sendState = ROLE_SUPPORTED;
        }
        if (needReceiveScan && receive == null && hasRoom(storage) && canReceiveEffectively(storage)) {
            receive = storage;
            receiveFacing = facing;
            receiveState = ROLE_SUPPORTED;
        }
        return attempted;
    }

    @Override
    public IEnergyHandler init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return this;
        }
        initialized = true;
        bindHint(tileEntity);
        boolean needSendScan = send == null && sendState == ROLE_UNKNOWN;
        boolean needReceiveScan = receive == null && receiveState == ROLE_UNKNOWN;
        boolean attemptedSend = false;
        boolean attemptedReceive = false;
        for (int i = 0; i < EnumFacing.VALUES.length; i++) {
            if (!needSendScan && !needReceiveScan) break;
            int attempted = bindSide(tileEntity, EnumFacing.VALUES[i], needSendScan, needReceiveScan);
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
        return this;
    }

    @Override
    public IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        var ies = itemStack.getCapability(CapabilityEnergy.ENERGY, null);
        if (ies == null) return this;
        if (hasRoom(ies) && canReceiveEffectively(ies)) {
            this.receive = ies;
        }
        energyType = EnergyType.RECEIVE;
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
