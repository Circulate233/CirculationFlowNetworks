package com.circulation.circulation_networks;

import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.client.render.AnimatedNodeSpecialRenderer;
import com.circulation.circulation_networks.client.render.AnimatedSpecialItemModel;
import com.circulation.circulation_networks.client.render.ChargingNodeRenderer;
import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.client.render.HubRenderer;
import com.circulation.circulation_networks.client.render.NodePedestalRenderer;
import com.circulation.circulation_networks.client.render.PocketNodeModelCache;
import com.circulation.circulation_networks.client.render.PocketNodeSpecialRenderer;
import com.circulation.circulation_networks.client.render.PortNodeRenderer;
import com.circulation.circulation_networks.client.render.RelayNodeRenderer;
import com.circulation.circulation_networks.client.render.RotatingBlockModelCache;
import com.circulation.circulation_networks.client.render.RotatingModelVBORenderer;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
import com.circulation.circulation_networks.gui.GuiCirculationShielder;
import com.circulation.circulation_networks.gui.GuiHub;
import com.circulation.circulation_networks.gui.component.base.ComponentAtlas;
import com.circulation.circulation_networks.handlers.CirculationShielderRenderingHandler;
import com.circulation.circulation_networks.handlers.ConfigOverrideRenderingHandler;
import com.circulation.circulation_networks.handlers.EnergyWarningRenderingHandler;
import com.circulation.circulation_networks.handlers.ItemToolHandler;
import com.circulation.circulation_networks.handlers.NodeHighlightRenderingHandler;
import com.circulation.circulation_networks.handlers.NodeHudRenderingHandler;
import com.circulation.circulation_networks.handlers.NodeNetworkRenderingHandler;
import com.circulation.circulation_networks.handlers.PocketNodeRenderingHandler;
import com.circulation.circulation_networks.handlers.SpoceRenderingHandler;
import com.circulation.circulation_networks.manager.MachineTickManager;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.CFNBlocks;
import com.circulation.circulation_networks.registry.CFNMenuTypes;
import com.circulation.circulation_networks.utils.CI18n;
import com.circulation.circulation_networks.utils.RenderingBackend;
import com.circulation.circulation_networks.utils.RenderingBackendImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

final class CirculationFlowNetworksClient {

    private static final Identifier COMPONENT_ATLAS_RELOAD_LISTENER_ID =
        Identifier.fromNamespaceAndPath(CirculationFlowNetworks.MOD_ID, "component_atlas");
    private static boolean clientBootstrapComplete;

    private CirculationFlowNetworksClient() {
    }

