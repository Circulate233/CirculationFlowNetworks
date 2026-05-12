package com.circulation.circulation_networks;

import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.client.render.ChargingNodeRenderer;
import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.client.render.HubRenderer;
import com.circulation.circulation_networks.client.render.NodePedestalRenderer;
import com.circulation.circulation_networks.client.render.PocketNodeItemStackRenderer;
import com.circulation.circulation_networks.client.render.PocketNodeModelCache;
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
import com.circulation.circulation_networks.handlers.SpoceRenderingHandlerGL46L3;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.CFNBlocks;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.registry.CFNMenuTypes;
import com.circulation.circulation_networks.utils.CI18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL11;

final class CirculationFlowNetworksClient {

    private static OpenGLLevel openGLLevel = OpenGLLevel.GL_1_1;
    private static String openGLVersionString = "unavailable";

    private CirculationFlowNetworksClient() {
    }

    static void init(IEventBus modEventBus) {
        java.io.File modConfigDir = new java.io.File(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().toFile(), CirculationFlowNetworks.MOD_ID);
        ComponentAtlas.INSTANCE.configure(modConfigDir);
        Minecraft.getInstance().execute(() -> {
            registerAtlasReloadListener();
            openGLLevel = detectOpenGLLevel();
            SpoceRenderingHandler.INSTANCE = createSpoceHandler();
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (SpoceRenderingHandler.INSTANCE != null) SpoceRenderingHandler.INSTANCE.onRenderWorldLast(e);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> {
            if (SpoceRenderingHandler.INSTANCE != null) SpoceRenderingHandler.INSTANCE.onClientTick(e);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ClientAnimationTicker.tick());
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
        modEventBus.addListener(RotatingBlockModelCache::onRegisterAdditionalModels);
        modEventBus.addListener(PocketNodeModelCache::onRegisterAdditionalModels);
        modEventBus.addListener((ModelEvent.BakingCompleted event) -> {
            RotatingBlockModelCache.clear();
            PocketNodeModelCache.clear();
            PocketNodeItemStackRenderer.clearCache();
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

    private static void registerAtlasReloadListener() {
        var resourceManager = Minecraft.getInstance().getResourceManager();
        if (resourceManager instanceof ReloadableResourceManager reloadable) {
            reloadable.registerReloadListener((ResourceManagerReloadListener) ignored -> ComponentAtlas.INSTANCE.restart());
        }
        ComponentAtlas.INSTANCE.restart();
    }

    private static OpenGLLevel detectOpenGLLevel() {
        String versionStr;
        try {
            versionStr = GL11.glGetString(GL11.GL_VERSION);
        } catch (Throwable throwable) {
            openGLVersionString = "unavailable";
            CirculationFlowNetworks.LOGGER.warn("Failed to obtain OpenGL version", throwable);
            return OpenGLLevel.GL_1_1;
        }

        if (versionStr == null) {
            openGLVersionString = "unavailable";
            CirculationFlowNetworks.LOGGER.warn("Failed to obtain OpenGL version");
            return OpenGLLevel.GL_1_1;
        }
        openGLVersionString = versionStr;

        try {
            String[] parts = versionStr.split("[. ]");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            if (major > 4 || (major == 4 && minor >= 6)) {
                return OpenGLLevel.GL_4_6;
            }
            if (major > 3 || (major == 3 && minor >= 2)) {
                return OpenGLLevel.GL_3_2_PLUS;
            }
        } catch (Exception e) {
            CirculationFlowNetworks.LOGGER.warn("Failed to parse OpenGL version: {}", versionStr, e);
        }

        return OpenGLLevel.GL_1_1;
    }

    private static SpoceRenderingHandler ensureSupportedOpenGL() {
        String message = "OpenGL version too low for Circulation Flow Networks. Detected: "
            + openGLVersionString + ". Minimum required: 3.2.";
        CirculationFlowNetworks.LOGGER.error(message);
        throw new IllegalStateException(message);
    }

    private static SpoceRenderingHandler createSpoceHandler() {
        return switch (openGLLevel) {
            case GL_4_6 -> new SpoceRenderingHandlerGL46L3();
            case GL_3_2_PLUS -> new SpoceRenderingHandler();
            default -> ensureSupportedOpenGL();
        };
    }


    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CFNMenuTypes.HUB_MENU, GuiHub::new);
        event.register(CFNMenuTypes.CIRCULATION_SHIELDER_MENU, GuiCirculationShielder::new);
    }

    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions animatedRenderer = new IClientItemExtensions() {
            @Override
            public @org.jetbrains.annotations.NotNull net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.circulation.circulation_networks.client.render.AnimatedNodeItemStackRenderer.getInstance();
            }
        };
        event.registerItem(animatedRenderer,
            CFNBlocks.blockRelayNode.asItem(),
            CFNBlocks.blockChargingNode.asItem(),
            CFNBlocks.blockPortNode.asItem(),
            CFNBlocks.blockNodePedestal.asItem()
        );
        IClientItemExtensions pocketNodeRenderer = new IClientItemExtensions() {
            @Override
            public @org.jetbrains.annotations.NotNull net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return PocketNodeItemStackRenderer.getInstance();
            }
        };
        event.registerItem(pocketNodeRenderer,
            CFNItems.pocketPortNode,
            CFNItems.pocketChargingNode,
            CFNItems.pocketRelayNode
        );
        event.registerBlock(MultiblockShellBlock.PARTICLE_CLIENT_EXTENSIONS, CFNBlocks.blockMultiblockShell);
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft.getInstance().execute(() -> {
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

    private enum OpenGLLevel {
        GL_1_1,
        GL_3_2_PLUS,
        GL_4_6
    }
}
