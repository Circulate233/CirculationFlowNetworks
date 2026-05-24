package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
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

public class EIOHandler implements IEnergyHandler {

    private static final int ROLE_UNKNOWN = 0;
    private static final int ROLE_SUPPORTED = 1;
    private static final int ROLE_UNSUPPORTED = 2;

    @Nullable
    private IPowerStorage storage;
    @Nullable
    private ICapBankNetwork capBankNetwork;
    @Nullable
    private TileCapBank capBank;
    @Nullable
    private ILegacyPoweredTile legacySendTile;
    @Nullable
    private ILegacyPoweredTile.Receiver legacyReceiveTile;
    @Nullable
    private EnergyTank machineSendTank;
    @Nullable
    private EnergyTank machineReceiveTank;
    @Nullable
    private EnumFacing sendFacing;
    @Nullable
    private EnumFacing receiveFacing;
    @Nullable
    private EnergyType energyType;
    private boolean initialized;
    private boolean prepared;
    private int sendState = ROLE_UNKNOWN;
    private int receiveState = ROLE_UNKNOWN;

    private static int clampPositive(long requested, long... limits) {
        long clamped = Math.max(0L, requested);
        for (long limit : limits) {
            clamped = Math.min(clamped, Math.max(0L, limit));
        }
        if (clamped == 0L) {
            return 0;
        }
        return clamped >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) clamped;
    }

    private static IPowerStorage resolveStorage(TileCapBank capBank) {
        IPowerStorage controller = capBank.getController();
        return controller != null ? controller : capBank;
    }

    private static boolean isMachineEnergySideEnabled(AbstractCapabilityMachineEntity machineEntity, EnumFacing facing) {
        IoMode mode = machineEntity.getIoMode(facing);
        return mode != null && mode.canInputOrOutput();
    }

    private static boolean canMachineSideReceiveEnergy(AbstractCapabilityMachineEntity machineEntity, EnumFacing facing) {
        IoMode mode = machineEntity.getIoMode(facing);
        return mode != null && mode.canRecieveInput();
    }

    private static boolean canMachineSideExtractEnergy(AbstractCapabilityMachineEntity machineEntity, EnumFacing facing) {
        IoMode mode = machineEntity.getIoMode(facing);
        return mode != null && mode.canOutput();
    }

    private static boolean hasEnergy(ILegacyPoweredTile tile) {
        return tile.getEnergyStored() > 0;
    }

    private static boolean hasRoom(ILegacyPoweredTile tile) {
        return tile.getEnergyStored() < tile.getMaxEnergyStored();
    }

    private static boolean hasEnergy(EnergyTank tank) {
        return tank.getEnergyStored() > 0;
    }

    private static boolean hasRoom(EnergyTank tank) {
        return tank.getEnergyStored() < tank.getMaxEnergyStored();
    }

    private static int receiveEnergyDirect(EnergyTank tank, int maxReceive, boolean simulate) {
        int received = Math.max(0, Math.min(maxReceive, Math.min(tank.getMaxEnergyStored() - tank.getEnergyStored(), tank.getMaxEnergyRecieved())));
        if (received > 0 && !simulate) {
            tank.setEnergyStored(tank.getEnergyStored() + received);
        }
        return received;
    }

    private static int extractEnergyDirect(ILegacyPoweredTile tile, int maxExtract, int maxOutput, boolean simulate) {
        int extracted = Math.max(0, Math.min(maxExtract, Math.min(tile.getEnergyStored(), maxOutput)));
        if (extracted > 0 && !simulate) {
            tile.setEnergyStored(tile.getEnergyStored() - extracted);
        }
        return extracted;
    }

    private static int extractEnergyDirect(EnergyTank tank, int maxExtract, int maxOutput, boolean simulate) {
        int extracted = Math.max(0, Math.min(maxExtract, Math.min(tank.getEnergyStored(), maxOutput)));
        if (extracted > 0 && !simulate) {
            tank.setEnergyStored(tank.getEnergyStored() - extracted);
        }
        return extracted;
    }

    private void bindLegacyPoweredTile(ILegacyPoweredTile poweredTile) {
        if (sendState == ROLE_SUPPORTED && sendFacing != null) {
            if (poweredTile instanceof AbstractGeneratorEntity generator
                && poweredTile.canConnectEnergy(sendFacing)
                && hasEnergy(poweredTile)
                && generator.getMaxEnergySent() > 0) {
                legacySendTile = poweredTile;
            } else if (!poweredTile.canConnectEnergy(sendFacing)) {
                sendState = ROLE_UNKNOWN;
            } else {
                legacySendTile = null;
            }
        }
        if (receiveState == ROLE_SUPPORTED && receiveFacing != null) {
            if (poweredTile instanceof ILegacyPoweredTile.Receiver receiver && poweredTile.canConnectEnergy(receiveFacing) && hasRoom(poweredTile)) {
                if (receiver.getMaxEnergyRecieved(receiveFacing) > 0) {
                    legacyReceiveTile = receiver;
                } else {
                    legacyReceiveTile = null;
                }
            } else if (!poweredTile.canConnectEnergy(receiveFacing)) {
                receiveState = ROLE_UNKNOWN;
            } else {
                legacyReceiveTile = null;
            }
        }
        boolean attemptedSend = false;
        boolean attemptedReceive = false;
        for (EnumFacing facing : EnumFacing.VALUES) {
            boolean needSendScan = legacySendTile == null && sendState == ROLE_UNKNOWN;
            boolean needReceiveScan = legacyReceiveTile == null && receiveState == ROLE_UNKNOWN;
            if (!needSendScan && !needReceiveScan) {
                break;
            }
            if (!poweredTile.canConnectEnergy(facing)) {
                continue;
            }
            if (needSendScan && poweredTile instanceof AbstractGeneratorEntity generator && hasEnergy(poweredTile)) {
                attemptedSend = true;
                if (generator.getMaxEnergySent() > 0) {
                    legacySendTile = poweredTile;
                    sendFacing = facing;
                    sendState = ROLE_SUPPORTED;
                }
            }
            if (needReceiveScan && poweredTile instanceof ILegacyPoweredTile.Receiver receiver && hasRoom(poweredTile)) {
                attemptedReceive = true;
                if (receiver.getMaxEnergyRecieved(facing) > 0) {
                    legacyReceiveTile = receiver;
                    receiveFacing = facing;
                    receiveState = ROLE_SUPPORTED;
                }
            }
        }
        if (legacySendTile == null && sendState == ROLE_UNKNOWN && attemptedSend) {
            sendState = ROLE_UNSUPPORTED;
        }
        if (legacyReceiveTile == null && receiveState == ROLE_UNKNOWN && attemptedReceive) {
            receiveState = ROLE_UNSUPPORTED;
        }
    }

    private void bindCapabilityMachine(AbstractCapabilityMachineEntity machineEntity) {
        IEnergyTank tank = machineEntity.getEnergy();
        if (tank == null) {
            return;
        }
        if (!(tank instanceof EnergyTank energyTank)) {
            energyType = EnergyType.INVALID;
            sendState = ROLE_UNSUPPORTED;
            receiveState = ROLE_UNSUPPORTED;
            return;
        }
        if (sendState == ROLE_SUPPORTED && sendFacing != null) {
            if (machineEntity instanceof AbstractCapabilityGeneratorEntity
                && canMachineSideExtractEnergy(machineEntity, sendFacing)
                && hasEnergy(energyTank)
                && energyTank.getMaxUsage() > 0) {
                machineSendTank = energyTank;
            } else if (!canMachineSideExtractEnergy(machineEntity, sendFacing)) {
                sendState = ROLE_UNKNOWN;
            } else {
                machineSendTank = null;
            }
        }
        if (receiveState == ROLE_SUPPORTED && receiveFacing != null) {
            if (canMachineSideReceiveEnergy(machineEntity, receiveFacing) && hasRoom(energyTank) && energyTank.getMaxEnergyRecieved() > 0) {
                machineReceiveTank = energyTank;
            } else if (!canMachineSideReceiveEnergy(machineEntity, receiveFacing)) {
                receiveState = ROLE_UNKNOWN;
            } else {
                machineReceiveTank = null;
            }
        }
        boolean attemptedSend = false;
        boolean attemptedReceive = false;
        for (EnumFacing facing : EnumFacing.VALUES) {
            boolean needSendScan = machineSendTank == null && sendState == ROLE_UNKNOWN;
            boolean needReceiveScan = machineReceiveTank == null && receiveState == ROLE_UNKNOWN;
            if (!needSendScan && !needReceiveScan) {
                break;
            }
            if (!isMachineEnergySideEnabled(machineEntity, facing)) {
                continue;
            }
            if (needSendScan && machineEntity instanceof AbstractCapabilityGeneratorEntity
                && canMachineSideExtractEnergy(machineEntity, facing)
                && hasEnergy(energyTank)) {
                attemptedSend = true;
                if (energyTank.getMaxUsage() > 0) {
                    machineSendTank = energyTank;
                    sendFacing = facing;
                    sendState = ROLE_SUPPORTED;
                }
            }
            if (needReceiveScan && canMachineSideReceiveEnergy(machineEntity, facing) && hasRoom(energyTank)) {
                attemptedReceive = true;
                if (energyTank.getMaxEnergyRecieved() > 0) {
                    machineReceiveTank = energyTank;
                    receiveFacing = facing;
                    receiveState = ROLE_SUPPORTED;
                }
            }
        }
        if (machineSendTank == null && sendState == ROLE_UNKNOWN && attemptedSend) {
            sendState = ROLE_UNSUPPORTED;
        }
        if (machineReceiveTank == null && receiveState == ROLE_UNKNOWN && attemptedReceive) {
            receiveState = ROLE_UNSUPPORTED;
        }
    }

    private void updateStorageEnergyType() {
        boolean send = legacySendTile != null || machineSendTank != null;
        boolean receive = legacyReceiveTile != null || machineReceiveTank != null;
        if (send) {
            energyType = receive ? EnergyType.STORAGE : EnergyType.SEND;
        } else if (receive) {
            energyType = EnergyType.RECEIVE;
        } else {
            energyType = EnergyType.INVALID;
        }
    }

    private void prepareLegacyPoweredTile(ILegacyPoweredTile poweredTile) {
        bindLegacyPoweredTile(poweredTile);
        updateStorageEnergyType();
        prepared = true;
    }

    private void prepareCapabilityMachine(AbstractCapabilityMachineEntity machineEntity) {
        bindCapabilityMachine(machineEntity);
        updateStorageEnergyType();
        prepared = true;
    }

    @Override
    public void asyncInit(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (tileEntity instanceof TileCapBank) {
            return;
        }
        if (tileEntity instanceof ILegacyPoweredTile poweredTile) {
            prepareLegacyPoweredTile(poweredTile);
            return;
        }
        if (tileEntity instanceof AbstractCapabilityMachineEntity machineEntity) {
            prepareCapabilityMachine(machineEntity);
            return;
        }
        energyType = EnergyType.INVALID;
        prepared = true;
    }

    @Override
    public boolean shouldRunAsyncInit(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        return tileEntity instanceof ILegacyPoweredTile || tileEntity instanceof AbstractCapabilityMachineEntity;
    }

    @Override
    public void init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (initialized) {
            return;
        }
        initialized = true;
        if (prepared) {
            return;
        }
        if (tileEntity instanceof TileCapBank tcapBank) {
            capBank = tcapBank;
            ICapBankNetwork activeNetwork = capBank.getNetwork();
            storage = activeNetwork != null ? activeNetwork : resolveStorage(capBank);
            if (storage instanceof ICapBankNetwork networkStorage) {
                capBankNetwork = networkStorage;
            }
            energyType = EnergyType.STORAGE;
            prepared = true;
            return;
        }
        if (tileEntity instanceof ILegacyPoweredTile poweredTile) {
            prepareLegacyPoweredTile(poweredTile);
            return;
        }
        if (tileEntity instanceof AbstractCapabilityMachineEntity machineEntity) {
            prepareCapabilityMachine(machineEntity);
            return;
        }
        energyType = EnergyType.INVALID;
        prepared = true;
    }

    private void refreshNetworkStorage() {
        if (capBank == null) {
            return;
        }
        ICapBankNetwork activeNetwork = capBank.getNetwork();
        if (activeNetwork != null) {
            if (activeNetwork == capBankNetwork) {
                return;
            }
            capBankNetwork = activeNetwork;
            storage = activeNetwork;
            return;
        }
        if (capBankNetwork != null) {
            capBankNetwork = null;
            storage = resolveStorage(capBank);
        }
    }

    @Nullable
    public ICapBankNetwork getCapBankNetwork() {
        return capBankNetwork;
    }

    @Override
    public void init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        energyType = EnergyType.INVALID;
    }

    @Override
    public void clear() {
        storage = null;
        capBankNetwork = null;
        capBank = null;
        legacySendTile = null;
        legacyReceiveTile = null;
        machineSendTank = null;
        machineReceiveTank = null;
        sendFacing = null;
        receiveFacing = null;
        energyType = null;
        initialized = false;
        prepared = false;
        sendState = ROLE_UNKNOWN;
        receiveState = ROLE_UNKNOWN;
    }

    @Nullable
    private EnumFacing findInputFace() {
        if (capBank == null) {
            return null;
        }
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (capBank.isInputEnabled(facing)) {
                return facing;
            }
        }
        return null;
    }

    private boolean canInput() {
        if (capBankNetwork != null) {
            return capBankNetwork.isInputEnabled();
        }
        return findInputFace() != null;
    }

    private boolean canOutput() {
        if (capBankNetwork != null) {
            return capBankNetwork.isOutputEnabled();
        }
        if (capBank == null) {
            return false;
        }
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (capBank.isOutputEnabled(facing)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        refreshNetworkStorage();
        if (legacyReceiveTile != null && receiveFacing != null) {
            int received = legacyReceiveTile.receiveEnergy(receiveFacing, clampPositive(maxReceive.asLongClamped(), legacyReceiveTile.getMaxEnergyStored() - legacyReceiveTile.getEnergyStored(), legacyReceiveTile.getMaxEnergyRecieved(receiveFacing)), false);
            if (received == 0 && maxReceive.isPositive()) {
                receiveState = ROLE_UNKNOWN;
            }
            return received > 0 ? EnergyAmount.obtain(received) : EnergyAmounts.ZERO;
        }
        if (machineReceiveTank != null) {
            int received = receiveEnergyDirect(machineReceiveTank, clampPositive(maxReceive.asLongClamped()), false);
            if (received == 0 && maxReceive.isPositive()) {
                receiveState = ROLE_UNKNOWN;
            }
            return received > 0 ? EnergyAmount.obtain(received) : EnergyAmounts.ZERO;
        }
        if (storage != null && canInput()) {
            long before = storage.getEnergyStoredL();
            int requested = clampPositive(maxReceive.asLongClamped(), storage.getMaxEnergyStoredL() - before, storage.getMaxInput());
            if (requested <= 0) {
                return EnergyAmounts.ZERO;
            }
            int accepted;
            if (capBankNetwork != null) {
                accepted = capBankNetwork.receiveEnergy(requested, false);
            } else {
                EnumFacing inputFace = findInputFace();
                accepted = inputFace == null || capBank == null ? 0 : capBank.receiveEnergy(inputFace, requested, false);
            }
            return accepted > 0 ? EnergyAmount.obtain(accepted) : EnergyAmounts.ZERO;
        }
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        refreshNetworkStorage();
        if (legacySendTile != null) {
            int maxOutput = legacySendTile instanceof AbstractGeneratorEntity generator ? generator.getMaxEnergySent() : legacySendTile.getEnergyStored();
            int extracted = extractEnergyDirect(legacySendTile, clampPositive(maxExtract.asLongClamped()), maxOutput, false);
            if (extracted == 0 && maxExtract.isPositive()) {
                sendState = ROLE_UNKNOWN;
            }
            return extracted > 0 ? EnergyAmount.obtain(extracted) : EnergyAmounts.ZERO;
        }
        if (machineSendTank != null) {
            int maxOutput = machineSendTank.getMaxUsage();
            int extracted = extractEnergyDirect(machineSendTank, clampPositive(maxExtract.asLongClamped()), maxOutput, false);
            if (extracted == 0 && maxExtract.isPositive()) {
                sendState = ROLE_UNKNOWN;
            }
            return extracted > 0 ? EnergyAmount.obtain(extracted) : EnergyAmounts.ZERO;
        }
        if (storage != null && canOutput()) {
            long before = storage.getEnergyStoredL();
            int requested = clampPositive(maxExtract.asLongClamped(), before, storage.getMaxOutput());
            if (requested <= 0) {
                return EnergyAmounts.ZERO;
            }
            storage.addEnergy(-requested);
            long extracted = storage.isCreative() ? requested : Math.max(0L, before - storage.getEnergyStoredL());
            return extracted > 0L ? EnergyAmount.obtain(extracted) : EnergyAmounts.ZERO;
        }
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        refreshNetworkStorage();
        if (legacySendTile != null) {
            int maxOutput = legacySendTile instanceof AbstractGeneratorEntity generator ? generator.getMaxEnergySent() : legacySendTile.getEnergyStored();
            return EnergyAmount.obtain(extractEnergyDirect(legacySendTile, Integer.MAX_VALUE, maxOutput, true));
        }
        if (machineSendTank != null) {
            return EnergyAmount.obtain(extractEnergyDirect(machineSendTank, Integer.MAX_VALUE, machineSendTank.getMaxUsage(), true));
        }
        if (storage != null && canOutput()) {
            return EnergyAmount.obtain(Math.max(0L, Math.min(storage.getEnergyStoredL(), storage.getMaxOutput())));
        }
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        refreshNetworkStorage();
        if (legacyReceiveTile != null && receiveFacing != null) {
            return EnergyAmount.obtain(legacyReceiveTile.receiveEnergy(receiveFacing, clampPositive(Integer.MAX_VALUE, legacyReceiveTile.getMaxEnergyStored() - legacyReceiveTile.getEnergyStored(), legacyReceiveTile.getMaxEnergyRecieved(receiveFacing)), true));
        }
        if (machineReceiveTank != null) {
            return EnergyAmount.obtain(receiveEnergyDirect(machineReceiveTank, Integer.MAX_VALUE, true));
        }
        if (storage != null && canInput()) {
            long room = Math.max(0L, storage.getMaxEnergyStoredL() - storage.getEnergyStoredL());
            return EnergyAmount.obtain(Math.min(room, storage.getMaxInput()));
        }
        return EnergyAmounts.ZERO;
    }

    @Override
    public boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return canExtractValue(hubMetadata).isPositive();
    }

    @Override
    public boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata) {
        return canReceiveValue(hubMetadata).isPositive();
    }

    @Override
    public EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata) {
        return energyType == null ? EnergyType.INVALID : energyType;
    }
}
