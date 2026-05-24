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
import ic2.core.WorldData;
import ic2.core.energy.grid.EnergyNetLocal;
import ic2.core.energy.grid.Tile;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

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
    private boolean initialized;
    private boolean prepared;

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

    private void resetBlockState() {
        energyType = EnergyType.INVALID;
        send = null;
        receive = null;
        receiveFacing = EnumFacing.NORTH;
        isItem = false;
    }

    private void prepareBlockState(TileEntity tileEntity) {
        resetBlockState();
        var data = WorldData.get(tileEntity.getWorld(), false);
        if (data == null) {
            prepared = true;
            return;
        }
        BlockPos pos = tileEntity.getPos();
        EnergyNetLocal energyNet = data.energyNet;
        Tile tiles = energyNet.getTile(pos);
        IEnergyTile tile = null;
        if (tiles != null) {
            for (IEnergyTile subTile : tiles.getSubTiles()) {
                if (EnergyNet.instance.getPos(subTile).equals(pos)) {
                    tile = subTile;
                }
            }
        }
        boolean output = tile instanceof IEnergySource;
        boolean input = tile instanceof IEnergySink;
        if (output) {
            send = (IEnergySource) tile;
            if (input) {
                receive = (IEnergySink) tile;
                energyType = EnergyType.STORAGE;
            } else {
                energyType = EnergyType.SEND;
            }
        } else if (input) {
            var sink = (IEnergySink) tile;
            for (var value : EnumFacing.values()) {
                if (sink.acceptsEnergyFrom(null, value)) {
                    receiveFacing = value;
                    receive = sink;
                    energyType = EnergyType.RECEIVE;
                    break;
                }
            }
        }
        if (!(send != null && send.getOfferedEnergy() > 0) && !(receive != null && receive.getDemandedEnergy() > 0)) {
            energyType = EnergyType.INVALID;
        }
        prepared = true;
    }

    @Override
    public void asyncInit(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        prepareBlockState(tileEntity);
    }

    @Override
    public boolean shouldRunAsyncInit(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        return true;
    }

    @Override
    public void init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!prepared) {
            prepareBlockState(tileEntity);
        }
    }

    @Override
    public void init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        isItem = true;
        this.itemStack = itemStack;
        energyType = EnergyType.RECEIVE;
        prepared = true;
    }

    @Override
    public void clear() {
        this.energyType = EnergyType.INVALID;
        this.send = null;
        this.receive = null;
        this.itemStack = ItemStack.EMPTY;
        this.isItem = false;
        this.initialized = false;
        this.prepared = false;
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
