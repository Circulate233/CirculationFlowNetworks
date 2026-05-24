package com.circulation.circulation_networks.proxy;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.energy.manager.AE2HandlerManager;
import com.circulation.circulation_networks.energy.manager.EIOHandlerManager;
import com.circulation.circulation_networks.energy.manager.EUHandlerManager;
import com.circulation.circulation_networks.energy.manager.FEHandlerManager;
import com.circulation.circulation_networks.energy.manager.MEKHandlerManager;
import com.circulation.circulation_networks.energy.manager.MMCEHandlerManager;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.handlers.NodePlacementValidationHandler;
import com.circulation.circulation_networks.handlers.NodeRescanHandler;
import com.circulation.circulation_networks.manager.DatPersistenceScheduler;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.EnergyTypeOverrideManager;
import com.circulation.circulation_networks.manager.HubChannelManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.packets.BindHubChannel;
import com.circulation.circulation_networks.packets.CirculationShielderSyncPacket;
import com.circulation.circulation_networks.packets.ConfigOverrideRendering;
import com.circulation.circulation_networks.packets.ContainerProgressBar;
import com.circulation.circulation_networks.packets.ContainerValueConfig;
import com.circulation.circulation_networks.packets.CreateHubChannel;
import com.circulation.circulation_networks.packets.DeleteHubChannel;
import com.circulation.circulation_networks.packets.EnergyWarningRendering;
import com.circulation.circulation_networks.packets.HubPluginSyncData;
import com.circulation.circulation_networks.packets.HubPluginSyncRequest;
import com.circulation.circulation_networks.packets.NodeHudData;
import com.circulation.circulation_networks.packets.NodeHudRequest;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.packets.PocketNodeRendering;
import com.circulation.circulation_networks.packets.RenderingClear;
import com.circulation.circulation_networks.packets.SpoceRendering;
import com.circulation.circulation_networks.packets.ToggleItemFunctionMessage;
import com.circulation.circulation_networks.packets.UpdateHubChannelPermission;
import com.circulation.circulation_networks.packets.UpdateHubChannelSettings;
import com.circulation.circulation_networks.packets.UpdateItemModeMessage;
import com.circulation.circulation_networks.packets.UpdateNodeCustomName;
import com.circulation.circulation_networks.packets.UpdatePlayerChargingMode;
import com.circulation.circulation_networks.registry.RegistryBlocks;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.registry.RegistryItems;
import com.circulation.circulation_networks.tiles.BaseTileEntity;
import com.circulation.circulation_networks.tiles.nodes.BaseNodeTileEntity;
import com.circulation.circulation_networks.utils.BlockEntityLifecycleHooks;
import com.circulation.circulation_networks.utils.HubPlatformServices;
import com.circulation.circulation_networks.utils.HubTeamServices;
import com.circulation.circulation_networks.utils.Packet;
import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.Universe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.circulation.circulation_networks.CirculationFlowNetworks.NET_CHANNEL;

@SuppressWarnings("unused")
public class CommonProxy implements IGuiHandler {

    private static final boolean AE2_LOADED = Loader.isModLoaded("appliedenergistics2");
    private int id = 0;

    public CommonProxy() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void init() {
        HubPlatformServices.INSTANCE = new MyHubPlatformServices();

        if (Loader.isModLoaded("ftblib")) {
            HubTeamServices.INSTANCE = new MyHubTeamServices();
        }

        if (Loader.isModLoaded("enderio")) {
            RegistryEnergyHandler.registerEnergyHandler(new EIOHandlerManager());
        }
        if (AE2_LOADED) {
            RegistryEnergyHandler.registerEnergyHandler(AE2HandlerManager.INSTANCE);
        }
        RegistryEnergyHandler.registerEnergyHandler(new FEHandlerManager());
        try {
            Class.forName("hellfirepvp.modularmachinery.common.block.BlockBus");
            RegistryEnergyHandler.registerEnergyHandler(new MMCEHandlerManager());
        } catch (ClassNotFoundException ignored) {
        }
        if (Loader.isModLoaded("mekanism"))
            RegistryEnergyHandler.registerEnergyHandler(new MEKHandlerManager());
        if (Loader.isModLoaded("ic2")) {
            RegistryEnergyHandler.registerEnergyHandler(EUHandlerManager.INSTANCE);
            MinecraftForge.EVENT_BUS.register(EUHandlerManager.INSTANCE);
        }
        MinecraftForge.EVENT_BUS.register(NodePlacementValidationHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NodeRescanHandler.INSTANCE);
    }

