package com.circulation.circulation_networks.utils;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.events.AddNodeEvent;
import com.circulation.circulation_networks.events.RemoveNodeEvent;
//~ mc_imports
import net.minecraft.tileentity.TileEntity;
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

import org.jetbrains.annotations.Nullable;

public final class NodeEventHooks {

    //~ if >=1.20 'EventBus ' -> 'IEventBus ' {
    private static final EventBus EVENT_BUS;
    //~}

    static {
        //? if <1.21 {
        EVENT_BUS = MinecraftForge.EVENT_BUS;
        //?} else {
        /*EVENT_BUS = NeoForge.EVENT_BUS;
         *///?}
    }

    private NodeEventHooks() {
    }

    public static void postRemoveNodePre(INode node) {
        EVENT_BUS.post(new RemoveNodeEvent.Pre(node));
    }

    public static void postRemoveNodePost(INode node) {
        EVENT_BUS.post(new RemoveNodeEvent.Post(node));
    }

    //~ if >=1.20 'TileEntity ' -> 'BlockEntity ' {
    //~ if >=1.20 ' TileEntity ' -> ' BlockEntity ' {
    public static boolean postAddNodePre(INode node, @Nullable TileEntity blockEntity) {
        //? if <1.21 {
        return EVENT_BUS.post(new AddNodeEvent.Pre(node, blockEntity));
        //?} else {
        /*return EVENT_BUS.post(new AddNodeEvent.Pre(node, blockEntity)).isCanceled();
         *///?}
    }

    public static void postAddNodePost(INode node, @Nullable TileEntity blockEntity) {
        EVENT_BUS.post(new AddNodeEvent.Post(node, blockEntity));
    }
    //~}
    //~}
}
