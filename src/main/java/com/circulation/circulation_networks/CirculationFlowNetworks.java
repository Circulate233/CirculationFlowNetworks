package com.circulation.circulation_networks;

import com.circulation.circulation_networks.energy.manager.FEHandlerManager;
import com.circulation.circulation_networks.handlers.NodePlacementValidationHandler;
import com.circulation.circulation_networks.handlers.NodeRescanHandler;
import com.circulation.circulation_networks.manager.ChargingManager;
import com.circulation.circulation_networks.manager.DatPersistenceScheduler;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.EnergyTypeOverrideManager;
import com.circulation.circulation_networks.manager.HubChannelManager;
import com.circulation.circulation_networks.manager.MachineNodeBlockEntityManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.network.CFNNetwork;
import com.circulation.circulation_networks.packets.ConfigOverrideRendering;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.packets.PocketNodeRendering;
import com.circulation.circulation_networks.packets.RenderingClear;
import com.circulation.circulation_networks.registry.CFNBlocks;
import com.circulation.circulation_networks.registry.CFNCreativeTabs;
import com.circulation.circulation_networks.registry.RegistryBlocks;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.registry.RegistryItems;
import com.circulation.circulation_networks.tiles.BlockEntityMultiblockShell;
import com.circulation.circulation_networks.utils.HubPlatformServices;
import com.circulation.circulation_networks.utils.HubTeamServices;
import com.circulation.circulation_networks.utils.Packet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Mod(CirculationFlowNetworks.MOD_ID)
public final class CirculationFlowNetworks {

    public static final String MOD_ID = "circulation_networks";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public CirculationFlowNetworks(IEventBus modEventBus, ModContainer modContainer) {
        CFNConfig.register(modContainer);
        RegistryItems.register(modEventBus);
        RegistryBlocks.register(modEventBus);
        CFNCreativeTabs.register(modEventBus);
        registerEnergyHandlers();
        modEventBus.addListener(CFNConfig::onConfigLoad);
        modEventBus.addListener(CFNConfig::onConfigReload);
        modEventBus.addListener(this::onRegisterPayloadHandlers);
        modEventBus.addListener(this::onLoadComplete);
        modEventBus.addListener(this::onRegisterCapabilities);
        if (FMLEnvironment.getDist().isClient()) {
            CirculationFlowNetworksClient.init(modEventBus);
        }
        installPlatformServices();
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPost);
        NeoForge.EVENT_BUS.register(NodePlacementValidationHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(NodeRescanHandler.INSTANCE);
    }

    public static <T extends Packet<T>> void sendToPlayer(T packet, ServerPlayer player) {
        CFNNetwork.sendToPlayer(packet, player);
    }

    public static <T extends Packet<T>> void sendToServer(T packet) {
        CFNNetwork.sendToServer(packet);
    }

    private void registerEnergyHandlers() {
        RegistryEnergyHandler.registerEnergyHandler(new FEHandlerManager());
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        RegistryEnergyHandler.lock();
    }

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        BlockEntityMultiblockShell.registerCapabilityProxy(event, Capabilities.Energy.BLOCK, CFNBlocks.blockMultiblockShell);
        BlockEntityMultiblockShell.registerCapabilityProxy(event, Capabilities.Item.BLOCK, CFNBlocks.blockMultiblockShell);
        BlockEntityMultiblockShell.registerCapabilityProxy(event, Capabilities.Fluid.BLOCK, CFNBlocks.blockMultiblockShell);
    }

    private void installPlatformServices() {
        HubPlatformServices.INSTANCE = new HubPlatformServices() {
            @Override
            public List<PlayerIdentity> getOnlinePlayers() {
                var server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    return Collections.emptyList();
                }
                List<PlayerIdentity> players = new ObjectArrayList<>();
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    players.add(new PlayerIdentity(player.getUUID(), player.getName().getString()));
                }
                return players;
            }

            @Override
            public boolean hasChannelManagementOverride(net.minecraft.world.entity.player.Player player) {
                if (!(player instanceof ServerPlayer serverPlayer)) {
                    return false;
                }
                var server = serverPlayer.level().getServer();
                if (server == null) {
                    return false;
                }
                NameAndId nameAndId = new NameAndId(serverPlayer.getGameProfile());
                if (server.isSingleplayer()) {
                    return server.isSingleplayerOwner(nameAndId)
                        && serverPlayer.getAbilities().instabuild;
                }
                return server.getPlayerList().isOp(nameAndId);
            }
        };

        if (ModList.get().isLoaded("ftbteams")) {
            HubTeamServices.INSTANCE = new HubTeamServices() {
                @Override
                protected boolean arePlayersInSameTeamInternal(UUID firstPlayerId, UUID secondPlayerId) {
                    var api = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api();
                    return api != null
                        && api.isManagerLoaded()
                        && api.getManager().arePlayersInSameTeam(firstPlayerId, secondPlayerId);
                }
            };
        }
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        HubChannelManager.INSTANCE.load();
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        CFNNetwork.register(event);
    }

    private void onServerTickPre(ServerTickEvent.Pre event) {
        EnergyMachineManager.INSTANCE.onServerTick();
    }

    private void onServerTickPost(ServerTickEvent.Post event) {
        MachineNodeBlockEntityManager.INSTANCE.onServerTick();
        DatPersistenceScheduler.INSTANCE.onServerTick();
    }

    private void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        NetworkManager.INSTANCE.validatePendingNodesInChunk(level, chunk.getPos().x(), chunk.getPos().z());
        PocketNodeManager.INSTANCE.onChunkLoad(level, chunk.getPos().x(), chunk.getPos().z());
    }

    private void onBlockBreak(BreakBlockEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            PocketNodeManager.INSTANCE.onHostBlockBroken(level, event.getPos());
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
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
        MachineNodeBlockEntityManager.INSTANCE.clear();
        DatPersistenceScheduler.INSTANCE.reset();
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        HubPlatformServices.INSTANCE.markOnlinePlayersDirty();
        if (event.getEntity() instanceof ServerPlayer player) {
            ConfigOverrideRendering.sendFullSync(player);
            sendToPlayer(new PocketNodeRendering(player), player);
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        HubPlatformServices.INSTANCE.markOnlinePlayersDirty();
        if (event.getEntity() instanceof ServerPlayer player) {
            NodeNetworkRendering.removePlayer(player);
        }
    }

    private void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NodeNetworkRendering.removePlayer(player);
            ConfigOverrideRendering.sendFullSync(player);
            sendToPlayer(RenderingClear.INSTANCE, player);
            sendToPlayer(new PocketNodeRendering(player), player);
        }
    }
}

