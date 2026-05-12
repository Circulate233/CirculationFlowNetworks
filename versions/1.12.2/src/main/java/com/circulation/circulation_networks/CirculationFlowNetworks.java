package com.circulation.circulation_networks;

import com.circulation.circulation_networks.manager.ChargingManager;
import com.circulation.circulation_networks.manager.DatPersistenceScheduler;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.EnergyTypeOverrideManager;
import com.circulation.circulation_networks.manager.HubChannelManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.proxy.CommonProxy;
import com.circulation.circulation_networks.registry.CFNBlocks;
import com.circulation.circulation_networks.utils.Packet;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
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

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION,
    dependencies = "required:mixinbooter@[8.0,);")
public class CirculationFlowNetworks {

    public static final String MOD_ID = Tags.MOD_ID;
    public static final String CLIENT_PROXY = "com.circulation.circulation_networks.proxy.ClientProxy";
    public static final String COMMON_PROXY = "com.circulation.circulation_networks.proxy.CommonProxy";
    public static final SimpleNetworkWrapper NET_CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID);
    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);
    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(CirculationFlowNetworks.MOD_ID) {
        @Override
        public @NotNull ItemStack createIcon() {
            return CFNBlocks.blockHub == null ? ItemStack.EMPTY : new ItemStack(CFNBlocks.blockHub);
        }
    };
    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = COMMON_PROXY)
    public static CommonProxy proxy = null;
    @Mod.Instance(MOD_ID)
    public static CirculationFlowNetworks instance;
    public static MinecraftServer server;

    public static void openGui(EntityPlayer player, World world, int x, int y, int z) {
        openGui(0, player, world, x, y, z);
    }

    public static void openGui(int guiId, EntityPlayer player, World world, int x, int y, int z) {
        player.openGui(CirculationFlowNetworks.instance, guiId, world, x, y, z);
    }

    public static <T extends Packet<T>> void sendToPlayer(T packet, EntityPlayerMP player) {
        NET_CHANNEL.sendTo(packet, player);
    }

    public static <T extends Packet<T>> void sendToServer(T packet) {
        NET_CHANNEL.sendToServer(packet);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
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
        NetworkManager.INSTANCE.saveGrid();
        PocketNodeManager.INSTANCE.save();
        HubChannelManager.INSTANCE.save();
        EnergyTypeOverrideManager.save();
        NetworkManager.INSTANCE.onServerStop();
        PocketNodeManager.INSTANCE.onServerStop();
        EnergyMachineManager.INSTANCE.onServerStop();
        EnergyTypeOverrideManager.onServerStop();
        ChargingManager.INSTANCE.onServerStop();
        HubChannelManager.INSTANCE.onServerStop();
        DatPersistenceScheduler.INSTANCE.reset();
        server = null;
    }

}
