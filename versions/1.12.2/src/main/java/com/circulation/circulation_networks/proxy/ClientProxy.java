package com.circulation.circulation_networks.proxy;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.client.render.AnimatedNodeItemStackRenderer;
import com.circulation.circulation_networks.client.render.ChargingNodeRotatingRenderer;
import com.circulation.circulation_networks.client.render.ClientAnimationTicker;
import com.circulation.circulation_networks.client.render.HubRotatingRenderer;
import com.circulation.circulation_networks.client.render.NodePedestalRotatingRenderer;
import com.circulation.circulation_networks.client.render.PocketNodeItemStackRenderer;
import com.circulation.circulation_networks.client.render.PocketNodeModelCache;
import com.circulation.circulation_networks.client.render.PortNodeRotatingRenderer;
import com.circulation.circulation_networks.client.render.RelayNodeRotatingRenderer;
import com.circulation.circulation_networks.client.render.RotatingBlockModelCache;
import com.circulation.circulation_networks.client.render.RotatingModelRenderHelper;
import com.circulation.circulation_networks.events.BlockEntityLifeCycleEvent;
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
import com.circulation.circulation_networks.handlers.SpoceRenderingHandlerGL32L2;
import com.circulation.circulation_networks.handlers.SpoceRenderingHandlerGL32L3;
import com.circulation.circulation_networks.handlers.SpoceRenderingHandlerGL46L2;
import com.circulation.circulation_networks.handlers.SpoceRenderingHandlerGL46L3;
import com.circulation.circulation_networks.manager.MachineTickManager;
import com.circulation.circulation_networks.registry.RegistryBlocks;
import com.circulation.circulation_networks.registry.RegistryItems;
import com.circulation.circulation_networks.tiles.BaseTileEntity;
import com.circulation.circulation_networks.tiles.TileEntityNodePedestal;
import com.circulation.circulation_networks.tiles.nodes.TileEntityChargingNode;
import com.circulation.circulation_networks.tiles.nodes.TileEntityHub;
import com.circulation.circulation_networks.tiles.nodes.TileEntityPortNode;
import com.circulation.circulation_networks.tiles.nodes.TileEntityRelayNode;
import com.circulation.circulation_networks.utils.CI18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.io.File;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {

    public static OpenGLLevel openGLLevel = OpenGLLevel.GL_1_1;
    public static boolean isLWJGL3 = false;

    static {
        try {
            Class.forName("org.lwjgl.system.MemoryStack");
            isLWJGL3 = true;
        } catch (ClassNotFoundException ignored) {
        }
    }

    public static OpenGLLevel detectOpenGLLevel() {
        String versionStr = GL11.glGetString(GL11.GL_VERSION);
        if (versionStr == null) {
            CirculationFlowNetworks.LOGGER.warn("Failed to obtain OpenGL version, defaulting to GL_1_1");
            return OpenGLLevel.GL_1_1;
        }
        try {
            String[] parts = versionStr.split("[. ]");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            if (major > 4 || (major == 4 && minor >= 6)) {
                return OpenGLLevel.GL_4_6;
            } else if (major > 3 || (major == 3 && minor >= 2)) {
                return OpenGLLevel.GL_3_2_PLUS;
            } else {
                return OpenGLLevel.GL_1_1;
            }
        } catch (Exception e) {
            CirculationFlowNetworks.LOGGER.warn("Failed to parse OpenGL version: {}", versionStr);
            return OpenGLLevel.GL_1_1;
        }
    }

    private static SpoceRenderingHandler createSpoceHandler() {
        return switch (openGLLevel) {
            case GL_4_6 -> isLWJGL3 ? new SpoceRenderingHandlerGL46L3() : new SpoceRenderingHandlerGL46L2();
            case GL_3_2_PLUS -> isLWJGL3 ? new SpoceRenderingHandlerGL32L3() : new SpoceRenderingHandlerGL32L2();
            default -> new SpoceRenderingHandler();
        };
    }

    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        CI18n.setI18nInternal(new MyCI18n());
    }

    public void init() {
        super.init();
        File modConfigDir = new File(Loader.instance().getConfigDir(), CirculationFlowNetworks.MOD_ID);
        ComponentAtlas.INSTANCE.startAsync(modConfigDir);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRelayNode.class, new RelayNodeRotatingRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityChargingNode.class, new ChargingNodeRotatingRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityPortNode.class, new PortNodeRotatingRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityHub.class, new HubRotatingRenderer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityNodePedestal.class, new NodePedestalRotatingRenderer());
        AnimatedNodeItemStackRenderer.bindItemRenderers();
        PocketNodeItemStackRenderer.bindItemRenderers();
        openGLLevel = detectOpenGLLevel();
        SpoceRenderingHandler.INSTANCE = createSpoceHandler();
    }

    public void postInit() {
        super.postInit();
        MinecraftForge.EVENT_BUS.register(SpoceRenderingHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NodeNetworkRenderingHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(EnergyWarningRenderingHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ConfigOverrideRenderingHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(PocketNodeRenderingHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NodeHighlightRenderingHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(NodeHudRenderingHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ItemToolHandler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(CirculationShielderRenderingHandler.INSTANCE);
    }

    @SubscribeEvent
    public void onModelRegister(ModelRegistryEvent event) {
        RegistryBlocks.registerBlockModels();
        RegistryItems.registerItemModels();
    }

    @Override
    public @Nullable GuiContainer getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        var tile = world.getTileEntity(new BlockPos(x, y, z));
        if (tile == null) {
            return null;
        } else if (tile instanceof BaseTileEntity te && te.hasGui()) {
            return te.getGui(player);
        }
        return null;
    }

    @SubscribeEvent
    public void onTextureReloadPre(TextureStitchEvent.Pre event) {
        ComponentAtlas.INSTANCE.dispose();
        RotatingModelRenderHelper.clearDisplayLists();
        RotatingBlockModelCache.clear();
        PocketNodeModelCache.clear();
        PocketNodeItemStackRenderer.clearCache();
        // 1.12.2 bakes hub sub-models on demand, so their dynamic-only sprites must be stitched explicitly.
        RotatingBlockModelCache.registerAdditionalSprites(event.getMap());
        PocketNodeModelCache.registerAdditionalSprites(event.getMap());
    }

    @SubscribeEvent
    public void onTextureReloadPost(TextureStitchEvent.Post event) {
        ComponentAtlas.INSTANCE.restart();
    }

    @SubscribeEvent
    public void onClientStop(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            MachineTickManager.INSTANCE.clear();
            NodeNetworkRenderingHandler.INSTANCE.clearLinks();
            EnergyWarningRenderingHandler.INSTANCE.clear();
            ConfigOverrideRenderingHandler.INSTANCE.clear();
            PocketNodeRenderingHandler.INSTANCE.clear();
            NodeHighlightRenderingHandler.INSTANCE.clear();
            SpoceRenderingHandler.INSTANCE.clear();
            ClientAnimationTicker.reset();
        });
    }

    @SubscribeEvent
    public void onBlockEntityInvalidate(BlockEntityLifeCycleEvent.Invalidate event) {
        if (!event.getWorld().isRemote) {
            return;
        }
        RotatingModelRenderHelper.removeDisplayLists(System.identityHashCode(event.getWorld()), event.getPos());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            ClientAnimationTicker.tick();
            MachineTickManager.INSTANCE.onClientTick();
        }
    }

    public enum OpenGLLevel {
        GL_1_1, GL_3_2_PLUS, GL_4_6
    }

    private static class MyCI18n extends CI18n {
        @Override
        public String formatInternal(String key, Object... params) {
            return I18n.format(key, params);
        }

        @Override
        public boolean hasKeyInternal(String key) {
            return I18n.hasKey(key);
        }
    }
}
