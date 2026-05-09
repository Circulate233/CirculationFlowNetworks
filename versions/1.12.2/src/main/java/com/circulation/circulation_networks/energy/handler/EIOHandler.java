package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import crazypants.enderio.base.power.IPowerStorage;
import crazypants.enderio.powertools.machine.capbank.TileCapBank;
import crazypants.enderio.powertools.machine.capbank.network.ICapBankNetwork;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

public class EIOHandler implements IEnergyHandler {

    @Nullable
    private IPowerStorage storage;
    @Nullable
    private ICapBankNetwork capBankNetwork;
    @Nullable
    private TileCapBank capBank;
    @Nullable
    private EnergyType energyType;

    private static int clampPositive(long requested, long... limits) {
        long clamped = Math.max(0L, requested);
        for (long limit : limits) {
            clamped = Math.min(clamped, Math.max(0L, limit));
        }
        if (clamped <= 0L) {
            return 0;
        }
        return clamped >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) clamped;
    }

    private static IPowerStorage resolveStorage(TileCapBank capBank) {
        IPowerStorage controller = capBank.getController();
        return controller != null ? controller : capBank;
    }

    @Override
    public IEnergyHandler init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (tileEntity instanceof TileCapBank tcapBank) {
            capBank = tcapBank;
            ICapBankNetwork activeNetwork = capBank.getNetwork();
            storage = activeNetwork != null ? activeNetwork : resolveStorage(capBank);
            if (storage instanceof ICapBankNetwork networkStorage) {
                capBankNetwork = networkStorage;
            }
            energyType = EnergyType.STORAGE;
            return this;
        }
        energyType = EnergyType.INVALID;
        return this;
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

    @Override
    public IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata) {
        energyType = EnergyType.INVALID;
        return this;
    }

    @Override
    public void clear() {
        storage = null;
        capBankNetwork = null;
        capBank = null;
        energyType = null;
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
        if (storage != null && canOutput()) {
            return EnergyAmount.obtain(Math.max(0L, Math.min(storage.getEnergyStoredL(), storage.getMaxOutput())));
        }
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        refreshNetworkStorage();
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
