package com.circulation.circulation_networks;

import com.circulation.circulation_networks.api.INodeBlockEntity;
import com.circulation.circulation_networks.energy.manager.AE2HandlerManager;
import com.circulation.circulation_networks.energy.manager.DEHandlerManager;
import com.circulation.circulation_networks.energy.manager.FEHandlerManager;
import com.circulation.circulation_networks.energy.manager.MEKHandlerManager;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.handlers.NodePlacementValidationHandler;
import com.circulation.circulation_networks.handlers.NodeRescanHandler;
import com.circulation.circulation_networks.manager.ChargingManager;
import com.circulation.circulation_networks.manager.DatPersistenceScheduler;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.EnergyTypeOverrideManager;
import com.circulation.circulation_networks.manager.HubChannelManager;
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
import com.circulation.circulation_networks.tiles.MultiblockShellBlockEntity;
import com.circulation.circulation_networks.utils.BlockEntityLifecycleHooks;
import com.circulation.circulation_networks.utils.HubPlatformServices;
import com.circulation.circulation_networks.utils.HubTeamServices;
import com.circulation.circulation_networks.utils.Packet;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mekanism.common.tile.TileEntityBoundingBlock;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
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
    private static final boolean AE2_LOADED = ModList.get().isLoaded("ae2");

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
        if (FMLEnvironment.dist.isClient()) {
            CirculationFlowNetworksClient.init(modEventBus);
        }
        installPlatformServices();
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(this::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(this::onLevelUnload);
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
        if (AE2_LOADED) {
            RegistryEnergyHandler.registerEnergyHandler(AE2HandlerManager.INSTANCE);
        }
        RegistryEnergyHandler.registerEnergyHandler(new FEHandlerManager());
        if (ModList.get().isLoaded("draconicevolution")) {
            RegistryEnergyHandler.registerEnergyHandler(new DEHandlerManager());
        }
        if (ModList.get().isLoaded("mekanism")) {
            RegistryEnergyHandler.registerBlackClass(TileEntityBoundingBlock.class);
            RegistryEnergyHandler.registerEnergyHandler(new MEKHandlerManager());
        }
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        RegistryEnergyHandler.lock();
    }

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        MultiblockShellBlockEntity.registerCapabilityProxy(event, Capabilities.EnergyStorage.BLOCK, CFNBlocks.blockMultiblockShell);
        MultiblockShellBlockEntity.registerCapabilityProxy(event, Capabilities.ItemHandler.BLOCK, CFNBlocks.blockMultiblockShell);
        MultiblockShellBlockEntity.registerCapabilityProxy(event, Capabilities.FluidHandler.BLOCK, CFNBlocks.blockMultiblockShell);
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
                    players.add(new PlayerIdentity(player.getUUID(), player.getGameProfile().getName()));
                }
                return players;
            }

            @Override
            public boolean hasChannelManagementOverride(net.minecraft.world.entity.player.Player player) {
                if (!(player instanceof ServerPlayer serverPlayer)) {
                    return false;
                }
                if (serverPlayer.server.isSingleplayer()) {
                    return serverPlayer.server.isSingleplayerOwner(serverPlayer.getGameProfile())
                        && serverPlayer.getAbilities().instabuild;
                }
                return serverPlayer.server.getPlayerList().isOp(serverPlayer.getGameProfile());
            }
        };

        if (ModList.get().isLoaded("ftbteams")) {
            HubTeamServices.INSTANCE = new HubTeamServices() {
                @Override
                protected boolean arePlayersInSameTeamInternal(UUID firstPlayerId, UUID secondPlayerId) {
                    var api = FTBTeamsAPI.api();
                    return api != null
                        && api.isManagerLoaded()
                        && api.getManager().arePlayersInSameTeam(firstPlayerId, secondPlayerId);
                }
            };
        }
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        HubChannelManager.INSTANCE.load();
        EnergyTypeOverrideManager.get();
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        CFNNetwork.register(event);
    }

    private void onServerTickPre(ServerTickEvent.Pre event) {
        EnergyMachineManager.INSTANCE.onServerTick();
    }

    private void onServerTickPost(ServerTickEvent.Post event) {
        DatPersistenceScheduler.INSTANCE.onServerTick();
    }

    private void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        EnergyMachineManager.INSTANCE.markChunkLoaded(
            level.dimension().location().hashCode(), chunk.getPos().x, chunk.getPos().z
        );
        syncLoadedChunkNodeBlockEntities(level, chunk);
        NetworkManager.INSTANCE.validatePendingNodesInChunk(level, chunk.getPos().x, chunk.getPos().z);
        PocketNodeManager.INSTANCE.onChunkLoad(level, chunk.getPos().x, chunk.getPos().z);
    }

    private void syncLoadedChunkNodeBlockEntities(Level level, LevelChunk chunk) {
        if (!NetworkManager.INSTANCE.isInit()) {
            return;
        }
        for (var blockEntity : chunk.getBlockEntities().values()) {
            if (!(blockEntity instanceof INodeBlockEntity<?> nodeBlockEntity)) {
                continue;
            }
            nodeBlockEntity.syncNodeAfterNetworkInit();
            BlockEntityLifecycleHooks.dispatchValidate(
                new BlockEntityLifeCycleEvent.Validate(level, blockEntity.getBlockPos(), blockEntity)
            );
        }
    }

    private void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        EnergyMachineManager.INSTANCE.markChunkUnloaded(
            level.dimension().location().hashCode(), chunk.getPos().x, chunk.getPos().z
        );
    }

    private void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            EnergyMachineManager.INSTANCE.clearDimensionChunkResidency(
                level.dimension().location().hashCode()
            );
        }
    }

    private void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            PocketNodeManager.INSTANCE.onHostBlockBroken(level, event.getPos());
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        NetworkManager.INSTANCE.saveGrid();
        PocketNodeManager.INSTANCE.save();
        HubChannelManager.INSTANCE.save();
        EnergyTypeOverrideManager.save();
        EnergyMachineManager.INSTANCE.onServerStop();
        NetworkManager.INSTANCE.onServerStop();
        PocketNodeManager.INSTANCE.onServerStop();
        EnergyTypeOverrideManager.onServerStop();
        ChargingManager.INSTANCE.onServerStop();
        HubChannelManager.INSTANCE.onServerStop();
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
