package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.tiles.nodes.BlockEntityLongDistanceRelayNode;
import com.circulation.circulation_networks.tiles.BlockEntityCirculationShielder;
import com.circulation.circulation_networks.tiles.BlockEntityMultiblockShell;
import com.circulation.circulation_networks.tiles.BlockEntityNodePedestal;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityChargingNode;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityHub;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityPortNode;
import com.circulation.circulation_networks.tiles.nodes.BlockEntityRelayNode;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class CFNBlockEntityTypes {

    public static BlockEntityType<BlockEntityHub> HUB;
    public static BlockEntityType<BlockEntityChargingNode> CHARGING_NODE;
    public static BlockEntityType<BlockEntityRelayNode> RELAY_NODE;
    public static BlockEntityType<BlockEntityLongDistanceRelayNode> LONG_DISTANCE_RELAY_NODE;
    public static BlockEntityType<BlockEntityPortNode> PORT_NODE;
    public static BlockEntityType<BlockEntityCirculationShielder> CIRCULATION_SHIELDER;
    public static BlockEntityType<BlockEntityNodePedestal> NODE_PEDESTAL;
    public static BlockEntityType<BlockEntityMultiblockShell> MULTIBLOCK_SHELL;

    private CFNBlockEntityTypes() {
    }
}
