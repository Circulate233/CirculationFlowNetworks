package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.blocks.BlockCirculationShielder;
import com.circulation.circulation_networks.blocks.BlockNodePedestal;
import com.circulation.circulation_networks.blocks.MultiblockShellBlock;
import com.circulation.circulation_networks.blocks.nodes.BlockChargingNode;
import com.circulation.circulation_networks.blocks.nodes.BlockHub;
import com.circulation.circulation_networks.blocks.nodes.BlockPortNode;
import com.circulation.circulation_networks.blocks.nodes.BlockRelayNode;
import com.circulation.circulation_networks.blocks.nodes.BlockLongDistanceRelayNode;

public final class CFNBlocks {

    public static BlockPortNode blockPortNode;
    public static BlockChargingNode blockChargingNode;
    public static BlockRelayNode blockRelayNode;
    public static BlockLongDistanceRelayNode blockLongDistanceRelayNode;
    public static BlockCirculationShielder blockCirculationShielder;
    public static BlockHub blockHub;
    public static BlockNodePedestal blockNodePedestal;
    public static MultiblockShellBlock blockMultiblockShell;

    private CFNBlocks() {
    }
}