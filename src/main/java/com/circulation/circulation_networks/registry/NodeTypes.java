package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.api.NodeCreator;
import com.circulation.circulation_networks.api.NodeDeserializer;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.network.nodes.ChargingNode;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.network.nodes.Node;
import com.circulation.circulation_networks.network.nodes.PortNode;
import com.circulation.circulation_networks.network.nodes.machine_node.ConsumerNode;
import com.circulation.circulation_networks.network.nodes.machine_node.GeneratorNode;
import com.circulation.circulation_networks.network.nodes.machine_node.StorageNode;
import com.circulation.circulation_networks.utils.NbtCompat;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NodeTypes {

    private static final Object2ReferenceMap<String, NodeType<?>> TYPES_BY_ID = new Object2ReferenceOpenHashMap<>();
    private static final Object2ReferenceMap<String, NodeDeserializer> DESERIALIZERS_BY_TYPE_ID = new Object2ReferenceOpenHashMap<>();
    private static final Object2ReferenceMap<String, NodeCreator> CREATORS_BY_TYPE_ID = new Object2ReferenceOpenHashMap<>();

    private NodeTypes() {
    }

    private static <N extends INode> NodeType<N> type(String id, Class<N> nodeClass, boolean allowsPocketNode, NodeDeserializer deserializer, @Nullable NodeCreator creator) {
        var nodeType = new SimpleNodeType<>(id, nodeClass, allowsPocketNode);
        register(nodeType, deserializer, creator);
        return nodeType;
    }

    public static @Nullable NodeType<?> getById(@Nullable String id) {
        return id == null ? null : TYPES_BY_ID.get(id);
    }

    public static <N extends INode> @NotNull NodeType<N> register(@NotNull NodeType<N> nodeType, @NotNull NodeDeserializer deserializer, @Nullable NodeCreator creator) {
        String typeId = nodeType.id();
        NodeType<?> existingType = TYPES_BY_ID.get(typeId);
        if (existingType != null && existingType != nodeType) {
            throw new IllegalStateException("Duplicate node type id registration: " + typeId);
        }
        TYPES_BY_ID.put(typeId, nodeType);
        registerTypeDeserializer(typeId, deserializer);
        if (creator != null) {
            registerTypeCreator(typeId, creator);
        }
        return nodeType;
    }

    public static @Nullable NodeCreator getCreator(@NotNull String typeId) {
        return CREATORS_BY_TYPE_ID.get(typeId);
    }    public static final NodeType<PortNode> PORT_NODE = type("port_node", PortNode.class, true, PortNode::new,
        ctx -> new PortNode(ctx, CFNConfig.NODE.portNode.energyScope, CFNConfig.NODE.portNode.linkScope));

    public static @Nullable INode deserialize(@Nullable CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        if (!tag.contains("type")) {
            return null;
        }
        if (!tag.contains("dim") || !isRegisteredDimension(NbtCompat.getStringOr(tag, "dim", ""))) {
            return null;
        }

        NodeDeserializer deserializer = DESERIALIZERS_BY_TYPE_ID.get(NbtCompat.getStringOr(tag, "type", ""));
        return deserializer == null ? null : deserializer.apply(tag);
    }

    private static void registerTypeDeserializer(String typeId, NodeDeserializer deserializer) {
        NodeDeserializer existing = DESERIALIZERS_BY_TYPE_ID.get(typeId);
        if (existing != null && existing != deserializer) {
            throw new IllegalStateException("Duplicate node type deserializer registration: " + typeId);
        }
        DESERIALIZERS_BY_TYPE_ID.put(typeId, deserializer);
    }

    private static void registerTypeCreator(String typeId, NodeCreator creator) {
        NodeCreator existing = CREATORS_BY_TYPE_ID.get(typeId);
        if (existing != null && existing != creator) {
            throw new IllegalStateException("Duplicate node type creator registration: " + typeId);
        }
        CREATORS_BY_TYPE_ID.put(typeId, creator);
    }

    private static boolean isRegisteredDimension(String dimKey) {
        return WorldResolveCompat.isRegisteredDimension(dimKey);
    }

    private record SimpleNodeType<N extends INode>(String id, Class<N> nodeClass,
                                                   boolean allowsPocketNode) implements NodeType<N> {
    }    public static final NodeType<ChargingNode> CHARGING_NODE = type("charging_node", ChargingNode.class, true, ChargingNode::new,
        ctx -> new ChargingNode(ctx, CFNConfig.NODE.chargingNode.chargingScope, CFNConfig.NODE.chargingNode.linkScope));






    public static final NodeType<Node> RELAY_NODE = type("relay_node", Node.class, true, Node::new,
        ctx -> new Node(NodeTypes.RELAY_NODE, ctx, CFNConfig.NODE.relayNode.linkScope));


    public static final NodeType<GeneratorNode> GENERATOR = type("generator", GeneratorNode.class, false, GeneratorNode::new, null);


    public static final NodeType<StorageNode> STORAGE = type("storage", StorageNode.class, false, StorageNode::new, null);


    public static final NodeType<ConsumerNode> CONSUMER = type("consumer", ConsumerNode.class, false, ConsumerNode::new, null);


    public static final NodeType<HubNode> HUB = type("hub", HubNode.class, false, HubNode::new,
        ctx -> new HubNode(ctx, CFNConfig.NODE.hub.energyScope, CFNConfig.NODE.hub.chargingScope, CFNConfig.NODE.hub.linkScope));


}
