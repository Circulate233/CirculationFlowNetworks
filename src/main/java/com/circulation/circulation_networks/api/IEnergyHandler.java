package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.proxy.CommonProxy;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import java.util.Map;
import java.util.Queue;

public interface IEnergyHandler {

    Map<Class<? extends IEnergyHandler>, Queue<IEnergyHandler>> POOL = new Reference2ObjectOpenHashMap<>();

    static IEnergyHandler release(TileEntity tileEntity) {
        if (tileEntity.hasCapability(CommonProxy.ceHandlerCapability, null))
            return tileEntity.getCapability(CommonProxy.ceHandlerCapability, null);
        var m = RegistryEnergyHandler.getEnergyManager(tileEntity);
        if (m == null) return null;
        var q = POOL.get(m.getEnergyHandlerClass());
        if (q.isEmpty()) return m.newInstance(tileEntity);
        var t = q.poll();
        return t.init(tileEntity);
    }

    static IEnergyHandler release(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var m = RegistryEnergyHandler.getEnergyManager(stack);
        if (m == null) return null;
        var q = POOL.get(m.getEnergyHandlerClass());
        if (q.isEmpty()) return m.newInstance(stack);
        var t = q.poll();
        return t.init(stack);
    }

    IEnergyHandler init(TileEntity tileEntity);

    IEnergyHandler init(ItemStack itemStack);

    void clear();

    long receiveEnergy(long maxReceive);

    long extractEnergy(long maxExtract);

    long canExtractValue();

    long canReceiveValue();

    boolean canExtract();

    boolean canReceive();

    default void recycle() {
        this.clear();
        var queue = POOL.get(this.getClass());
        if (queue.size() < EnergyMachineManager.INSTANCE.getMachineGridMap().size()) {
            queue.add(this);
        }
    }

    default EnergyType getType() {
        boolean receive = canReceive();
        if (canExtract()) {
            return receive ? EnergyType.STORAGE : EnergyType.SEND;
        } else if (receive) {
            return EnergyType.RECEIVE;
        }
        return EnergyType.INVALID;
    }

    enum EnergyType {
        SEND,
        RECEIVE,
        STORAGE,
        INVALID
    }
}