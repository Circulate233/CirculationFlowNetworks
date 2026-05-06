package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.utils.ObjectPool;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import com.circulation.circulation_networks.network.nodes.HubNode;
//~ mc_imports
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.Nullable;

public interface IEnergyHandler {

    int MAX_POOL_SIZE = 4096;
    Reference2ObjectMap<Class<? extends IEnergyHandler>, ObjectPool<IEnergyHandler>> POOL = new Reference2ObjectOpenHashMap<>();

    //~ if >=1.20 '(TileEntity ' -> '(BlockEntity ' {
    static @Nullable IEnergyHandler release(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
        if (tileEntity instanceof IMachineNodeBlockEntity mbe) return mbe.getEnergyHandler();
        var m = RegistryEnergyHandler.getEnergyManager(tileEntity);
        if (m == null) return null;
        return release(tileEntity, m, hubMetadata);
    }

    static @Nullable IEnergyHandler release(TileEntity tileEntity,
                                                                     IEnergyHandlerManager manager,
                                                                     @Nullable HubNode.HubMetadata hubMetadata) {
        if (tileEntity instanceof IMachineNodeBlockEntity mbe) return mbe.getEnergyHandler();
        var q = POOL.get(manager.getEnergyHandlerClass());
        var t = q == null ? manager.newBlockEntityInstance() : q.obtain();
        return t.init(tileEntity, hubMetadata);
    }

    static @Nullable IEnergyHandler release(ItemStack stack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (stack == null || stack.isEmpty()) return null;
        var m = RegistryEnergyHandler.getEnergyManager(stack);
        if (m == null) return null;
        var q = POOL.get(m.getEnergyHandlerClass());
        var t = q == null ? m.newItemInstance() : q.obtain();
        return t.init(stack, hubMetadata);
    }

    IEnergyHandler init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata);
    //~}

    IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata);

    void clear();

    EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata);

    EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata);

    EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata);

    EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata);

    boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata);

    boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata);

    default void recycle() {
        var queue = POOL.get(this.getClass());
        if (queue != null) {
            queue.recycle(this);
        } else {
            this.clear();
        }
    }

    EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata);

    enum EnergyType {
        SEND,
        RECEIVE,
        STORAGE,
        INVALID
    }
}
