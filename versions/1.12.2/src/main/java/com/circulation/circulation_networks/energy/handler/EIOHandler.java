package com.circulation.circulation_networks.energy.handler;

import com.circulation.circulation_networks.api.EnergyAmount;
import com.circulation.circulation_networks.api.EnergyAmounts;
import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
import crazypants.enderio.base.power.IPowerStorage;
import crazypants.enderio.base.power.forge.tile.ILegacyPoweredTile;
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
    private ILegacyPoweredTile.Generator generator;
    @Nullable
    private ILegacyPoweredTile.Receiver receiver;
    @Nullable
    private EnumFacing receiveFacing;
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

    @Nullable
    private static EnumFacing findConnectedFacing(ILegacyPoweredTile tile) {
        for (int i = 0; i < EnumFacing.VALUES.length; i++) {
            EnumFacing facing = EnumFacing.VALUES[i];
            if (tile.canConnectEnergy(facing)) {
                return facing;
            }
        }
        return null;
    }

    @Override
    public IEnergyHandler init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (tileEntity instanceof IPowerStorage powerStorage) {
            IPowerStorage controller = powerStorage.getController();
            storage = controller != null ? controller : powerStorage;
            if (storage instanceof ICapBankNetwork network) {
                capBankNetwork = network;
            }
            energyType = EnergyType.STORAGE;
            return this;
        }
        if (tileEntity instanceof ILegacyPoweredTile.Generator legacyGenerator) {
            generator = legacyGenerator;
            energyType = EnergyType.SEND;
            return this;
        }
        if (tileEntity instanceof ILegacyPoweredTile.Receiver legacyReceiver) {
            receiver = legacyReceiver;
            receiveFacing = findConnectedFacing(legacyReceiver);
            energyType = EnergyType.RECEIVE;
            return this;
        }
        energyType = EnergyType.INVALID;
        return this;
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
        generator = null;
        receiver = null;
        receiveFacing = null;
        energyType = null;
    }

    @Override
    public EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata) {
        if (storage != null) {
            long before = storage.getEnergyStoredL();
            int requested = clampPositive(maxReceive.asLongClamped(), storage.getMaxEnergyStoredL() - before, storage.getMaxInput());
            if (requested <= 0) {
                return EnergyAmounts.ZERO;
            }
            if (capBankNetwork != null) {
                capBankNetwork.receiveEnergy(requested, false);
            } else {
                storage.addEnergy(requested);
            }
            long accepted = Math.max(0L, storage.getEnergyStoredL() - before);
            return accepted > 0L ? EnergyAmount.obtain(accepted) : EnergyAmounts.ZERO;
        }
        if (receiver != null && receiveFacing != null) {
            long room = Math.max(0L, receiver.getMaxEnergyStored() - receiver.getEnergyStored());
            int requested = clampPositive(maxReceive.asLongClamped(), room, receiver.getMaxEnergyRecieved(receiveFacing));
            if (requested <= 0) {
                return EnergyAmounts.ZERO;
            }
            int accepted = receiver.receiveEnergy(receiveFacing, requested, false);
            return accepted > 0 ? EnergyAmount.obtain(accepted) : EnergyAmounts.ZERO;
        }
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata) {
        if (storage != null) {
            long before = storage.getEnergyStoredL();
            int requested = clampPositive(maxExtract.asLongClamped(), before, storage.getMaxOutput());
            if (requested <= 0) {
                return EnergyAmounts.ZERO;
            }
            storage.addEnergy(-requested);
            long extracted = Math.max(0L, before - storage.getEnergyStoredL());
            return extracted > 0L ? EnergyAmount.obtain(extracted) : EnergyAmounts.ZERO;
        }
        if (generator != null) {
            int before = generator.getEnergyStored();
            int requested = clampPositive(maxExtract.asLongClamped(), before, before);
            if (requested <= 0) {
                return EnergyAmounts.ZERO;
            }
            generator.setEnergyStored(before - requested);
            return EnergyAmount.obtain(Math.max(0, before - generator.getEnergyStored()));
        }
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (storage != null) {
            return EnergyAmount.obtain(Math.max(0L, Math.min(storage.getEnergyStoredL(), storage.getMaxOutput())));
        }
        if (generator != null) {
            return EnergyAmount.obtain(Math.max(0, generator.getEnergyStored()));
        }
        return EnergyAmounts.ZERO;
    }

    @Override
    public EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata) {
        if (storage != null) {
            long room = Math.max(0L, storage.getMaxEnergyStoredL() - storage.getEnergyStoredL());
            return EnergyAmount.obtain(Math.min(room, storage.getMaxInput()));
        }
        if (receiver != null && receiveFacing != null) {
            long room = Math.max(0L, receiver.getMaxEnergyStored() - receiver.getEnergyStored());
            return EnergyAmount.obtain(Math.min(room, receiver.getMaxEnergyRecieved(receiveFacing)));
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