    public void postInit() {
        RegistryEnergyHandler.lock();
    }

    public <T extends Packet<T>> void registerMessage(Class<T> aClass, Side side) {
        NET_CHANNEL.registerMessage(aClass, aClass, id++, side);
    }

    public void preInit(FMLPreInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(CirculationFlowNetworks.instance, this);
        registerMessage(CirculationShielderSyncPacket.class, Side.SERVER);
        registerMessage(UpdateItemModeMessage.class, Side.SERVER);
        registerMessage(ToggleItemFunctionMessage.class, Side.SERVER);
        registerMessage(ContainerProgressBar.class, Side.SERVER);
        registerMessage(UpdatePlayerChargingMode.class, Side.SERVER);
        registerMessage(UpdateNodeCustomName.class, Side.SERVER);
        registerMessage(NodeHudRequest.class, Side.SERVER);
        registerMessage(HubPluginSyncRequest.class, Side.SERVER);
        registerMessage(BindHubChannel.class, Side.SERVER);
        registerMessage(CreateHubChannel.class, Side.SERVER);
        registerMessage(UpdateHubChannelSettings.class, Side.SERVER);
        registerMessage(DeleteHubChannel.class, Side.SERVER);
        registerMessage(UpdateHubChannelPermission.class, Side.SERVER);

        registerMessage(SpoceRendering.class, Side.CLIENT);
        registerMessage(NodeNetworkRendering.class, Side.CLIENT);
        registerMessage(EnergyWarningRendering.class, Side.CLIENT);
        registerMessage(ConfigOverrideRendering.class, Side.CLIENT);
        registerMessage(ContainerProgressBar.class, Side.CLIENT);
        registerMessage(ContainerValueConfig.class, Side.CLIENT);
        registerMessage(RenderingClear.INSTANCE, Side.CLIENT);
        registerMessage(PocketNodeRendering.class, Side.CLIENT);
        registerMessage(NodeHudData.class, Side.CLIENT);
        registerMessage(HubPluginSyncData.class, Side.CLIENT);
    }

    @SubscribeEvent
    public void registryItem(RegistryEvent.Register<Item> event) {
        RegistryItems.registerItems(event);
    }

