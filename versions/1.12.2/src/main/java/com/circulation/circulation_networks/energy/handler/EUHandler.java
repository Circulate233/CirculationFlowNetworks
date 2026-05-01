package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.EnergyAmountConversionUtils;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import ic2.api.energy.tile.IEnergyTile;
import ic2.api.item.ElectricItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("DataFlowIssue")
public class EUHandler implements IEnergyHandler {
    private static final double MAX_EU_TRANSFER = Long.MAX_VALUE / 4.0D;

    private EnergyType energyType;
    @Nullable
    private IEnergySource send;

    private boolean isItem;
    private ItemStack itemStack = ItemStack.EMPTY;

    @Nullable
    private IEnergySink receive;
    private EnumFacing receiveFacing = EnumFacing.NORTH;

    static EnergyAmount positiveFeAmountFromEu(double valueEu) {
        if (!(valueEu > 0.0D)) {
            return EnergyAmount.obtain(0L);
        }
        if (!Double.isFinite(valueEu)) {
            return EnergyAmount.obtain(Long.MAX_VALUE);
        }
        if (valueEu >= MAX_EU_TRANSFER) {
            return EnergyAmount.obtain(Long.MAX_VALUE);
        }
        return EnergyAmountConversionUtils.obtainFromDoubleFloor(valueEu).multiply(4L);
    }

    static void setAcceptedFeFromEuResult(EnergyAmount targetFe, double resultEu, EnergyAmount requestedEu) {
        if (!(resultEu > 0.0D)) {
            targetFe.setZero();
            return;
        }
        if (!Double.isFinite(resultEu)) {
            targetFe.copyFrom(requestedEu).multiply(4L);
            return;
        }
        EnergyAmountConversionUtils.setFromDoubleFloor(targetFe, resultEu);
        targetFe.multiply(4L);
        EnergyAmount requestedFe = EnergyAmount.obtain(requestedEu).multiply(4L);
        try {
            targetFe.min(requestedFe);
        } finally {
            requestedFe.recycle();
        }
    }

    static void setAcceptedFeFromEuRemainder(EnergyAmount targetFe, double remainderEu, EnergyAmount requestedEu) {
        if (!(remainderEu > 0.0D)) {
            targetFe.copyFrom(requestedEu).multiply(4L);
            return;
        }
        if (!Double.isFinite(remainderEu)) {
            targetFe.setZero();
            return;
        }
        EnergyAmount remainderFe = EnergyAmountConversionUtils.obtainFromDoubleFloor(remainderEu).multiply(4L);
        EnergyAmount requestedFe = EnergyAmount.obtain(requestedEu).multiply(4L);
        try {
            targetFe.copyFrom(requestedFe).subtract(remainderFe);
            if (targetFe.isNegative()) {
                targetFe.setZero();
            }
        } finally {
            remainderFe.recycle();
            requestedFe.recycle();
        }
    }

    @Override
    public IEnergyHandler init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        isItem = false;
        IEnergyTile tile = EnergyNet.instance.getSubTile(tileEntity.getWorld(), tileEntity.getPos());
        boolean o = tile instanceof IEnergySource;
        boolean i = tile instanceof IEnergySink;
        if (o) {
            if (i) {
                energyType = EnergyType.STORAGE;
                receive = (IEnergySink) tile;
            } else energyType = EnergyType.SEND;
            send = (IEnergySource) tile;
        } else {
            var receive = (IEnergySink) tile;
            for (var value : EnumFacing.values()) {
                if (receive.acceptsEnergyFrom(null, value)) {
                    this.receiveFacing = value;
                    this.receive = receive;
                    energyType = EnergyType.RECEIVE;
                    break;
                }
            }
        }
        if (!(send != null && send.getOfferedEnergy() > 0) && !(receive != null && receive.getDemandedEnergy() > 0))
            energyType = EnergyType.INVALID;
        return this;
    }

    @Override
    public IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        isItem = true;
        this.itemStack = itemStack;
        energyType = EnergyType.RECEIVE;
        return this;
    }

    @Override
    public void clear() {
        this.energyType = EnergyType.INVALID;
        this.send = null;
        this.receive = null;
        this.itemStack = ItemStack.EMPTY;
        this.isItem = false;
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            EnergyAmount receivable = EnergyAmount.obtain(maxReceive);
            if (receivable.isZero()) {
                return receivable;
            }
            EnergyAmount euAmount = EnergyAmount.obtain(receivable).divide(4L);
            try {
                double charged = ElectricItem.manager.charge(itemStack, EnergyAmountConversionUtils.toDoubleClamped(euAmount), Integer.MAX_VALUE, false, false);
                setAcceptedFeFromEuResult(receivable, charged, euAmount);
                return receivable;
            } finally {
                euAmount.recycle();
            }
        } else {
            if (receive == null) return EnergyAmounts.ZERO;
            EnergyAmount receivable = EnergyAmount.obtain(maxReceive);
            if (receivable.isZero()) {
                return receivable;
            }
            EnergyAmount euAmount = EnergyAmount.obtain(receivable).divide(4L);
            try {
                double leftover = receive.injectEnergy(receiveFacing, EnergyAmountConversionUtils.toDoubleClamped(euAmount), 0);
                setAcceptedFeFromEuRemainder(receivable, leftover, euAmount);
                return receivable;
            } finally {
                euAmount.recycle();
            }
        }
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        EnergyAmount extractable = EnergyAmount.obtain(maxExtract);
        if (extractable.isZero()) {
            return extractable;
        }
        EnergyAmount euAmount = EnergyAmount.obtain(extractable).divide(4L);
        try {
            send.drawEnergy(EnergyAmountConversionUtils.toDoubleClamped(euAmount));
            return extractable;
        } finally {
            euAmount.recycle();
        }
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        return positiveFeAmountFromEu(send.getOfferedEnergy());
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            return positiveFeAmountFromEu(
                ElectricItem.manager.charge(itemStack, Double.MAX_VALUE, Integer.MAX_VALUE, false, true)
            );
        } else {
            if (receive == null) return EnergyAmounts.ZERO;
            return positiveFeAmountFromEu(receive.getDemandedEnergy());
        }
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return send != null && send.getOfferedEnergy() > 0;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            return ElectricItem.manager.getMaxCharge(itemStack) > 0;
        } else {
            return receive != null && receive.getDemandedEnergy() > 0;
        }
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return energyType;
    }
}
