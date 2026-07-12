package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
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

import java.util.Objects;

public class EUHandler implements IEnergyHandler {
    private static final double MAX_EU_TRANSFER = Long.MAX_VALUE / 4.0D;
    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.STATIC,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );

    private EnergyType energyType;
    @Nullable
    private IEnergySource send;

    private boolean isItem;
    private ItemStack itemStack = ItemStack.EMPTY;

    @Nullable
    private IEnergySink receive;
    @Nullable
    private IEnergyTile cachedTile;
    private EnumFacing receiveFacing = EnumFacing.NORTH;
    @Nullable
    private TileEntity blockEntity;
    private long activeEpoch = Long.MIN_VALUE;

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

    private void clearTickState() {
        energyType = EnergyType.INVALID;
        send = null;
        receive = null;
        receiveFacing = EnumFacing.NORTH;
    }

    @Nullable
    private IEnergyTile getCachedTile(BlockPos pos) {
        if (cachedTile == null) {
            return null;
        }
        BlockPos cachedPos = EnergyNet.instance.getPos(cachedTile);
        if (!pos.equals(cachedPos)) {
            cachedTile = null;
            return null;
        }
        return cachedTile;
    }

    @Nullable
    private IEnergyTile findEnergyTile(TileEntity tileEntity, BlockPos pos) {
        IEnergyTile tile = getCachedTile(pos);
        if (tile != null) {
            return tile;
        }
        tile = resolveEnergyTile(tileEntity);
        cachedTile = tile;
        return tile;
    }

    /**
     * Resolves the IC2 tile currently registered for a block entity without assuming that
     * {@link EnergyNet#getSubTile(net.minecraft.world.World, BlockPos)} is already populated.
     * IC2 emits its load event before that public lookup is reliable for some machine classes.
     */
    @Nullable
    public static IEnergyTile resolveEnergyTile(TileEntity tileEntity) {
        Objects.requireNonNull(tileEntity, "tileEntity");
        if (tileEntity.getWorld() == null || tileEntity.getPos() == null) {
            return null;
        }
        BlockPos pos = tileEntity.getPos();
        IEnergyTile tile = EnergyNet.instance.getSubTile(tileEntity.getWorld(), pos);
        if (tile != null) {
            return tile;
        }
        var data = WorldData.get(tileEntity.getWorld(), false);
        if (data == null) {
            return null;
        }
        EnergyNetLocal energyNet = data.energyNet;
        Tile tiles = energyNet.getTile(pos);
        if (tiles != null) {
            for (IEnergyTile subTile : tiles.getSubTiles()) {
                if (EnergyNet.instance.getPos(subTile).equals(pos)) {
                    return subTile;
                }
            }
        }
        if (tileEntity instanceof IEnergyTile energyTile && pos.equals(EnergyNet.instance.getPos(energyTile))) {
            return energyTile;
        }
        return null;
    }

    /**
     * Returns whether an IC2 tile has a usable CFN structural role. Cables deliberately do not
     * qualify because connecting them would create a second energy network path.
     */
    public static boolean supportsEnergyTile(@Nullable IEnergyTile tile) {
        if (tile instanceof ic2.core.block.wiring.TileEntityCable) {
            return false;
        }
        if (tile instanceof IEnergySource) {
            return true;
        }
        if (tile instanceof IEnergySink sink) {
            for (EnumFacing facing : EnumFacing.VALUES) {
                if (sink.acceptsEnergyFrom(null, facing)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void prepareBlockState(TileEntity tileEntity) {
        clearTickState();
        BlockPos pos = tileEntity.getPos();
        IEnergyTile tile = findEnergyTile(tileEntity, pos);
        boolean output = tile instanceof IEnergySource;
        boolean input = tile instanceof IEnergySink;
        if (output) {
            send = (IEnergySource) tile;
            energyType = EnergyType.SEND;
        }
        if (input) {
            IEnergySink sink = (IEnergySink) tile;
            for (var value : EnumFacing.values()) {
                if (sink.acceptsEnergyFrom(null, value)) {
                    receiveFacing = value;
                    receive = sink;
                    energyType = output ? EnergyType.STORAGE : EnergyType.RECEIVE;
                    break;
                }
            }
        }
    }

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    @Override
    public void bindBlockEntity(TileEntity tileEntity, HandlerInvalidationSink invalidationSink) {
        if (blockEntity != null || isItem) {
            throw new IllegalStateException("EU handler is already bound");
        }
        blockEntity = Objects.requireNonNull(tileEntity, "tileEntity");
        Objects.requireNonNull(invalidationSink, "invalidationSink");
        cachedTile = null;
        prepareBlockState(tileEntity);
        if (energyType == EnergyType.INVALID) {
            throw new IllegalArgumentException("IC2 block entity has no structural source or sink role");
        }
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        if (blockEntity == null) {
            throw new IllegalStateException("EU handler has no block-entity binding");
        }
        if (epoch <= activeEpoch) {
            throw new IllegalArgumentException("EU handler epoch must increase: previous " + activeEpoch + ", got " + epoch);
        }
        activeEpoch = epoch;
        return HandlerTickResult.UNCHANGED;
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("EU handler has a static tick lifecycle");
    }

    @Override
    public void unbindBlockEntity() {
        clearTickState();
        cachedTile = null;
        blockEntity = null;
        activeEpoch = Long.MIN_VALUE;
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (blockEntity != null || isItem) {
            throw new IllegalStateException("EU handler is already bound");
        }
        isItem = true;
        this.itemStack = itemStack;
        energyType = EnergyType.RECEIVE;
    }

    @Override
    public void unbindItem() {
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
