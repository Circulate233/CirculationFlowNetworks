package com.circulation.circulation_networks.proxy;

import com.circulation.circulation_networks.handlers.InspectionToolHandler;
import com.circulation.circulation_networks.handlers.NodeNetworkRenderingHandler;
import com.circulation.circulation_networks.handlers.SpoceRenderingHandler;
import com.circulation.circulation_networks.registry.RegistryBlocks;
import com.circulation.circulation_networks.registry.RegistryItems;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    public void preInit() {
        super.preInit();
    }

    public void init() {
        super.init();
    }

    public void postInit() {
        super.postInit();
        MinecraftForge.EVENT_BUS.register(SpoceRenderingHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NodeNetworkRenderingHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(InspectionToolHandler.INSTANCE);
    }

    @SubscribeEvent
    public void onModelRegister(ModelRegistryEvent event) {
        RegistryBlocks.registerBlockModels();
        RegistryItems.registerItemModels();
    }
}