    static void init(IEventBus modEventBus) {
        RenderingBackend.setInstance(new RenderingBackendImpl());
        java.io.File modConfigDir = new java.io.File(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().toFile(), CirculationFlowNetworks.MOD_ID);
        ComponentAtlas.INSTANCE.configure(modConfigDir);
        // Use addListener instead of register() to avoid NeoForge restriction
        // on @SubscribeEvent methods in superclass when registering a subclass
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentParticles e) -> {
            ensureClientBootstrap();
            if (SpoceRenderingHandler.INSTANCE != null) SpoceRenderingHandler.INSTANCE.onRenderWorldLast(e);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> {
            ensureClientBootstrap();
            if (SpoceRenderingHandler.INSTANCE != null) SpoceRenderingHandler.INSTANCE.onClientTick(e);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            ClientAnimationTicker.tick();
            MachineTickManager.INSTANCE.onClientTick();
        });
        NeoForge.EVENT_BUS.register(NodeNetworkRenderingHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(EnergyWarningRenderingHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(ConfigOverrideRenderingHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(PocketNodeRenderingHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(NodeHighlightRenderingHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(NodeHudRenderingHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(ItemToolHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(CirculationShielderRenderingHandler.INSTANCE);
        NeoForge.EVENT_BUS.addListener(CirculationFlowNetworksClient::onBlockEntityInvalidate);
        NeoForge.EVENT_BUS.addListener(CirculationFlowNetworksClient::onClientLoggingOut);
        modEventBus.addListener(CirculationFlowNetworksClient::onRegisterMenuScreens);
        modEventBus.addListener(CirculationFlowNetworksClient::onRegisterClientExtensions);
        modEventBus.addListener(CirculationFlowNetworksClient::onRegisterItemModels);
        modEventBus.addListener(CirculationFlowNetworksClient::onRegisterRenderPipelines);
        modEventBus.addListener(CirculationFlowNetworksClient::onRegisterSpecialModelRenderers);
        modEventBus.addListener(CirculationFlowNetworksClient::onAddClientReloadListeners);
        modEventBus.addListener(RotatingBlockModelCache::onRegisterAdditionalModels);
        modEventBus.addListener((ModelEvent.BakingCompleted event) -> {
            RotatingBlockModelCache.clear();
            PocketNodeModelCache.clear();
        });
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(CFNBlockEntityTypes.RELAY_NODE, RelayNodeRenderer::new);
            event.registerBlockEntityRenderer(CFNBlockEntityTypes.CHARGING_NODE, ChargingNodeRenderer::new);
            event.registerBlockEntityRenderer(CFNBlockEntityTypes.PORT_NODE, PortNodeRenderer::new);
            event.registerBlockEntityRenderer(CFNBlockEntityTypes.HUB, HubRenderer::new);
            event.registerBlockEntityRenderer(CFNBlockEntityTypes.NODE_PEDESTAL, NodePedestalRenderer::new);
        });

        CI18n.setI18nInternal(new CI18n() {
            @Override
            protected String formatInternal(String key, Object... params) {
                return I18n.get(key, params);
            }

            @Override
            protected boolean hasKeyInternal(String key) {
                return I18n.exists(key);
            }
        });
    }

    private static void ensureClientBootstrap() {
        if (clientBootstrapComplete) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ComponentAtlas.INSTANCE.restart();
        SpoceRenderingHandler.INSTANCE = new SpoceRenderingHandler();
        clientBootstrapComplete = true;
    }

    private static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(COMPONENT_ATLAS_RELOAD_LISTENER_ID, (ResourceManagerReloadListener) ignored -> ComponentAtlas.INSTANCE.restart());
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CFNMenuTypes.HUB_MENU, GuiHub::new);
        event.register(CFNMenuTypes.CIRCULATION_SHIELDER_MENU, GuiCirculationShielder::new);
    }

    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerBlock(MultiblockShellBlock.PARTICLE_CLIENT_EXTENSIONS, CFNBlocks.blockMultiblockShell);
    }

    private static void onRegisterSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(AnimatedNodeSpecialRenderer.TYPE_ID, AnimatedNodeSpecialRenderer.Unbaked.MAP_CODEC);
        event.register(PocketNodeSpecialRenderer.TYPE_ID, PocketNodeSpecialRenderer.Unbaked.MAP_CODEC);
    }

    private static void onRegisterItemModels(RegisterItemModelsEvent event) {
        event.register(AnimatedSpecialItemModel.TYPE_ID, AnimatedSpecialItemModel.Unbaked.MAP_CODEC);
    }

    private static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
        SpoceRenderingHandler.onRegisterRenderPipelines(event);
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft.getInstance().execute(() -> {
            MachineTickManager.INSTANCE.clear();
            NodeNetworkRenderingHandler.INSTANCE.clearLinks();
            EnergyWarningRenderingHandler.INSTANCE.clear();
            ConfigOverrideRenderingHandler.INSTANCE.clear();
            PocketNodeRenderingHandler.INSTANCE.clear();
            NodeHighlightRenderingHandler.INSTANCE.clear();
            CirculationShielderRenderingHandler.INSTANCE.clear();
            RotatingModelVBORenderer.clearAll();
            ClientAnimationTicker.reset();
            if (SpoceRenderingHandler.INSTANCE != null) {
                SpoceRenderingHandler.INSTANCE.clear();
            }
        });
    }

    private static void onBlockEntityInvalidate(BlockEntityLifeCycleEvent.Invalidate event) {
        if (!event.getWorld().isClientSide()) {
            return;
        }
        RotatingModelVBORenderer.removePosition(System.identityHashCode(event.getWorld()), event.getPos());
    }
}
