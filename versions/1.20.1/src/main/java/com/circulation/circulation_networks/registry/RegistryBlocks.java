package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.blocks.AnimatedNodeBlockItem;
import com.circulation.circulation_networks.blocks.BlockCirculationShielder;
import com.circulation.circulation_networks.blocks.BlockNodePedestal;
import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.blocks.nodes.BlockChargingNode;
import com.circulation.circulation_networks.blocks.nodes.BlockHub;
import com.circulation.circulation_networks.blocks.nodes.BlockPortNode;
import com.circulation.circulation_networks.blocks.nodes.BlockRelayNode;
import com.circulation.circulation_networks.container.ContainerCirculationShielder;
import com.circulation.circulation_networks.container.ContainerHub;
import com.circulation.circulation_networks.container.ContainerMachinePriority;
import com.circulation.circulation_networks.tiles.CirculationShielderBlockEntity;
import com.circulation.circulation_networks.tiles.MultiblockShellBlockEntity;
import com.circulation.circulation_networks.tiles.NodePedestalBlockEntity;
import com.circulation.circulation_networks.tiles.nodes.ChargingNodeBlockEntity;
import com.circulation.circulation_networks.tiles.nodes.HubBlockEntity;
import com.circulation.circulation_networks.tiles.nodes.PortNodeBlockEntity;
import com.circulation.circulation_networks.tiles.nodes.RelayNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@SuppressWarnings("UnusedReturnValue")
public final class RegistryBlocks {

