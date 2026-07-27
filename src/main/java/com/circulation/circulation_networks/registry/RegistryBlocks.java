package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.blocks.BaseBlockItem;
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
import com.circulation.circulation_networks.tiles.BlockEntityCirculationShielder;
import com.circulation.circulation_networks.tiles.BlockEntityMultiblockShell;
import com.circulation.circulation_networks.tiles.BlockEntityNodePedestal;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityChargingNode;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityHub;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityPortNode;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityRelayNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Function;

@SuppressWarnings("UnusedReturnValue")
public final class RegistryBlocks {

    private RegistryBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegistryBlocks::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            CFNBlocks.blockHub = registerBlock(helper, "hub", BlockHub::new);
            CFNBlocks.blockChargingNode = registerBlock(helper, "charging_node", BlockChargingNode::new);
            CFNBlocks.blockRelayNode = registerBlock(helper, "relay_node", BlockRelayNode::new);
            CFNBlocks.blockPortNode = registerBlock(helper, "port_node", BlockPortNode::new);
            CFNBlocks.blockCirculationShielder = registerBlock(helper, "circulation_shielder", BlockCirculationShielder::new);
            CFNBlocks.blockNodePedestal = registerBlock(helper, "node_pedestal", BlockNodePedestal::new);
            CFNBlocks.blockMultiblockShell = registerBlock(helper, "multiblock_shell", MultiblockShellBlock::new);
        });

        event.register(Registries.ITEM, helper -> {
            registerBlockItem(helper, "hub", CFNBlocks.blockHub);
            registerBlockItem(helper, "charging_node", CFNBlocks.blockChargingNode);
            registerBlockItem(helper, "relay_node", CFNBlocks.blockRelayNode);
            registerBlockItem(helper, "port_node", CFNBlocks.blockPortNode);
            registerBlockItem(helper, "circulation_shielder", CFNBlocks.blockCirculationShielder);
            registerBlockItem(helper, "node_pedestal", CFNBlocks.blockNodePedestal);
        });

        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            CFNBlockEntityTypes.HUB = registerBlockEntityType(helper, "hub",
                new BlockEntityType<>(BlockEntityHub::new, CFNBlocks.blockHub));
            CFNBlockEntityTypes.CHARGING_NODE = registerBlockEntityType(helper, "charging_node",
                new BlockEntityType<>(BlockEntityChargingNode::new, CFNBlocks.blockChargingNode));
            CFNBlockEntityTypes.RELAY_NODE = registerBlockEntityType(helper, "relay_node",
                new BlockEntityType<>(BlockEntityRelayNode::new, CFNBlocks.blockRelayNode));
            CFNBlockEntityTypes.PORT_NODE = registerBlockEntityType(helper, "port_node",
                new BlockEntityType<>(BlockEntityPortNode::new, CFNBlocks.blockPortNode));
            CFNBlockEntityTypes.CIRCULATION_SHIELDER = registerBlockEntityType(helper, "circulation_shielder",
                new BlockEntityType<>(BlockEntityCirculationShielder::new, CFNBlocks.blockCirculationShielder));
            CFNBlockEntityTypes.NODE_PEDESTAL = registerBlockEntityType(helper, "node_pedestal",
                new BlockEntityType<>(BlockEntityNodePedestal::new, CFNBlocks.blockNodePedestal));
            CFNBlockEntityTypes.MULTIBLOCK_SHELL = registerBlockEntityType(helper, "multiblock_shell",
                new BlockEntityType<>(BlockEntityMultiblockShell::new, CFNBlocks.blockMultiblockShell));
        });

        event.register(Registries.MENU, helper -> {
            CFNMenuTypes.HUB_MENU = registerMenuType(helper, "hub", IMenuTypeExtension.create((containerId, inv, buf) -> {
                BlockPos pos = buf.readBlockPos();
                BlockEntity be = inv.player.level().getBlockEntity(pos);
                if (be instanceof BlockEntityHub hub) {
                    hub.syncNodeAfterNetworkInit();
                    return new ContainerHub(CFNMenuTypes.HUB_MENU, containerId, inv.player, hub.getNode());
                }
                throw new IllegalArgumentException(String.valueOf(be));
            }));
            CFNMenuTypes.CIRCULATION_SHIELDER_MENU = registerMenuType(helper, "circulation_shielder", IMenuTypeExtension.create((containerId, inv, buf) -> {
                BlockPos pos = buf.readBlockPos();
                BlockEntity be = inv.player.level().getBlockEntity(pos);
                if (be instanceof BlockEntityCirculationShielder shielder) {
                    return new ContainerCirculationShielder(CFNMenuTypes.CIRCULATION_SHIELDER_MENU, containerId, inv.player, shielder);
                }
                throw new IllegalArgumentException(String.valueOf(be));
            }));
            CFNMenuTypes.MACHINE_PRIORITY_MENU = registerMenuType(helper, "machine_priority", IMenuTypeExtension.create((containerId, inv, buf) ->
                new ContainerMachinePriority(
                    CFNMenuTypes.MACHINE_PRIORITY_MENU,
                    containerId,
                    inv.player,
                    buf.readBlockPos(),
                    ContainerMachinePriority.handFromOrdinal(buf.readUnsignedByte())
                )
            ));
        });
    }

    private static <T extends Block> T registerBlock(RegisterEvent.RegisterHelper<Block> helper, String name, Function<ResourceKey<Block>, T> factory) {
        Identifier id = Identifier.parse(CirculationFlowNetworks.MOD_ID + ":" + name);
        T block = factory.apply(ResourceKey.create(Registries.BLOCK, id));
        helper.register(id, block);
        return block;
    }

    private static void registerBlockItem(RegisterEvent.RegisterHelper<Item> helper, String name, Block block) {
        Identifier id = Identifier.parse(CirculationFlowNetworks.MOD_ID + ":" + name);
        helper.register(
            id,
            new BaseBlockItem(
                block,
                new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)),
                "hub".equals(name)
            )
        );
    }

    private static <T extends BlockEntity> BlockEntityType<T> registerBlockEntityType(
        RegisterEvent.RegisterHelper<BlockEntityType<?>> helper, String name, BlockEntityType<T> type) {
        helper.register(Identifier.parse(CirculationFlowNetworks.MOD_ID + ":" + name), type);
        return type;
    }

    private static <T extends AbstractContainerMenu> MenuType<T> registerMenuType(
        RegisterEvent.RegisterHelper<MenuType<?>> helper, String name, MenuType<T> type) {
        helper.register(Identifier.parse(CirculationFlowNetworks.MOD_ID + ":" + name), type);
        return type;
    }
}
