package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.utils.EnergyAmountConversionUtils;
import com.github.bsideup.jabel.Desugar;
import mekanism.api.energy.IEnergizedItem;
import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.api.energy.IStrictEnergyOutputter;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.content.matrix.SynchronizedMatrixData;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tile.TileEntityEnergyCube;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

public class MEKHandler implements IEnergyHandler {

    private static final HandlerBindingPolicy BINDING_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );
    private static final double FE_TO_MEK_RATIO = 2.5D;
    private static final int ROLE_UNKNOWN = 0;
    private static final int ROLE_SUPPORTED = 1;
    private static final int ROLE_UNSUPPORTED = 2;
    private static final int MODE_UNKNOWN = 0;
    private static final int MODE_ENERGY_CUBE = 1;
    private static final int MODE_INDUCTION_PORT = 2;
    private static final int MODE_ORDINARY = 3;
    private static final InductionPortAccess INDUCTION_PORT = resolveInductionPortAccess();
    private static final BigInteger MAX_DIRECT_DOUBLE_TRANSFER = BigDecimal.valueOf(Double.MAX_VALUE).toBigInteger();
    private static final BigInteger MAX_SCALED_DOUBLE_TRANSFER = BigDecimal.valueOf(Double.MAX_VALUE / FE_TO_MEK_RATIO).toBigInteger();

    private final EnergyAmount maxReceive = EnergyAmount.obtain(MAX_SCALED_DOUBLE_TRANSFER);
    private final EnergyAmount maxExtract = EnergyAmount.obtain(MAX_SCALED_DOUBLE_TRANSFER);
    private final EnergyAmount needEnergy = EnergyAmount.obtain(0L);
    @Nullable
    private IStrictEnergyStorage send;
    @Nullable
    private IStrictEnergyStorage receive;
    private boolean isItem;
    private IEnergizedItem receiveItem;
    private ItemStack stack = ItemStack.EMPTY;
    private EnergyType energyType = EnergyType.INVALID;
    private boolean creative;
    @Nullable
    private TileEntity blockEntity;
    private long activeEpoch = Long.MIN_VALUE;
    @Nullable
    private EnumFacing sendFacing;
    @Nullable
    private EnumFacing receiveFacing;
    private int sendState = ROLE_UNKNOWN;
    private int receiveState = ROLE_UNKNOWN;
    private int blockMode = MODE_UNKNOWN;
    private boolean supportsSend;
    private boolean supportsReceive;

    private static void clampToMaximum(EnergyAmount amount, BigInteger maximum) {
        if (amount == null || !amount.isInitialized() || amount.isNegative()) {
            return;
        }
        if (amount.asBigInteger().compareTo(maximum) > 0) {
            amount.init(maximum);
        }
    }

    private static EnergyType structuralType(boolean send, boolean receive) {
        if (send) {
            return receive ? EnergyType.STORAGE : EnergyType.SEND;
        }
        return receive ? EnergyType.RECEIVE : EnergyType.INVALID;
    }

    private void bindOrdinaryHint(TileEntity tileEntity) {
        if (!(tileEntity instanceof IStrictEnergyStorage storage)) {
            return;
        }
        if (sendState == ROLE_SUPPORTED && sendFacing != null) {
            if (tileEntity instanceof IStrictEnergyOutputter outputter && outputter.canOutputEnergy(sendFacing)) {
                send = storage;
            } else {
                sendState = ROLE_UNKNOWN;
            }
        }
        if (receiveState == ROLE_SUPPORTED && receiveFacing != null) {
            if (tileEntity instanceof IStrictEnergyAcceptor acceptor && acceptor.canReceiveEnergy(receiveFacing)) {
                receive = storage;
            } else {
                receiveState = ROLE_UNKNOWN;
            }
        }
    }

    private int bindOrdinarySide(TileEntity tileEntity,
                                 IStrictEnergyStorage storage,
                                 EnumFacing facing,
                                 boolean needSendScan,
                                 boolean needReceiveScan) {
        if (!needSendScan && !needReceiveScan) {
            return 0;
        }
        int attempted = 0;
        if (needSendScan) {
            attempted |= 1;
            if (tileEntity instanceof IStrictEnergyOutputter outputter && outputter.canOutputEnergy(facing)) {
                send = storage;
                sendFacing = facing;
                sendState = ROLE_SUPPORTED;
            }
        }
        if (needReceiveScan) {
            attempted |= 2;
            if (tileEntity instanceof IStrictEnergyAcceptor acceptor && acceptor.canReceiveEnergy(facing)) {
                receive = storage;
                receiveFacing = facing;
                receiveState = ROLE_SUPPORTED;
            }
        }
        return attempted;
    }

    private void prepareEnergyCube(TileEntityEnergyCube tileEntity) {
        creative = tileEntity.tier == EnergyCubeTier.CREATIVE;
        send = tileEntity;
        receive = tileEntity;
        energyType = EnergyType.STORAGE;
        double maxOutput = tileEntity.getMaxOutput();
        EnergyAmountConversionUtils.setFromDoubleFloor(maxExtract, maxOutput);
        EnergyAmountConversionUtils.setFromDoubleFloor(maxReceive, maxOutput);
    }

    private void prepareInductionPort(TileEntity tileEntity) {
        if (!(tileEntity instanceof IStrictEnergyStorage storage)) {
            throw new IllegalStateException("Mekanism induction port does not implement IStrictEnergyStorage: "
                + describeTile(tileEntity, INDUCTION_PORT.layoutName()));
        }
        send = storage;
        receive = storage;
        energyType = EnergyType.STORAGE;
        var matrixData = INDUCTION_PORT.structure(tileEntity);
        if (matrixData != null) {
            EnergyAmountConversionUtils.setFromDoubleFloor(maxExtract, matrixData.getRemainingOutput());
            EnergyAmountConversionUtils.setFromDoubleFloor(maxReceive, matrixData.getRemainingInput());
        }
    }

    private static boolean isInductionPort(TileEntity tileEntity) {
        return INDUCTION_PORT.isInstance(tileEntity);
    }

    private static String describeTile(TileEntity tileEntity, String layoutName) {
        return "tile=" + tileEntity.getClass().getName() + ", pos=" + tileEntity.getPos() + ", layout=" + layoutName;
    }

    private void prepareOrdinaryTransferLimits(TileEntity tileEntity) {
        if (tileEntity instanceof IEnergyWrapper energyWrapper) {
            double maxOutput = energyWrapper.getMaxOutput();
            if (maxOutput != 0.0D) {
                EnergyAmountConversionUtils.setFromDoubleFloor(maxExtract, maxOutput);
                EnergyAmountConversionUtils.setFromDoubleFloor(maxReceive, maxOutput);
                return;
            }
        }
        maxExtract.init(MAX_SCALED_DOUBLE_TRANSFER);
        maxReceive.init(MAX_SCALED_DOUBLE_TRANSFER);
    }

    private void prepareOrdinaryBlock(TileEntity tileEntity) {
        if (!(tileEntity instanceof IStrictEnergyStorage storage)) {
            energyType = EnergyType.INVALID;
            return;
        }
        bindOrdinaryHint(tileEntity);
        boolean needSendScan = send == null && sendState == ROLE_UNKNOWN;
        boolean needReceiveScan = receive == null && receiveState == ROLE_UNKNOWN;
        boolean attemptedSend = false;
        boolean attemptedReceive = false;
        for (var i = 0; i < EnumFacing.VALUES.length; i++) {
            if (!needSendScan && !needReceiveScan) break;
            int attempted = bindOrdinarySide(tileEntity, storage, EnumFacing.VALUES[i], needSendScan, needReceiveScan);
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
        energyType = structuralType(send != null, receive != null);

        prepareOrdinaryTransferLimits(tileEntity);
    }

    private boolean refreshOrdinaryCached(TileEntity tileEntity) {
        if (!(tileEntity instanceof IStrictEnergyStorage storage)) {
            return false;
        }
        send = null;
        receive = null;
        if (supportsSend && sendFacing != null && tileEntity instanceof IStrictEnergyOutputter outputter
            && outputter.canOutputEnergy(sendFacing)) {
            send = storage;
        }
        if (supportsReceive && receiveFacing != null && tileEntity instanceof IStrictEnergyAcceptor acceptor
            && acceptor.canReceiveEnergy(receiveFacing)) {
            receive = storage;
        }
        prepareOrdinaryTransferLimits(tileEntity);
        return (!supportsSend || send != null) && (!supportsReceive || receive != null);
    }

    private boolean rediscoverRequiredOrdinary(TileEntity tileEntity) {
        if (!(tileEntity instanceof IStrictEnergyStorage storage)) {
            return false;
        }
        send = null;
        receive = null;
        sendFacing = null;
        receiveFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (supportsSend && send == null && tileEntity instanceof IStrictEnergyOutputter outputter
                && outputter.canOutputEnergy(facing)) {
                send = storage;
                sendFacing = facing;
            }
            if (supportsReceive && receive == null && tileEntity instanceof IStrictEnergyAcceptor acceptor
                && acceptor.canReceiveEnergy(facing)) {
                receive = storage;
                receiveFacing = facing;
            }
        }
        sendState = supportsSend && send != null ? ROLE_SUPPORTED : ROLE_UNSUPPORTED;
        receiveState = supportsReceive && receive != null ? ROLE_SUPPORTED : ROLE_UNSUPPORTED;
        return (!supportsSend || send != null) && (!supportsReceive || receive != null);
    }

    private void refreshBlockState(TileEntity tileEntity) {
        if (blockMode == MODE_ENERGY_CUBE && tileEntity instanceof TileEntityEnergyCube te) {
            prepareEnergyCube(te);
        } else if (blockMode == MODE_INDUCTION_PORT && isInductionPort(tileEntity)) {
            prepareInductionPort(tileEntity);
        } else if (blockMode == MODE_ORDINARY && !(tileEntity instanceof TileEntityEnergyCube)
            && !isInductionPort(tileEntity)) {
            prepareOrdinaryBlock(tileEntity);
        } else if (tileEntity instanceof TileEntityEnergyCube te) {
            blockMode = MODE_ENERGY_CUBE;
            prepareEnergyCube(te);
        } else if (isInductionPort(tileEntity)) {
            blockMode = MODE_INDUCTION_PORT;
            prepareInductionPort(tileEntity);
        } else {
            blockMode = MODE_ORDINARY;
            prepareOrdinaryBlock(tileEntity);
        }
    }

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return BINDING_POLICY;
    }

    @Override
    public void bindBlockEntity(TileEntity tileEntity, HandlerInvalidationSink invalidationSink) {
        if (blockEntity != null || isItem) {
            throw new IllegalStateException("Mekanism handler is already bound");
        }
        blockEntity = Objects.requireNonNull(tileEntity, "tileEntity");
        Objects.requireNonNull(invalidationSink, "invalidationSink");
        refreshBlockState(tileEntity);
        supportsSend = send != null;
        supportsReceive = receive != null;
        energyType = structuralType(supportsSend, supportsReceive);
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        if (blockEntity == null) {
            throw new IllegalStateException("Mekanism handler has no block-entity binding");
        }
        if (epoch <= activeEpoch) {
            throw new IllegalArgumentException("Mekanism handler epoch must increase: previous " + activeEpoch + ", got " + epoch);
        }
        activeEpoch = epoch;
        send = null;
        receive = null;
        energyType = EnergyType.INVALID;
        creative = false;
        maxReceive.setZero();
        maxExtract.setZero();
        boolean valid;
        if (blockMode == MODE_ENERGY_CUBE && blockEntity instanceof TileEntityEnergyCube energyCube) {
            prepareEnergyCube(energyCube);
            valid = true;
        } else if (blockMode == MODE_INDUCTION_PORT && INDUCTION_PORT.isInstance(blockEntity)) {
            prepareInductionPort(blockEntity);
            valid = true;
        } else if (blockMode == MODE_ORDINARY) {
            valid = refreshOrdinaryCached(blockEntity);
            if (!valid) {
                valid = rediscoverRequiredOrdinary(blockEntity);
            }
        } else {
            valid = false;
        }
        if (valid && (supportsSend || supportsReceive)) {
            energyType = structuralType(supportsSend, supportsReceive);
            return HandlerTickResult.UNCHANGED;
        }
        send = null;
        receive = null;
        energyType = EnergyType.INVALID;
        return HandlerTickResult.SUSPEND_UNTIL_REBIND;
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("Mekanism handler uses begin-only tick lifecycle");
    }

    @Override
    public void unbindBlockEntity() {
        send = null;
        receive = null;
        energyType = EnergyType.INVALID;
        creative = false;
        maxReceive.setZero();
        maxExtract.setZero();
        activeEpoch = Long.MIN_VALUE;
        blockEntity = null;
        sendFacing = null;
        receiveFacing = null;
        sendState = ROLE_UNKNOWN;
        receiveState = ROLE_UNKNOWN;
        blockMode = MODE_UNKNOWN;
        supportsSend = false;
        supportsReceive = false;
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (blockEntity != null || isItem) {
            throw new IllegalStateException("Mekanism handler is already bound");
        }
        isItem = true;
        receiveItem = (IEnergizedItem) itemStack.getItem();
        double i = receiveItem.getMaxTransfer(itemStack);
        double r = receiveItem.getMaxEnergy(itemStack) - receiveItem.getEnergy(itemStack);
        EnergyAmountConversionUtils.setFromDoubleFloor(needEnergy, Math.max(0.0D, i == 0 ? r : Math.min(r, i)));
        stack = itemStack;
        energyType = EnergyType.RECEIVE;
    }

    @Override
    public void unbindItem() {
        maxReceive.setZero();
        maxExtract.setZero();
        send = null;
        receive = null;
        receiveItem = null;
        energyType = EnergyType.INVALID;
        creative = false;
        isItem = false;
        needEnergy.setZero();
        stack = ItemStack.EMPTY;
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            EnergyAmount accepted = EnergyAmount.obtain(needEnergy).min(maxReceive);
            clampToMaximum(accepted, MAX_DIRECT_DOUBLE_TRANSFER);
            if (!accepted.isZero()) {
                receiveItem.setEnergy(stack, receiveItem.getEnergy(stack) + EnergyAmountConversionUtils.toDoubleClamped(accepted));
                needEnergy.subtract(accepted);
            }
            return accepted;
        } else {
            if (receive == null) return EnergyAmounts.ZERO;
            EnergyAmount receivable = EnergyAmount.obtain(maxReceive);
            clampToMaximum(receivable, MAX_SCALED_DOUBLE_TRANSFER);
            if (!receivable.isZero()) {
                receive.setEnergy(receive.getEnergy() + EnergyAmountConversionUtils.toDoubleClamped(receivable) * FE_TO_MEK_RATIO);
            }
            return receivable;
        }
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        EnergyAmount extractable = EnergyAmount.obtain(maxExtract);
        clampToMaximum(extractable, MAX_SCALED_DOUBLE_TRANSFER);
        if (!extractable.isZero() && !creative) {
            send.setEnergy(send.getEnergy() - EnergyAmountConversionUtils.toDoubleClamped(extractable) * FE_TO_MEK_RATIO);
        }
        return extractable;
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (send == null) return EnergyAmounts.ZERO;
        if (creative) return EnergyAmount.obtain(maxExtract);
        EnergyAmount extractable = EnergyAmountConversionUtils.obtainFromDoubleFloor(send.getEnergy() * 0.4D);
        return extractable.min(maxExtract);
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) {
            return EnergyAmount.obtain(needEnergy);
        } else {
            if (receive == null) return EnergyAmounts.ZERO;
            EnergyAmount receivable = EnergyAmountConversionUtils.obtainFromDoubleFloor((receive.getMaxEnergy() - receive.getEnergy()) * 0.4D);
            return receivable.min(maxReceive);
        }
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        if (creative) return true;
        return send != null && send.getEnergy() >= 2.5;
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        if (isItem) return needEnergy.isPositive();
        else return receive != null && (receive.getMaxEnergy() - receive.getEnergy()) * 0.4D > 0.0D;
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return energyType;
    }

    private static InductionPortAccess resolveInductionPortAccess() {
        String dependencyVersion = IStrictEnergyStorage.class.getPackage().getImplementationVersion();
        try {
            ClassLoader loader = MEKHandler.class.getClassLoader();
            ClassNotFoundException missingModern = null;
            for (String candidate : new String[] {
                "mekanism.common.tile.multiblock.TileEntityInductionPort",
                "mekanism.common.tile.TileEntityInductionPort"
            }) {
                try {
                    Class<?> portClass = Class.forName(candidate, false, loader);
                    MethodHandle structureGetter = MethodHandles.publicLookup()
                        .unreflectGetter(portClass.getField("structure"))
                        .asType(MethodType.methodType(SynchronizedMatrixData.class, Object.class));
                    return new InductionPortAccess(portClass, structureGetter, candidate);
                } catch (ClassNotFoundException exception) {
                    if (missingModern == null) {
                        missingModern = exception;
                    }
                }
            }
            IllegalStateException failure = new IllegalStateException(
                "Cannot resolve Mekanism induction-port structure accessor: version="
                    + (dependencyVersion == null ? "unknown" : dependencyVersion)
                    + ", candidates=mekanism.common.tile.multiblock.TileEntityInductionPort,"
                    + "mekanism.common.tile.TileEntityInductionPort, accessor=public field structure",
                missingModern
            );
            CirculationFlowNetworks.LOGGER.error(failure.getMessage(), failure);
            throw failure;
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            IllegalStateException failure = new IllegalStateException(
                "Cannot resolve Mekanism induction-port structure accessor: version="
                    + (dependencyVersion == null ? "unknown" : dependencyVersion),
                exception
            );
            CirculationFlowNetworks.LOGGER.error(failure.getMessage(), failure);
            throw failure;
        }
    }

    @Desugar
    private record InductionPortAccess(Class<?> portClass, MethodHandle structureGetter, String layoutName) {

        private boolean isInstance(TileEntity tileEntity) {
            return portClass.isInstance(tileEntity);
        }

        @Nullable
        private SynchronizedMatrixData structure(TileEntity tileEntity) {
            try {
                return (SynchronizedMatrixData) structureGetter.invokeExact((Object) tileEntity);
            } catch (RuntimeException | Error exception) {
                throw exception;
            } catch (Throwable throwable) {
                throw new IllegalStateException(
                    "Failed to read Mekanism induction-port structure: " + describeTile(tileEntity, layoutName),
                    throwable
                );
            }
        }

        public String layoutName() {
            return layoutName;
        }
    }

}