    @SubscribeEvent
    public void registryBlock(RegistryEvent.Register<Block> event) {
        RegistryBlocks.registerBlocks(event);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            EnergyMachineManager.INSTANCE.onServerTick();
        } else {
            if (AE2_LOADED) {
                AE2HandlerManager.INSTANCE.clearTickCache();
            }
            DatPersistenceScheduler.INSTANCE.onServerTick();
        }
    }

    public <T extends Packet<T>> void registerMessage(T aClass, Side side) {
        //noinspection unchecked
        NET_CHANNEL.registerMessage(aClass, (Class<T>) aClass.getClass(), id++, side);
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getWorld().isRemote) {
            return;
        }
        syncLoadedChunkNodeBlockEntities(event);
        NetworkManager.INSTANCE.validatePendingNodesInChunk(event.getWorld(), event.getChunk().x, event.getChunk().z);
        PocketNodeManager.INSTANCE.onChunkLoad(event.getWorld(), event.getChunk().x, event.getChunk().z);
    }

    private void syncLoadedChunkNodeBlockEntities(ChunkEvent.Load event) {
        if (!NetworkManager.INSTANCE.isInit()) {
            return;
        }
        World world = event.getWorld();
        for (TileEntity blockEntity : event.getChunk().getTileEntityMap().values()) {
            if (!(blockEntity instanceof INodeBlockEntity<?> nodeBlockEntity)) {
                continue;
            }
            nodeBlockEntity.syncNodeAfterNetworkInit();
            BlockEntityLifecycleHooks.dispatchValidate(new BlockEntityLifeCycleEvent.Validate(world, blockEntity.getPos(), blockEntity));
        }
    }

    public void revalidateLoadedNodeBlockEntities() {
        for (Integer dimId : DimensionManager.getIDs()) {
            World world = DimensionManager.getWorld(dimId);
            revalidateLoadedNodeBlockEntities(world);
        }
    }

    public void revalidateLoadedNodeBlockEntities(@Nullable World world) {
        if (world == null || world.isRemote) {
            return;
        }
        List<TileEntity> loadedTileEntities = new ArrayList<>(world.loadedTileEntityList);
        for (TileEntity blockEntity : loadedTileEntities) {
            if (!(blockEntity instanceof BaseNodeTileEntity<?> nodeBlockEntity)) {
                continue;
            }
            nodeBlockEntity.syncNodeAfterNetworkInit();
            BlockEntityLifecycleHooks.dispatchValidate(new BlockEntityLifeCycleEvent.Validate(world, blockEntity.getPos(), blockEntity));
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.getWorld().isRemote) {
            PocketNodeManager.INSTANCE.onHostBlockBroken(event.getWorld(), event.getPos());
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
            EnergyTypeOverrideManager.get();
            HubChannelManager.INSTANCE.load();
        }
        if (!event.getWorld().isRemote && NetworkManager.INSTANCE.isInit()) {
            revalidateLoadedNodeBlockEntities(event.getWorld());
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP player) {
            NET_CHANNEL.sendTo(RenderingClear.INSTANCE, player);
            NET_CHANNEL.sendTo(new ConfigOverrideRendering(player.dimension), player);
            NET_CHANNEL.sendTo(new PocketNodeRendering(player), player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        HubPlatformServices.INSTANCE.markOnlinePlayersDirty();
        if (event.player instanceof EntityPlayerMP player) {
            NET_CHANNEL.sendTo(new ConfigOverrideRendering(player.dimension), player);
            NET_CHANNEL.sendTo(new PocketNodeRendering(player), player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        HubPlatformServices.INSTANCE.markOnlinePlayersDirty();
    }

    @Override
    public @Nullable Container getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        var tile = world.getTileEntity(new BlockPos(x, y, z));
        if (tile == null) {
            return null;
        } else if (tile instanceof BaseTileEntity te && te.hasGui()) {
            return te.getContainer(player);
        }
        return null;
    }

    @Override
    public @Nullable Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    private static class MyHubPlatformServices extends HubPlatformServices {
        @Override
        public List<PlayerIdentity> getOnlinePlayers() {
            if (CirculationFlowNetworks.server == null) {
                return Collections.emptyList();
            }
            List<PlayerIdentity> players = new ObjectArrayList<>();
            for (EntityPlayerMP player : CirculationFlowNetworks.server.getPlayerList().getPlayers()) {
                players.add(new PlayerIdentity(player.getUniqueID(), player.getName()));
            }
            return players;
        }

        @Override
        public boolean hasChannelManagementOverride(EntityPlayer player) {
            if (!(player instanceof EntityPlayerMP serverPlayer)) {
                return false;
            }
            if (serverPlayer.server == null) {
                return false;
            }
            if (serverPlayer.server.isSinglePlayer()) {
                return serverPlayer.server.getServerOwner().equalsIgnoreCase(serverPlayer.getName())
                    && serverPlayer.capabilities.isCreativeMode;
            }
            return serverPlayer.server.getPlayerList().getOppedPlayers().getEntry(serverPlayer.getGameProfile()) != null;
        }
    }

    private static class MyHubTeamServices extends HubTeamServices {
        @Override
        protected boolean arePlayersInSameTeamInternal(UUID firstPlayerId, UUID secondPlayerId) {
            if (!Universe.loaded()) {
                return false;
            }

            Universe universe = Universe.get();
            ForgePlayer firstPlayer = universe.getPlayer(firstPlayerId);
            ForgePlayer secondPlayer = universe.getPlayer(secondPlayerId);
            return firstPlayer != null
                && secondPlayer != null
                && firstPlayer.hasTeam()
                && secondPlayer.hasTeam()
                && firstPlayer.team.equalsTeam(secondPlayer.team);
        }
    }
}
