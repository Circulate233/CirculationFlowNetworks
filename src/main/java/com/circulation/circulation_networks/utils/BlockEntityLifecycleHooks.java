package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.EnergyTypeOverrideManager;
import com.circulation.circulation_networks.manager.HubChannelManager;
import com.circulation.circulation_networks.manager.MachineNodeBlockEntityManager;
import com.circulation.circulation_networks.manager.NetworkManager;
//~ mc_imports
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
//? if <1.21 {
import net.minecraftforge.common.MinecraftForge;
//?} else {
/*import net.neoforged.neoforge.common.NeoForge;
 *///?}
//? if <1.20 {
import net.minecraftforge.fml.common.eventhandler.EventBus;
//?} else if < 1.21 {
/*import net.minecraftforge.eventbus.api.IEventBus;
 *///?} else {
/*import net.neoforged.bus.api.IEventBus;
 *///?}

public final class BlockEntityLifecycleHooks {

    //~ if >=1.20 'EventBus ' -> 'IEventBus ' {
    private static final EventBus EVENT_BUS;
    //~}

    private static boolean validateLock;
    private static boolean invalidateLock;

    static {
        //? if <1.21 {
        EVENT_BUS = MinecraftForge.EVENT_BUS;
        //?} else {
        /*EVENT_BUS = NeoForge.EVENT_BUS;
         *///?}
    }

    private BlockEntityLifecycleHooks() {
    }

    public static void dispatchValidate(BlockEntityLifeCycleEvent.Validate event) {
        MachineNodeBlockEntityManager.INSTANCE.onBlockEntityValidate(event);
        NetworkManager.INSTANCE.onBlockEntityValidate(event);
        EnergyMachineManager.INSTANCE.onBlockEntityValidate(event);
        HubChannelManager.INSTANCE.onBlockEntityValidate(event);
    }

    public static void dispatchInvalidate(BlockEntityLifeCycleEvent.Invalidate event) {
        MachineNodeBlockEntityManager.INSTANCE.onBlockEntityInvalidate(event);
        NetworkManager.INSTANCE.onBlockEntityInvalidate(event);
        EnergyMachineManager.INSTANCE.onBlockEntityInvalidate(event);
        HubChannelManager.INSTANCE.onBlockEntityInvalidate(event);
        var overrideManager = EnergyTypeOverrideManager.get();
        if (overrideManager != null) {
            overrideManager.onBlockEntityInvalidate(event);
        }
    }

    //~ if >=1.20 'World ' -> 'Level ' {
    //~ if >=1.20 '(TileEntity ' -> '(BlockEntity ' {
    //~ if >=1.20 ' TileEntity ' -> ' BlockEntity ' {
    public static void postValidate(World world, BlockPos pos, TileEntity blockEntity) {
        if (validateLock) {
            return;
        }
        validateLock = true;
        try {
            if (blockEntity instanceof INodeBlockEntity<?> nodeBlockEntity) {
                nodeBlockEntity.nodeValidate();
            }
            BlockEntityLifeCycleEvent.Validate event = new BlockEntityLifeCycleEvent.Validate(world, pos, blockEntity);
            dispatchValidate(event);
            EVENT_BUS.post(event);
        } finally {
            validateLock = false;
        }
    }

    public static void postInvalidate(World world, BlockPos pos, TileEntity blockEntity) {
        if (invalidateLock) {
            return;
        }
        invalidateLock = true;
        try {
            if (blockEntity instanceof INodeBlockEntity<?> nodeBlockEntity) {
                nodeBlockEntity.nodeInvalidate();
            }
            BlockEntityLifeCycleEvent.Invalidate event = new BlockEntityLifeCycleEvent.Invalidate(world, pos, blockEntity);
            dispatchInvalidate(event);
            EVENT_BUS.post(event);
        } finally {
            invalidateLock = false;
        }
    }
    //~}
    //~}
    //~}
}
