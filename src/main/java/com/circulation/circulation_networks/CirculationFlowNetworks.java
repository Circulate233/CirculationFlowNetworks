package com.circulation.circulation_networks;

import com.circulation.circulation_networks.manager.ChargingManager;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.proxy.CommonProxy;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class CirculationFlowNetworks {

    public static final String MOD_ID = Tags.MOD_ID;
    public static final String CLIENT_PROXY = "com.circulation.circulation_networks.proxy.ClientProxy";
    public static final String COMMON_PROXY = "com.circulation.circulation_networks.proxy.CommonProxy";

    public static final SimpleNetworkWrapper NET_CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID);

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);
    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(CirculationFlowNetworks.MOD_ID) {
        @Override
        public @NotNull ItemStack createIcon() {
            return ItemStack.EMPTY;
        }
    };
    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = COMMON_PROXY)
    public static CommonProxy proxy = null;
    @Mod.Instance(MOD_ID)
    public static CirculationFlowNetworks instance;
    public static MinecraftServer server;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        server = event.getServer();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        server = null;
        NetworkManager.INSTANCE.onServerStop();
        EnergyMachineManager.INSTANCE.onServerStop();
        ChargingManager.INSTANCE.onServerStop();
    }

}
