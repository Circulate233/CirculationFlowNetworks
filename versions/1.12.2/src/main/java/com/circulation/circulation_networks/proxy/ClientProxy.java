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
import com.circulation.circulation_networks.container.ContainerMachinePriority;
import com.circulation.circulation_networks.gui.GuiMachinePriority;
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
import com.circulation.circulation_networks.packets.ConfiguratorInteractionReport;
import com.circulation.circulation_networks.registry.RegistryBlocks;
import com.circulation.circulation_networks.registry.RegistryItems;
import com.circulation.circulation_networks.tiles.BaseTileEntity;
import com.circulation.circulation_networks.tiles.TileEntityNodePedestal;
import com.circulation.circulation_networks.tiles.nodes.TileEntityChargingNode;
import com.circulation.circulation_networks.tiles.nodes.TileEntityHub;
import com.circulation.circulation_networks.tiles.nodes.TileEntityPortNode;
import com.circulation.circulation_networks.tiles.nodes.TileEntityRelayNode;
import com.circulation.circulation_networks.utils.CI18n;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
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

    @Override
    public void displayConfiguratorInteraction(ConfiguratorInteractionReport message) {
        Minecraft.getMinecraft().addScheduledTask(() -> displayConfiguratorInteractionNow(message));
    }

    private static void displayConfiguratorInteractionNow(ConfiguratorInteractionReport message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        World world = minecraft.world;
        if (player == null || world == null) {
            return;
        }
        if (message.hasMachine()) {
            BlockPos pos = BlockPos.fromLong(message.getMachinePosition());
            player.sendMessage(new TextComponentTranslation(
                "item.circulation_networks.circulation_configurator.interaction.machine.title",
                resolveInteractionName(player, world, pos), pos.getX(), pos.getY(), pos.getZ()
            ));
            player.sendMessage(new TextComponentTranslation(
                "item.circulation_networks.circulation_configurator.interaction.machine.input",
                message.getMachineInput()
            ));
            player.sendMessage(new TextComponentTranslation(
                "item.circulation_networks.circulation_configurator.interaction.machine.output",
                message.getMachineOutput()
            ));
        }
        if (message.hasNetwork()) {
            displayInteractionRanking(player, world,
                "item.circulation_networks.circulation_configurator.interaction.network.input_top",
                message, true);
            displayInteractionRanking(player, world,
                "item.circulation_networks.circulation_configurator.interaction.network.output_top",
                message, false);
        }
    }

    private static void displayInteractionRanking(EntityPlayerSP player, World world, String titleKey,
                                                  ConfiguratorInteractionReport message, boolean input) {
        player.sendMessage(new TextComponentTranslation(titleKey));
        int count = input ? message.getInputCount() : message.getOutputCount();
        if (count == 0) {
            player.sendMessage(new TextComponentTranslation(
                "item.circulation_networks.circulation_configurator.interaction.network.empty"
            ));
            return;
        }
        for (int index = 0; index < count; index++) {
            long position = input ? message.getInputPosition(index) : message.getOutputPosition(index);
            String value = input ? message.getInputValue(index) : message.getOutputValue(index);
            BlockPos pos = BlockPos.fromLong(position);
            player.sendMessage(new TextComponentTranslation(
                "item.circulation_networks.circulation_configurator.interaction.network.entry",
                index + 1, resolveInteractionName(player, world, pos),
                pos.getX(), pos.getY(), pos.getZ(), value
            ));
        }
    }

    private static ITextComponent resolveInteractionName(EntityPlayerSP player, World world, BlockPos pos) {
        if (world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            return new TextComponentString(pos.toString());
        }
        IBlockState state = world.getBlockState(pos);
        try {
            Vec3d center = new Vec3d(pos).add(0.5D, 0.5D, 0.5D);
            RayTraceResult target = new RayTraceResult(center, EnumFacing.UP, pos);
            ItemStack picked = state.getBlock().getPickBlock(state, target, world, pos, player);
            if (!picked.isEmpty()) {
                return new TextComponentString(picked.getDisplayName());
            }
        } catch (RuntimeException exception) {
            CirculationFlowNetworks.LOGGER.warn("Failed to resolve picked block name at {}", pos, exception);
        }
        return new TextComponentString(state.getBlock().getLocalizedName());
    }

    @SubscribeEvent
    public void onModelRegister(ModelRegistryEvent event) {
        RegistryBlocks.registerBlockModels();
        RegistryItems.registerItemModels();
    }

    @Override
    public @Nullable GuiContainer getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        EnumHand priorityHand = ContainerMachinePriority.handFromGuiId(ID);
        if (priorityHand != null) {
            return new GuiMachinePriority(
                new ContainerMachinePriority(player, world, new BlockPos(x, y, z), priorityHand)
            );
        }
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
