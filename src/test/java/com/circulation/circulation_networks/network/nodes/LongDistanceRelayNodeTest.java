package com.circulation.circulation_networks.network.nodes;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.IGrid;
import com.circulation.circulation_networks.handlers.LongDistanceRelayInteraction;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.manager.MachineBindingIndex;
import com.circulation.circulation_networks.network.Grid;
import com.circulation.circulation_networks.registry.NodeTypes;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LongDistanceRelayNodeTest {
    private Level topologyWorld;

    @BeforeAll
    static void initializeMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @AfterEach
    void resetTopology() {
        NetworkManager.INSTANCE.onServerStop();
        MachineBindingIndex.INSTANCE.onServerStop();
    }
    private static CompoundTag tag(int x, boolean otherDimension) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", otherDimension ? "minecraft:the_nether" : "minecraft:overworld");
        tag.putLong("pos", new BlockPos(x, 64, 0).asLong());
        tag.putDouble("linkScope", 16);
        return tag;
    }

    private static LongDistanceRelayNode relay(int x) {
        return new LongDistanceRelayNode(tag(x, false));
    }

    private static void pair(LongDistanceRelayNode a, LongDistanceRelayNode b) {
        a.rememberPeer(b);
        b.rememberPeer(a);
    }

    @Test
    void ordinaryNodesMayReachRelayButRelayDoesNotInitiateOrdinaryLinks() {
        LongDistanceRelayNode relay = relay(0);
        Node nearby = new Node(NodeTypes.RELAY_NODE, tag(8, false));
        assertEquals(0, relay.getLinkScope());
        assertEquals(0, relay.getLinkScopeSq());
        assertEquals(INode.LinkType.B_TO_A, relay.linkScopeCheck(nearby));
        assertEquals(INode.LinkType.A_TO_B, nearby.linkScopeCheck(relay));
        assertEquals(INode.LinkType.DISCONNECT, relay.linkScopeCheck(relay(1)));
    }

    @Test
    void explicitPairHasNoDistanceLimitAndRequiresBothIdentities() {
        LongDistanceRelayNode a = relay(-29_000_000);
        LongDistanceRelayNode b = relay(29_000_000);
        a.rememberPeer(b);
        assertFalse(a.canLinkTo(b));
        b.rememberPeer(a);
        assertEquals(INode.LinkType.DOUBLY, a.linkScopeCheck(b));
        assertEquals(INode.LinkType.DOUBLY, b.linkScopeCheck(a));
    }

    @Test
    void threeConnectionsFillEitherEndpointButRepeatedPairDoesNotUseAnotherSlot() {
        LongDistanceRelayNode a = relay(0);
        LongDistanceRelayNode first = relay(1000);
        pair(a, first);
        pair(a, relay(2000));
        assertTrue(a.hasFreeConnection());
        pair(a, relay(3000));
        assertFalse(a.hasFreeConnection());
        assertEquals(NetworkManager.RelayLinkResult.FULL, NetworkManager.checkRelayConnection(a, relay(4000)));
        assertEquals(NetworkManager.RelayLinkResult.FULL, NetworkManager.checkRelayConnection(relay(4000), a));
        assertEquals(NetworkManager.RelayLinkResult.ALREADY_CONNECTED, NetworkManager.checkRelayConnection(a, first));
        pair(a, first);
        assertEquals(3, a.getPeerPositions().size());
        a.forgetPeer(first);
        assertTrue(a.hasFreeConnection());
    }

    @Test
    void savedPairRestoresItsIdentityAndUnboundedLink() {
        LongDistanceRelayNode a = relay(0);
        LongDistanceRelayNode b = relay(100000);
        pair(a, b);
        LongDistanceRelayNode restoredA = new LongDistanceRelayNode(a.serialize());
        LongDistanceRelayNode restoredB = new LongDistanceRelayNode(b.serialize());
        assertEquals(a.getRelayId(), restoredA.getRelayId());
        assertEquals(b.getRelayId(), restoredB.getRelayId());
        assertEquals(INode.LinkType.DOUBLY, restoredA.linkScopeCheck(restoredB));
        assertEquals(1, restoredA.getPeerPositions().size());
        assertEquals(0, restoredA.getLinkScope());
    }

    @Test
    void aReplacementAtTheSamePositionDoesNotInheritTheOldConnection() {
        LongDistanceRelayNode a = relay(0);
        LongDistanceRelayNode b = relay(1000);
        pair(a, b);
        LongDistanceRelayNode replacement = relay(1000);
        replacement.rememberPeer(a);
        assertFalse(a.canLinkTo(replacement));
        assertFalse(replacement.canLinkTo(a));
    }

    @Test
    void differentDimensionsNeverLinkEvenWithManuallyRestoredPeerData() {
        LongDistanceRelayNode a = relay(0);
        LongDistanceRelayNode b = new LongDistanceRelayNode(tag(1000, true));
        pair(a, b);
        assertFalse(a.canLinkTo(b));
        assertEquals(NetworkManager.RelayLinkResult.DIFFERENT_DIMENSION, NetworkManager.checkRelayConnection(a, b));
    }

    @Test
    void relayHasNoPocketFormAndCannotConnectToItself() {
        LongDistanceRelayNode a = relay(0);
        assertFalse(a.getNodeType().allowsPocketNode());
        assertFalse(a.canLinkTo(a));
        assertEquals(NetworkManager.RelayLinkResult.UNAVAILABLE, NetworkManager.checkRelayConnection(a, a));
    }

    @Test
    void twoHubGridsCannotMergeButLinksInsideOneHubGridAreAllowed() throws Exception {
        LongDistanceRelayNode a = relay(0);
        LongDistanceRelayNode b = relay(1000);
        Field grid = Node.class.getDeclaredField("grid");
        grid.setAccessible(true);
        IGrid first = hubGrid();
        grid.set(a, first);
        grid.set(b, hubGrid());
        assertEquals(NetworkManager.RelayLinkResult.HUB_CONFLICT, NetworkManager.checkRelayConnection(a, b));
        grid.set(b, first);
        assertEquals(NetworkManager.RelayLinkResult.CONNECTED, NetworkManager.checkRelayConnection(a, b));
    }

    private static IGrid hubGrid() {
        IHubNode hub = (IHubNode) Proxy.newProxyInstance(IHubNode.class.getClassLoader(), new Class<?>[] {IHubNode.class},
            (proxy, method, arguments) -> null);
        return (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[] {IGrid.class},
            (proxy, method, arguments) -> method.getName().equals("getHubNode") ? hub : null);
    }

    @SuppressWarnings("unchecked")
    private void registerTopology(Node... nodes) throws Exception {
        // Keep world/chunk I/O outside this graph test while exercising real node lifecycle callbacks.
        topologyWorld = mock(Level.class);
        when(topologyWorld.getChunkSource()).thenReturn(mock(ChunkSource.class));
        Field worldField = Node.class.getDeclaredField("world");
        worldField.setAccessible(true);
        NetworkManager manager = NetworkManager.INSTANCE;
        Field activeField = NetworkManager.class.getDeclaredField("activeNodes");
        activeField.setAccessible(true);
        var active = (ReferenceSet<INode>) activeField.get(manager);
        Field positionsField = NetworkManager.class.getDeclaredField("posNodes");
        positionsField.setAccessible(true);
        var positions = (Object2ObjectMap<String, Long2ReferenceMap<INode>>) positionsField.get(manager);
        Field gridsField = NetworkManager.class.getDeclaredField("grids");
        gridsField.setAccessible(true);
        var grids = (Map<UUID, IGrid>) gridsField.get(manager);
        Grid grid = new Grid(UUID.randomUUID());
        grids.put(grid.getId(), grid);
        for (Node node : nodes) {
            worldField.set(node, new WeakReference<>(topologyWorld));
            node.setActive(true);
            node.setGrid(grid);
            grid.getNodes().add(node);
            active.add(node);
            var dimension = positions.get(node.getDimensionId());
            if (dimension == positions.defaultReturnValue()) {
                dimension = new Long2ReferenceOpenHashMap<>();
                positions.put(node.getDimensionId(), dimension);
            }
            dimension.put(node.getPos().asLong(), node);
        }
    }

    @Test
    void clearingBindingsSplitsTheGridAndPreservesOrdinaryIncomingConnections() throws Exception {
        LongDistanceRelayNode a = relay(0);
        LongDistanceRelayNode b = relay(1000);
        LongDistanceRelayNode c = relay(2000);
        Node ordinary = new Node(NodeTypes.RELAY_NODE, tag(8, false));
        registerTopology(a, b, c, ordinary);
        pair(a, b);
        pair(a, c);
        a.addNeighbor(b);
        b.addNeighbor(a);
        a.addNeighbor(c);
        c.addNeighbor(a);
        ordinary.addNeighbor(a);
        UUID identity = a.getRelayId();

        assertEquals(2, NetworkManager.INSTANCE.clearLongDistanceRelayLinks(a));
        for (LongDistanceRelayNode relay : new LongDistanceRelayNode[] {a, b, c}) {
            assertTrue(relay.isActive());
            assertTrue(relay.getPeerPositions().isEmpty());
            assertTrue(relay.getNeighbors().isEmpty());
            assertTrue(relay.hasFreeConnection());
            assertTrue(new LongDistanceRelayNode(relay.serialize()).getPeerPositions().isEmpty());
        }
        assertEquals(identity, a.getRelayId());
        assertTrue(ordinary.getNeighbors().contains(a));
        assertSame(a.getGrid(), ordinary.getGrid());
        assertNotSame(a.getGrid(), b.getGrid());
        assertNotSame(a.getGrid(), c.getGrid());
        assertNotSame(b.getGrid(), c.getGrid());
    }

    @Test
    void clearingBindingsKeepsAnAlternateOrdinaryPathConnected() throws Exception {
        LongDistanceRelayNode a = relay(0);
        LongDistanceRelayNode b = relay(10);
        Node ordinary = new Node(NodeTypes.RELAY_NODE, tag(5, false));
        registerTopology(a, b, ordinary);
        pair(a, b);
        a.addNeighbor(b);
        b.addNeighbor(a);
        ordinary.addNeighbor(a);
        ordinary.addNeighbor(b);
        IGrid grid = a.getGrid();

        assertEquals(1, NetworkManager.INSTANCE.clearLongDistanceRelayLinks(a));
        assertSame(grid, a.getGrid());
        assertSame(grid, b.getGrid());
        assertSame(grid, ordinary.getGrid());
        assertEquals(2, ordinary.getNeighbors().size());
        assertFalse(a.canLinkTo(b));
        assertFalse(b.canLinkTo(a));
    }

    @Test
    void clearingMissingPeersCancelsPendingSelectionsAndKeepsTheNodeUsable() throws Exception {
        LongDistanceRelayNode a = relay(0);
        registerTopology(a);
        a.rememberPeer(relay(3000));
        Object player = new Object();
        Object otherPlayer = new Object();
        String prefix = "message.circulation_networks.long_distance_relay.";
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(player, a));
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(otherPlayer, a));

        assertEquals(prefix + "cleared", LongDistanceRelayInteraction.clear(player, a));
        assertTrue(a.getPeerPositions().isEmpty());
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(player, a));
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(otherPlayer, a));
        assertEquals(prefix + "cleared", LongDistanceRelayInteraction.clear(player, a));
        assertTrue(a.isActive());
    }

    @Test
    void clearingAnUnavailableNodeDoesNotModifyBindings() {
        LongDistanceRelayNode a = relay(0);
        LongDistanceRelayNode b = relay(1000);
        pair(a, b);
        assertEquals(-1, NetworkManager.INSTANCE.clearLongDistanceRelayLinks(a));
        assertEquals(-1, NetworkManager.INSTANCE.clearLongDistanceRelayLinks(null));
        assertTrue(a.canLinkTo(b));
        assertTrue(b.canLinkTo(a));
    }

    @Test
    void eachTwoClickRoundEndsAndClickingTheOriginCancels() throws Exception {
        LongDistanceRelayNode a = relay(0);
        LongDistanceRelayNode b = new LongDistanceRelayNode(tag(1000, true));
        Field active = Node.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(a, true);
        active.setBoolean(b, true);
        Object player = new Object();
        String prefix = "message.circulation_networks.long_distance_relay.";
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(player, a));
        assertEquals(prefix + "different_dimension", LongDistanceRelayInteraction.select(player, b));
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(player, a));
        assertEquals(prefix + "cancelled", LongDistanceRelayInteraction.select(player, a));
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(player, b));
        assertEquals(prefix + "cancelled", LongDistanceRelayInteraction.select(player, b));
    }

    @Test
    void unavailableSecondClickEndsTheSelection() throws Exception {
        LongDistanceRelayNode a = relay(0);
        LongDistanceRelayNode b = relay(1000);
        registerTopology(a, b);
        Object player = new Object();
        String prefix = "message.circulation_networks.long_distance_relay.";
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(player, a));
        assertEquals(prefix + "unavailable", LongDistanceRelayInteraction.select(player, null));
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(player, b));
        assertTrue(a.getPeerPositions().isEmpty());
        assertTrue(b.getPeerPositions().isEmpty());

        assertEquals(prefix + "unavailable", LongDistanceRelayInteraction.select(player, relay(2000)));
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(player, a));
    }

    @Test
    void clearingAnUnboundRelayDoesNotRescanOrDirtyTheGrid() throws Exception {
        LongDistanceRelayNode a = relay(0);
        registerTopology(a);
        IGrid grid = mock(IGrid.class);
        Field nodeGrid = Node.class.getDeclaredField("grid");
        nodeGrid.setAccessible(true);
        nodeGrid.set(a, grid);

        assertEquals(0, NetworkManager.INSTANCE.clearLongDistanceRelayLinks(a));
        verifyNoInteractions(grid);
    }

    @Test
    void serverStopDropsPendingSavesAndSelectionsFromThePreviousWorld() throws Exception {
        LongDistanceRelayNode a = relay(0);
        registerTopology(a);
        NetworkManager manager = NetworkManager.INSTANCE;
        manager.markGridDirty(a.getGrid());
        Object player = new Object();
        String prefix = "message.circulation_networks.long_distance_relay.";
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(player, a));

        manager.onServerStop();
        Field dirty = NetworkManager.class.getDeclaredField("dirtyGrids");
        dirty.setAccessible(true);
        assertTrue(((java.util.Set<?>) dirty.get(manager)).isEmpty());
        assertTrue(manager.getAllGrids().isEmpty());
        assertTrue(manager.getActiveNodes().isEmpty());
        assertEquals(prefix + "start", LongDistanceRelayInteraction.select(player, a));
    }
}
