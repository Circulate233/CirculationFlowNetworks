package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.tiles.CirculationShielderBlockEntity;
import com.circulation.circulation_networks.tiles.MultiblockShellBlockEntity;
import com.circulation.circulation_networks.tiles.NodePedestalBlockEntity;
import com.circulation.circulation_networks.tiles.nodes.ChargingNodeBlockEntity;
import com.circulation.circulation_networks.tiles.nodes.HubBlockEntity;
import com.circulation.circulation_networks.tiles.nodes.PortNodeBlockEntity;
import com.circulation.circulation_networks.tiles.nodes.RelayNodeBlockEntity;
import com.circulation.circulation_networks.tiles.nodes.LongDistanceRelayNodeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class CFNBlockEntityTypes {

    public static BlockEntityType<HubBlockEntity> HUB;
    public static BlockEntityType<ChargingNodeBlockEntity> CHARGING_NODE;
    public static BlockEntityType<RelayNodeBlockEntity> RELAY_NODE;
    public static BlockEntityType<LongDistanceRelayNodeBlockEntity> LONG_DISTANCE_RELAY_NODE;
    public static BlockEntityType<PortNodeBlockEntity> PORT_NODE;
    public static BlockEntityType<CirculationShielderBlockEntity> CIRCULATION_SHIELDER;
    public static BlockEntityType<NodePedestalBlockEntity> NODE_PEDESTAL;
    public static BlockEntityType<MultiblockShellBlockEntity> MULTIBLOCK_SHELL;

    private CFNBlockEntityTypes() {
    }
}
