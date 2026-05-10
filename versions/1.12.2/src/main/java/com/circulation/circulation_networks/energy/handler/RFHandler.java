package com.circulation.circulation_networks.energy.handler;

import cofh.redstoneflux.api.IEnergyConnection;
import cofh.redstoneflux.api.IEnergyContainerItem;
import cofh.redstoneflux.api.IEnergyProvider;
import cofh.redstoneflux.api.IEnergyReceiver;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

public class RFHandler implements IEnergyHandler {

    @Nullable
    private IEnergyProvider send;
    @Nullable
    private IEnergyReceiver receive;
    @Nullable
    private IEnergyContainerItem receiveItem;
    @Nullable
    private EnumFacing sendFacing;
    @Nullable
    private EnumFacing receiveFacing;
    private ItemStack itemStack = ItemStack.EMPTY;
    private boolean isItem;
    private boolean sendProven;
    private boolean receiveProven;
    private boolean sendDirty = true;
    private boolean receiveDirty = true;
    private boolean initialized;
    private EnergyType energyType;

    public RFHandler() {
    }

    private static int asRfAmount(EnergyAmount amount) {
        long value = amount.asLongClamped();
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static boolean hasEnergy(cofh.redstoneflux.api.IEnergyHandler handler, EnumFacing facing) {
        return handler.getEnergyStored(facing) > 0;
    }

    private static boolean hasRoom(cofh.redstoneflux.api.IEnergyHandler handler, EnumFacing facing) {
        return handler.getEnergyStored(facing) < handler.getMaxEnergyStored(facing);
    }

    private void bindHint(TileEntity tileEntity) {
        if (sendProven && !sendDirty && sendFacing != null && tileEntity instanceof IEnergyProvider provider && tileEntity instanceof cofh.redstoneflux.api.IEnergyHandler handler) {
            if (hasEnergy(handler, sendFacing)) {
                send = provider;
            }
        }
        if (receiveProven && !receiveDirty && receiveFacing != null && tileEntity instanceof IEnergyReceiver receiver && tileEntity instanceof cofh.redstoneflux.api.IEnergyHandler handler) {
            if (hasRoom(handler, receiveFacing)) {
                receive = receiver;
            }
        }
    }

    private void bindTileSide(TileEntity tileEntity, EnumFacing facing, boolean needSendScan, boolean needReceiveScan) {
        if (!needSendScan && !needReceiveScan) {
            return;
        }
        if (!(tileEntity instanceof IEnergyConnection connection) || !connection.canConnectEnergy(facing)) {
            return;
        }
        if (needSendScan && send == null && tileEntity instanceof IEnergyProvider provider && hasEnergy(provider, facing) && provider.extractEnergy(facing, 1, true) > 0) {
            send = provider;
            sendFacing = facing;
            sendProven = true;
            sendDirty = false;
        }
        if (needReceiveScan && receive == null && tileEntity instanceof IEnergyReceiver receiver && hasRoom(receiver, facing) && receiver.receiveEnergy(facing, 1, true) > 0) {
            receive = receiver;
            receiveFacing = facing;
            receiveProven = true;
            receiveDirty = false;
        }
    }

    @Override
    public IEnergyHandler init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return this;
        }
        initialized = true;
        isItem = false;
        bindHint(tileEntity);
        boolean needSendScan = send == null && (sendDirty || !sendProven);
        boolean needReceiveScan = receive == null && (receiveDirty || !receiveProven);
        for (int i = 0; i < EnumFacing.VALUES.length; i++) {
            if (!needSendScan && !needReceiveScan) break;
            bindTileSide(tileEntity, EnumFacing.VALUES[i], needSendScan, needReceiveScan);
            needSendScan = send == null && (sendDirty || !sendProven);
            needReceiveScan = receive == null && (receiveDirty || !receiveProven);
        }
        return this;
    }

    @Override
    public IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        isItem = true;
        this.itemStack = itemStack;
        if (itemStack.getItem() instanceof IEnergyContainerItem containerItem) {
            receiveItem = containerItem;
            energyType = EnergyType.RECEIVE;
        }
        return this;
    }

    @Override
    public void clear() {
        send = null;
        receive = null;
        receiveItem = null;
        itemStack = ItemStack.EMPTY;
        isItem = false;
        energyType = null;
        initialized = false;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null || sendFacing == null) {
            return EnergyAmounts.ZERO;
        }
        int extracted = send.extractEnergy(sendFacing, asRfAmount(maxExtract), false);
        if (extracted == 0 && maxExtract.isPositive()) {
            sendDirty = true;
            sendProven = false;
        }
        return EnergyAmount.obtain(extracted);
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            if (receiveItem == null) {
                return EnergyAmounts.ZERO;
            }
            return EnergyAmount.obtain(receiveItem.receiveEnergy(itemStack, asRfAmount(maxReceive), false));
        }
        if (receive == null || receiveFacing == null) {
            return EnergyAmounts.ZERO;
        }
        int received = receive.receiveEnergy(receiveFacing, asRfAmount(maxReceive), false);
        if (received == 0 && maxReceive.isPositive()) {
            receiveDirty = true;
            receiveProven = false;
        }
        return EnergyAmount.obtain(received);
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null || sendFacing == null) {
            return EnergyAmounts.ZERO;
        }
        cofh.redstoneflux.api.IEnergyHandler handler = send;
        return EnergyAmount.obtain(handler.getEnergyStored(sendFacing));
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            if (receiveItem == null) {
                return EnergyAmounts.ZERO;
            }
            return EnergyAmount.obtain(receiveItem.receiveEnergy(itemStack, Integer.MAX_VALUE, true));
        }
        if (receive == null || receiveFacing == null) {
            return EnergyAmounts.ZERO;
        }
        cofh.redstoneflux.api.IEnergyHandler handler = receive;
        return EnergyAmount.obtain(Math.max(0, handler.getMaxEnergyStored(receiveFacing) - handler.getEnergyStored(receiveFacing)));
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        if (energyType == null) {
            boolean hasReceive = receive != null || receiveItem != null;
            if (send != null) {
                return energyType = hasReceive ? EnergyType.STORAGE : EnergyType.SEND;
            }
            if (hasReceive) {
                return energyType = EnergyType.RECEIVE;
            }
            return energyType = EnergyType.INVALID;
        }
        return energyType;
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return send != null && sendFacing != null;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            return receiveItem != null && receiveItem.receiveEnergy(itemStack, 1, true) > 0;
        }
        return receive != null && receiveFacing != null;
    }
}
