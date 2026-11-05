package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.EnergyTypeOverrideManager;
import com.circulation.circulation_networks.manager.HubChannelManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class BlockEntityLifecycleHooks {

    private static final IEventBus EVENT_BUS = NeoForge.EVENT_BUS;
    private static final ThreadLocal<Set<Object>> validating = ThreadLocal.withInitial(
        () -> Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final ThreadLocal<Set<Object>> invalidating = ThreadLocal.withInitial(
        () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private BlockEntityLifecycleHooks() {
    }

    public static void onValidate(Level world, BlockPos pos, BlockEntity blockEntity) {
        var active = validating.get();
        if (!active.add(blockEntity)) {
            return;
        }
        try {
            if (blockEntity instanceof INodeBlockEntity nodeBlockEntity) {
                nodeBlockEntity.nodeValidate();
            }
            BlockEntityLifeCycleEvent.Validate event = new BlockEntityLifeCycleEvent.Validate(world, pos, blockEntity);
            NetworkManager.INSTANCE.onBlockEntityValidate(event);
            EnergyMachineManager.INSTANCE.onBlockEntityValidate(event);
            HubChannelManager.INSTANCE.onBlockEntityValidate(event);
            EVENT_BUS.post(event);
        } finally {
            active.remove(blockEntity);
            if (active.isEmpty()) validating.remove();
        }
    }

    public static void onInvalidate(Level world, BlockPos pos, BlockEntity blockEntity) {
        var active = invalidating.get();
        if (!active.add(blockEntity)) {
            return;
        }
        try {
            if (blockEntity instanceof INodeBlockEntity nodeBlockEntity) {
                nodeBlockEntity.nodeInvalidate();
            }
            BlockEntityLifeCycleEvent.Invalidate event = new BlockEntityLifeCycleEvent.Invalidate(world, pos, blockEntity);
            NetworkManager.INSTANCE.onBlockEntityInvalidate(event);
            EnergyMachineManager.INSTANCE.onBlockEntityInvalidate(event);
            HubChannelManager.INSTANCE.onBlockEntityInvalidate(event);
            EnergyTypeOverrideManager overrideManager = EnergyTypeOverrideManager.get();
            if (overrideManager != null) {
                overrideManager.onBlockEntityInvalidate(event);
            }
            EVENT_BUS.post(event);
        } finally {
            active.remove(blockEntity);
            if (active.isEmpty()) invalidating.remove();
        }
    }
}
