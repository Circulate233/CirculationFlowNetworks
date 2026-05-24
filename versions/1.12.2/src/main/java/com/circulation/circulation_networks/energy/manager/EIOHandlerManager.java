package com.circulation.circulation_networks.energy.manager;

import com.circulation.circulation_networks.api.IEnergyHandler;
import com.circulation.circulation_networks.api.IEnergyHandlerManager;
import com.circulation.circulation_networks.energy.handler.EIOHandler;
import crazypants.enderio.base.machine.base.te.AbstractCapabilityMachineEntity;
import crazypants.enderio.base.machine.modes.IoMode;
import crazypants.enderio.base.power.IEnergyTank;
import crazypants.enderio.base.power.forge.tile.ILegacyPoweredTile;
import crazypants.enderio.powertools.machine.capbank.TileCapBank;
import crazypants.enderio.powertools.machine.capbank.network.ICapBankNetwork;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

public final class EIOHandlerManager implements IEnergyHandlerManager {

    public static final EIOHandlerManager INSTANCE = new EIOHandlerManager();

    private final Reference2ObjectMap<ICapBankNetwork, EIOHandler> networkCache = new Reference2ObjectOpenHashMap<>();

    public void clearTickCache() {
        networkCache.clear();
    }

    private EIOHandlerManager() {
    }

    private IEnergyHandler claimCapBankHandler(EIOHandler handler) {
        ICapBankNetwork network = handler.getCapBankNetwork();
        if (network == null) {
            return handler;
        }
        EIOHandler claimed = networkCache.get(network);
        if (claimed != null) {
            return claimed;
        }
        networkCache.put(network, handler);
        return handler;
    }

    @Override
    public IEnergyHandler resolveMappedHandler(IEnergyHandler handler, IEnergyHandler.HandlerResolveContext context) {
        if (handler instanceof EIOHandler eioHandler && context.tileEntity() instanceof TileCapBank) {
            return claimCapBankHandler(eioHandler);
        }
        return handler.resolveMappedHandler(context);
    }

    @Override
    public boolean isAvailable(TileEntity tileEntity) {
        if (tileEntity instanceof TileCapBank) {
            return true;
        }
        if (tileEntity instanceof ILegacyPoweredTile poweredTile) {
            for (EnumFacing facing : EnumFacing.VALUES) {
                if (poweredTile.canConnectEnergy(facing)) {
                    return true;
                }
            }
            return poweredTile.getMaxEnergyStored() > 0;
        }
        if (tileEntity instanceof AbstractCapabilityMachineEntity machineEntity) {
            IEnergyTank tank = machineEntity.getEnergy();
            if (tank == null || tank.getMaxEnergyStored() <= 0) {
                return false;
            }
            for (EnumFacing facing : EnumFacing.VALUES) {
                IoMode mode = machineEntity.getIoMode(facing);
                if (mode != null && mode.canInputOrOutput()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isAvailable(ItemStack itemStack) {
        return false;
    }

    @Override
    public Class<EIOHandler> getEnergyHandlerClass() {
        return EIOHandler.class;
    }

    @Override
    public int getPriority() {
        return 12;
    }

    @Override
    public IEnergyHandler newBlockEntityInstance() {
        return new EIOHandler();
    }

    @Override
    public IEnergyHandler newItemInstance() {
        return new EIOHandler();
    }

    @Override
    public String getUnit() {
        return "RF";
    }
}