    private RegistryBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegistryBlocks::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            CFNBlocks.blockHub = registerBlock(helper, "hub", new BlockHub());
            CFNBlocks.blockChargingNode = registerBlock(helper, "charging_node", new BlockChargingNode());
            CFNBlocks.blockRelayNode = registerBlock(helper, "relay_node", new BlockRelayNode());
            CFNBlocks.blockPortNode = registerBlock(helper, "port_node", new BlockPortNode());
            CFNBlocks.blockCirculationShielder = registerBlock(helper, "circulation_shielder", new BlockCirculationShielder());
            CFNBlocks.blockNodePedestal = registerBlock(helper, "node_pedestal", new BlockNodePedestal());
            CFNBlocks.blockMultiblockShell = registerBlock(helper, "multiblock_shell", new MultiblockShellBlock());
        });

        event.register(ForgeRegistries.Keys.ITEMS, helper -> {
            registerBlockItem(helper, "hub", CFNBlocks.blockHub);
            registerAnimatedNodeBlockItem(helper, "charging_node", CFNBlocks.blockChargingNode);
            registerAnimatedNodeBlockItem(helper, "relay_node", CFNBlocks.blockRelayNode);
            registerAnimatedNodeBlockItem(helper, "port_node", CFNBlocks.blockPortNode);
            registerBlockItem(helper, "circulation_shielder", CFNBlocks.blockCirculationShielder);
            registerAnimatedNodeBlockItem(helper, "node_pedestal", CFNBlocks.blockNodePedestal);
        });

        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, helper -> {
            CFNBlockEntityTypes.HUB = registerBlockEntityType(helper, "hub",
                BlockEntityType.Builder.of(HubBlockEntity::new, CFNBlocks.blockHub).build(null));
            CFNBlockEntityTypes.CHARGING_NODE = registerBlockEntityType(helper, "charging_node",
                BlockEntityType.Builder.of(ChargingNodeBlockEntity::new, CFNBlocks.blockChargingNode).build(null));
            CFNBlockEntityTypes.RELAY_NODE = registerBlockEntityType(helper, "relay_node",
                BlockEntityType.Builder.of(RelayNodeBlockEntity::new, CFNBlocks.blockRelayNode).build(null));
            CFNBlockEntityTypes.PORT_NODE = registerBlockEntityType(helper, "port_node",
                BlockEntityType.Builder.of(PortNodeBlockEntity::new, CFNBlocks.blockPortNode).build(null));
            CFNBlockEntityTypes.CIRCULATION_SHIELDER = registerBlockEntityType(helper, "circulation_shielder",
                BlockEntityType.Builder.of(CirculationShielderBlockEntity::new, CFNBlocks.blockCirculationShielder).build(null));
            CFNBlockEntityTypes.NODE_PEDESTAL = registerBlockEntityType(helper, "node_pedestal",
                BlockEntityType.Builder.of(NodePedestalBlockEntity::new, CFNBlocks.blockNodePedestal).build(null));
            CFNBlockEntityTypes.MULTIBLOCK_SHELL = registerBlockEntityType(helper, "multiblock_shell",
                BlockEntityType.Builder.of(MultiblockShellBlockEntity::new, CFNBlocks.blockMultiblockShell).build(null));
        });

        event.register(ForgeRegistries.Keys.MENU_TYPES, helper -> {
            CFNMenuTypes.HUB_MENU = registerMenuType(helper, "hub", IForgeMenuType.create((containerId, inv, buf) -> {
                BlockPos pos = buf.readBlockPos();
                BlockEntity be = inv.player.level().getBlockEntity(pos);
                if (be instanceof HubBlockEntity hub) {
                    hub.syncNodeAfterNetworkInit();
                    return new ContainerHub(CFNMenuTypes.HUB_MENU, containerId, inv.player, hub.getNode());
                }
                return null;
            }));
            CFNMenuTypes.CIRCULATION_SHIELDER_MENU = registerMenuType(helper, "circulation_shielder", IForgeMenuType.create((containerId, inv, buf) -> {
                BlockPos pos = buf.readBlockPos();
                BlockEntity be = inv.player.level().getBlockEntity(pos);
                if (be instanceof CirculationShielderBlockEntity shielder) {
                    return new ContainerCirculationShielder(CFNMenuTypes.CIRCULATION_SHIELDER_MENU, containerId, inv.player, shielder);
                }
                return null;
            }));
            CFNMenuTypes.MACHINE_PRIORITY_MENU = registerMenuType(helper, "machine_priority", IForgeMenuType.create(
                (containerId, inv, buf) -> new ContainerMachinePriority(
                    CFNMenuTypes.MACHINE_PRIORITY_MENU,
                    containerId,
                    inv.player,
                    buf.readBlockPos(),
                    ContainerMachinePriority.handFromOrdinal(buf.readByte())
                )
            ));
        });
    }

    private static <T extends Block> T registerBlock(RegisterEvent.RegisterHelper<Block> helper, String name, T block) {
        helper.register(new ResourceLocation(CirculationFlowNetworks.MOD_ID, name), block);
        return block;
    }

    private static void registerBlockItem(RegisterEvent.RegisterHelper<Item> helper, String name, Block block) {
        helper.register(new ResourceLocation(CirculationFlowNetworks.MOD_ID, name), new BlockItem(block, new Item.Properties()));
    }

    private static void registerAnimatedNodeBlockItem(RegisterEvent.RegisterHelper<Item> helper, String name, Block block) {
        helper.register(new ResourceLocation(CirculationFlowNetworks.MOD_ID, name), new AnimatedNodeBlockItem(block, new Item.Properties()));
    }

    private static <T extends BlockEntity> BlockEntityType<T> registerBlockEntityType(
        RegisterEvent.RegisterHelper<BlockEntityType<?>> helper, String name, BlockEntityType<T> type) {
        helper.register(new ResourceLocation(CirculationFlowNetworks.MOD_ID, name), type);
        return type;
    }

    private static <T extends net.minecraft.world.inventory.AbstractContainerMenu> MenuType<T> registerMenuType(
        RegisterEvent.RegisterHelper<MenuType<?>> helper, String name, MenuType<T> type) {
        helper.register(new ResourceLocation(CirculationFlowNetworks.MOD_ID, name), type);
        return type;
    }
}
