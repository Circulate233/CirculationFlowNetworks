package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.HandlerTickResult;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.network.nodes.HubNode;
import crazypants.enderio.base.machine.base.te.AbstractCapabilityGeneratorEntity;
import crazypants.enderio.base.machine.base.te.AbstractCapabilityMachineEntity;
import crazypants.enderio.base.machine.baselegacy.AbstractGeneratorEntity;
import crazypants.enderio.base.machine.modes.IoMode;
import crazypants.enderio.base.power.EnergyTank;
import crazypants.enderio.base.power.IEnergyTank;
import crazypants.enderio.base.power.IPowerStorage;
import crazypants.enderio.base.power.forge.tile.ILegacyPoweredTile;
import crazypants.enderio.powertools.machine.capbank.TileCapBank;
import crazypants.enderio.powertools.machine.capbank.network.ICapBankNetwork;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class EIOHandler implements IEnergyHandler {

    private static final HandlerBindingPolicy ORDINARY_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.FIXED,
        HandlerBindingPolicy.MappingScope.NONE,
        HandlerBindingPolicy.PairMatching.NONE
    );
    private static final HandlerBindingPolicy CAP_BANK_POLICY = HandlerBindingPolicy.of(
        HandlerBindingPolicy.TickLifecycle.BEGIN_TICK,
        HandlerBindingPolicy.RoleScope.ENDPOINT_DYNAMIC,
        HandlerBindingPolicy.MappingScope.SHARED_BACKEND,
        HandlerBindingPolicy.PairMatching.NONE
    );

    @Nullable
    private TileEntity blockEntity;
    @Nullable
    private HandlerInvalidationSink invalidationSink;
    @Nullable
    private TileCapBank capBank;
    @Nullable
    private IPowerStorage backendIdentity;
    @Nullable
    private ILegacyPoweredTile legacySend;
    @Nullable
    private ILegacyPoweredTile.Receiver legacyReceive;
    @Nullable
    private EnergyTank machineSend;
    @Nullable
    private EnergyTank machineReceive;
    @Nullable
    private EnumFacing sendFacing;
    @Nullable
    private EnumFacing receiveFacing;
    private HandlerBindingPolicy policy = ORDINARY_POLICY;
    private EnergyType energyType = EnergyType.INVALID;
    private boolean supportsSend;
    private boolean supportsReceive;
    private long activeEpoch = Long.MIN_VALUE;

    public static boolean supports(TileEntity tileEntity) {
        if (tileEntity instanceof TileCapBank bank) {
            return capBankType(bank) != EnergyType.INVALID;
        }
        return ordinaryType(tileEntity) != EnergyType.INVALID;
    }

    private static IPowerStorage resolveCapBankStorage(TileCapBank capBank, @Nullable ICapBankNetwork network) {
        if (network != null) {
            return network;
        }
        IPowerStorage controller = capBank.getController();
        return controller != null ? controller : capBank;
    }

    private static EnergyType capBankType(TileCapBank bank) {
        ICapBankNetwork network = bank.getNetwork();
        return capBankType(bank, network);
    }

    private static EnergyType capBankType(TileCapBank bank, @Nullable ICapBankNetwork network) {
        if (network == null) {
            return EnergyType.STORAGE;
        }
        boolean inputEnabled = false;
        boolean outputEnabled = false;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (!inputEnabled) inputEnabled = bank.isInputEnabled(facing);
            if (!outputEnabled) outputEnabled = bank.isOutputEnabled(facing);
            if (inputEnabled && outputEnabled) break;
        }
        return capBankEndpointType(inputEnabled, outputEnabled);
    }

    static EnergyType capBankEndpointType(boolean inputEnabled, boolean outputEnabled) {
        return structuralType(outputEnabled, inputEnabled);
    }

    private static EnergyType ordinaryType(TileEntity tileEntity) {
        boolean send = false;
        boolean receive = false;
        if (tileEntity instanceof ILegacyPoweredTile powered) {
            for (EnumFacing facing : EnumFacing.VALUES) {
                if (!powered.canConnectEnergy(facing)) continue;
                send |= powered instanceof AbstractGeneratorEntity generator && generator.getMaxEnergySent() > 0;
                receive |= powered instanceof ILegacyPoweredTile.Receiver receiver
                    && receiver.getMaxEnergyRecieved(facing) > 0;
            }
        } else if (tileEntity instanceof AbstractCapabilityMachineEntity machine) {
            IEnergyTank tank = machine.getEnergy();
            if (!(tank instanceof EnergyTank energyTank)) return EnergyType.INVALID;
            for (EnumFacing facing : EnumFacing.VALUES) {
                IoMode mode = machine.getIoMode(facing);
                if (mode == null) continue;
                send |= machine instanceof AbstractCapabilityGeneratorEntity && mode.canOutput()
                    && energyTank.getMaxUsage() > 0;
                receive |= mode.canRecieveInput() && energyTank.getMaxEnergyRecieved() > 0;
            }
        }
        return structuralType(send, receive);
    }

    private static EnergyType structuralType(boolean send, boolean receive) {
        if (send) return receive ? EnergyType.STORAGE : EnergyType.SEND;
        return receive ? EnergyType.RECEIVE : EnergyType.INVALID;
    }

    private static boolean validLegacySend(ILegacyPoweredTile powered, @Nullable EnumFacing facing) {
        return facing != null && powered.canConnectEnergy(facing)
            && powered instanceof AbstractGeneratorEntity generator && generator.getMaxEnergySent() > 0;
    }

    private static boolean validLegacyReceive(ILegacyPoweredTile powered, @Nullable EnumFacing facing) {
        return facing != null && powered.canConnectEnergy(facing)
            && powered instanceof ILegacyPoweredTile.Receiver receiver
            && receiver.getMaxEnergyRecieved(facing) > 0;
    }

    private static boolean validMachineSend(AbstractCapabilityMachineEntity machine, EnergyTank tank,
                                            @Nullable EnumFacing facing) {
        if (facing == null || !(machine instanceof AbstractCapabilityGeneratorEntity) || tank.getMaxUsage() <= 0) {
            return false;
        }
        IoMode mode = machine.getIoMode(facing);
        return mode != null && mode.canOutput();
    }

    private static boolean validMachineReceive(AbstractCapabilityMachineEntity machine, EnergyTank tank,
                                               @Nullable EnumFacing facing) {
        if (facing == null || tank.getMaxEnergyRecieved() <= 0) return false;
        IoMode mode = machine.getIoMode(facing);
        return mode != null && mode.canRecieveInput();
    }

    private static int clamp(long value) {
        if (value <= 0L) return 0;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static int clamp(long value, long firstLimit, long secondLimit) {
        if (value <= 0L || firstLimit <= 0L || secondLimit <= 0L) return 0;
        long result = Math.min(value, Math.min(firstLimit, secondLimit));
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private boolean refreshOrdinary() {
        legacySend = null;
        legacyReceive = null;
        machineSend = null;
        machineReceive = null;
        if (blockEntity instanceof ILegacyPoweredTile powered) {
            boolean sendValid;
            boolean receiveValid;
            if (supportsSend && supportsReceive && sendFacing == receiveFacing) {
                EnumFacing facing = sendFacing;
                boolean connected = facing != null && powered.canConnectEnergy(facing);
                sendValid = connected && powered instanceof AbstractGeneratorEntity generator
                    && generator.getMaxEnergySent() > 0;
                receiveValid = connected && powered instanceof ILegacyPoweredTile.Receiver receiver
                    && receiver.getMaxEnergyRecieved(facing) > 0;
            } else {
                sendValid = !supportsSend || validLegacySend(powered, sendFacing);
                receiveValid = !supportsReceive || validLegacyReceive(powered, receiveFacing);
            }
            if (sendValid && receiveValid) {
                if (supportsSend) legacySend = powered;
                if (supportsReceive) legacyReceive = (ILegacyPoweredTile.Receiver) powered;
                return true;
            }
            return scanLegacy(powered);
        }
        if (blockEntity instanceof AbstractCapabilityMachineEntity machine) {
            IEnergyTank tank = machine.getEnergy();
            if (!(tank instanceof EnergyTank energyTank)) return false;
            boolean sendValid;
            boolean receiveValid;
            if (supportsSend && supportsReceive && sendFacing == receiveFacing) {
                EnumFacing facing = sendFacing;
                boolean sendLimitValid = machine instanceof AbstractCapabilityGeneratorEntity
                    && energyTank.getMaxUsage() > 0;
                boolean receiveLimitValid = energyTank.getMaxEnergyRecieved() > 0;
                IoMode mode = facing != null && (sendLimitValid || receiveLimitValid) ? machine.getIoMode(facing) : null;
                sendValid = sendLimitValid && mode != null && mode.canOutput();
                receiveValid = receiveLimitValid && mode != null && mode.canRecieveInput();
            } else {
                sendValid = !supportsSend || validMachineSend(machine, energyTank, sendFacing);
                receiveValid = !supportsReceive || validMachineReceive(machine, energyTank, receiveFacing);
            }
            if (sendValid && receiveValid) {
                if (supportsSend) machineSend = energyTank;
                if (supportsReceive) machineReceive = energyTank;
                return true;
            }
            return scanMachine(machine, energyTank);
        }
        return false;
    }

    private boolean scanLegacy(ILegacyPoweredTile powered) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (supportsSend && legacySend == null && validLegacySend(powered, facing)) {
                legacySend = powered;
                sendFacing = facing;
            }
            if (supportsReceive && legacyReceive == null && validLegacyReceive(powered, facing)) {
                legacyReceive = (ILegacyPoweredTile.Receiver) powered;
                receiveFacing = facing;
            }
        }
        return (!supportsSend || legacySend != null) && (!supportsReceive || legacyReceive != null);
    }

    private boolean scanMachine(AbstractCapabilityMachineEntity machine, EnergyTank tank) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (supportsSend && machineSend == null && validMachineSend(machine, tank, facing)) {
                machineSend = tank;
                sendFacing = facing;
            }
            if (supportsReceive && machineReceive == null && validMachineReceive(machine, tank, facing)) {
                machineReceive = tank;
                receiveFacing = facing;
            }
        }
        return (!supportsSend || machineSend != null) && (!supportsReceive || machineReceive != null);
    }

    @Override
    public HandlerBindingPolicy bindingPolicy() {
        return policy;
    }

    @Override
    public void bindBlockEntity(TileEntity tileEntity, HandlerInvalidationSink invalidationSink) {
        if (blockEntity != null) throw new IllegalStateException("Ender IO handler is already bound");
        blockEntity = Objects.requireNonNull(tileEntity, "tileEntity");
        this.invalidationSink = Objects.requireNonNull(invalidationSink, "invalidationSink");
        if (tileEntity instanceof TileCapBank bank) {
            policy = CAP_BANK_POLICY;
            capBank = bank;
            ICapBankNetwork network = bank.getNetwork();
            backendIdentity = resolveCapBankStorage(bank, network);
            energyType = capBankType(bank, network);
            if (energyType == EnergyType.INVALID) {
                throw new IllegalArgumentException("Ender IO capacitor bank has no active storage role");
            }
            return;
        }
        policy = ORDINARY_POLICY;
        energyType = ordinaryType(tileEntity);
        supportsSend = energyType == EnergyType.SEND || energyType == EnergyType.STORAGE;
        supportsReceive = energyType == EnergyType.RECEIVE || energyType == EnergyType.STORAGE;
        if (energyType == EnergyType.INVALID || !refreshOrdinary()) {
            throw new IllegalArgumentException("Ender IO block entity has no structural energy role");
        }
    }

    @Override
    public HandlerTickResult beginServerTick(long epoch) {
        if (blockEntity == null || invalidationSink == null) {
            throw new IllegalStateException("Ender IO handler has no block-entity binding");
        }
        if (epoch <= activeEpoch) {
            throw new IllegalArgumentException("Ender IO epoch must increase: previous " + activeEpoch + ", got " + epoch);
        }
        activeEpoch = epoch;
        if (capBank != null) {
            ICapBankNetwork network = capBank.getNetwork();
            IPowerStorage currentBackend = resolveCapBankStorage(capBank, network);
            EnergyType currentType = capBankType(capBank, network);
            if (currentType == EnergyType.INVALID) {
                energyType = EnergyType.INVALID;
                backendIdentity = null;
                return HandlerTickResult.SUSPEND_UNTIL_REBIND;
            }
            boolean backendChanged = currentBackend != backendIdentity;
            boolean changed = backendChanged || currentType != energyType;
            if (backendChanged) invalidationSink.backendChanged();
            backendIdentity = currentBackend;
            energyType = currentType;
            return changed ? HandlerTickResult.STATE_CHANGED : HandlerTickResult.UNCHANGED;
        }
        if (!refreshOrdinary()) {
            energyType = EnergyType.INVALID;
            return HandlerTickResult.SUSPEND_UNTIL_REBIND;
        }
        return HandlerTickResult.UNCHANGED;
    }

    @Override
    public void endServerTick(long epoch) {
        throw new IllegalStateException("Ender IO endpoint handler uses begin-only tick lifecycle");
    }

    @Override
    public void unbindBlockEntity() {
        blockEntity = null;
        invalidationSink = null;
        capBank = null;
        backendIdentity = null;
        legacySend = null;
        legacyReceive = null;
        machineSend = null;
        machineReceive = null;
        sendFacing = null;
        receiveFacing = null;
        policy = ORDINARY_POLICY;
        energyType = EnergyType.INVALID;
        supportsSend = false;
        supportsReceive = false;
        activeEpoch = Long.MIN_VALUE;
    }

    public IPowerStorage backendIdentity() {
        return Objects.requireNonNull(backendIdentity, "Capacitor bank endpoint has no backend identity");
    }

    @Override
    public void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        throw new UnsupportedOperationException("Ender IO does not support item energy bindings");
    }

    @Override
    public void unbindItem() {
        throw new UnsupportedOperationException("Ender IO does not support item energy bindings");
    }

    private void requireOrdinaryBackend() {
        if (capBank != null) throw new IllegalStateException("Capacitor bank endpoint cannot be used as its backend");
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maximum, @Nullable HubNode.HubMetadata metadata) {
        requireOrdinaryBackend();
        if (legacyReceive != null && receiveFacing != null) {
            int accepted = legacyReceive.receiveEnergy(receiveFacing, clamp(maximum.asLongClamped()), false);
            return accepted > 0 ? EnergyAmount.obtain(accepted) : EnergyAmounts.ZERO;
        }
        if (machineReceive != null) {
            int stored = machineReceive.getEnergyStored();
            int accepted = clamp(maximum.asLongClamped(), (long) machineReceive.getMaxEnergyStored() - stored,
                machineReceive.getMaxEnergyRecieved());
            if (accepted > 0) machineReceive.setEnergyStored(stored + accepted);
            return accepted > 0 ? EnergyAmount.obtain(accepted) : EnergyAmounts.ZERO;
        }
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maximum, @Nullable HubNode.HubMetadata metadata) {
        requireOrdinaryBackend();
        if (legacySend != null) {
            int output = ((AbstractGeneratorEntity) legacySend).getMaxEnergySent();
            int stored = legacySend.getEnergyStored();
            int extracted = clamp(maximum.asLongClamped(), stored, output);
            if (extracted > 0) legacySend.setEnergyStored(stored - extracted);
            return extracted > 0 ? EnergyAmount.obtain(extracted) : EnergyAmounts.ZERO;
        }
        if (machineSend != null) {
            int stored = machineSend.getEnergyStored();
            int extracted = clamp(maximum.asLongClamped(), stored, machineSend.getMaxUsage());
            if (extracted > 0) machineSend.setEnergyStored(stored - extracted);
            return extracted > 0 ? EnergyAmount.obtain(extracted) : EnergyAmounts.ZERO;
        }
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata metadata) {
        requireOrdinaryBackend();
        if (legacySend != null) return EnergyAmount.obtain(clamp(Integer.MAX_VALUE, legacySend.getEnergyStored(),
            ((AbstractGeneratorEntity) legacySend).getMaxEnergySent()));
        if (machineSend != null)
            return EnergyAmount.obtain(clamp(Integer.MAX_VALUE, machineSend.getEnergyStored(), machineSend.getMaxUsage()));
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata metadata) {
        requireOrdinaryBackend();
        if (legacyReceive != null && receiveFacing != null) {
            return EnergyAmount.obtain(Math.max(0, legacyReceive.receiveEnergy(receiveFacing, Integer.MAX_VALUE, true)));
        }
        if (machineReceive != null) return EnergyAmount.obtain(clamp(Integer.MAX_VALUE,
            machineReceive.getMaxEnergyStored() - machineReceive.getEnergyStored(), machineReceive.getMaxEnergyRecieved()));
        return EnergyAmounts.ZERO;
    }

    @Override
    public boolean canExtract(IEnergyHandler receiver, @Nullable HubNode.HubMetadata metadata) {
        EnergyAmount value = canExtractValue(metadata);
        try {
            return value.isPositive();
        } finally {
            value.recycle();
        }
    }

    @Override
    public boolean canReceive(IEnergyHandler sender, @Nullable HubNode.HubMetadata metadata) {
        EnergyAmount value = canReceiveValue(metadata);
        try {
            return value.isPositive();
        } finally {
            value.recycle();
        }
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata metadata) {
        return energyType;
    }
}
