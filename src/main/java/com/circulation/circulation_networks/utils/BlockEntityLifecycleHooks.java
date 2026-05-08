package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.EnergyTypeOverrideManager;
import com.circulation.circulation_networks.manager.HubChannelManager;
import com.circulation.circulation_networks.manager.MachineTickManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

public final class BlockEntityLifecycleHooks {

    private static final IEventBus EVENT_BUS = NeoForge.EVENT_BUS;
    private static boolean validateLock;
    private static boolean invalidateLock;

    private BlockEntityLifecycleHooks() {
    }

    public static void onValidate(Level world, BlockPos pos, BlockEntity blockEntity) {
        if (validateLock) {
            return;
        }
        validateLock = true;
        try {
            if (blockEntity instanceof INodeBlockEntity nodeBlockEntity) {
                nodeBlockEntity.nodeValidate();
            }
            BlockEntityLifeCycleEvent.Validate event = new BlockEntityLifeCycleEvent.Validate(world, pos, blockEntity);
            MachineTickManager.INSTANCE.onBlockEntityValidate(event);
            NetworkManager.INSTANCE.onBlockEntityValidate(event);
            EnergyMachineManager.INSTANCE.onBlockEntityValidate(event);
            HubChannelManager.INSTANCE.onBlockEntityValidate(event);
            EVENT_BUS.post(event);
        } finally {
            validateLock = false;
        }
    }

    public static void onInvalidate(Level world, BlockPos pos, BlockEntity blockEntity) {
        if (invalidateLock) {
            return;
        }
        invalidateLock = true;
        try {
            if (blockEntity instanceof INodeBlockEntity nodeBlockEntity) {
                nodeBlockEntity.nodeInvalidate();
            }
            BlockEntityLifeCycleEvent.Invalidate event = new BlockEntityLifeCycleEvent.Invalidate(world, pos, blockEntity);
            MachineTickManager.INSTANCE.onBlockEntityInvalidate(event);
            NetworkManager.INSTANCE.onBlockEntityInvalidate(event);
            EnergyMachineManager.INSTANCE.onBlockEntityInvalidate(event);
            HubChannelManager.INSTANCE.onBlockEntityInvalidate(event);
            EnergyTypeOverrideManager overrideManager = EnergyTypeOverrideManager.get();
            if (overrideManager != null) {
                overrideManager.onBlockEntityInvalidate(event);
            }
            EVENT_BUS.post(event);
        } finally {
            invalidateLock = false;
        }
    }
}
