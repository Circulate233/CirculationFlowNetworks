package com.circulation.circulation_networks.registry;

import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.network.nodes.ChargingNode;
import com.circulation.circulation_networks.network.nodes.InductionNode;
import com.circulation.circulation_networks.network.nodes.machine_node.ConsumerNode;
import com.circulation.circulation_networks.network.nodes.machine_node.GeneratorNode;
import com.circulation.circulation_networks.network.nodes.machine_node.StorageNode;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nonnull;
import java.util.function.Function;

public final class RegistryNodes {

    private static final Object2ReferenceMap<String, DeserializationNode> map = new Object2ReferenceOpenHashMap<>();

    static {
        register(InductionNode.class, InductionNode::new);
        register(ChargingNode.class, ChargingNode::new);
        register(GeneratorNode.class, GeneratorNode::new);
        register(StorageNode.class, StorageNode::new);
        register(ConsumerNode.class, ConsumerNode::new);
    }

    public static void register(Class<? extends INode> nodeClass, DeserializationNode function) {
        map.put(nodeClass.getName(), function);
    }

    public static INode deserialize(NBTTagCompound tag) {
        if (tag != null && tag.hasKey("name") && tag.hasKey("dim") && DimensionManager.isDimensionRegistered(tag.getInteger("dim"))) {
            var d = map.get(tag.getString("name"));
            if (d != null) {
                return d.apply(tag);
            }
        }
        return null;
    }

    @FunctionalInterface
    public interface DeserializationNode extends Function<NBTTagCompound, INode> {

        @Override
        @Nonnull
        INode apply(NBTTagCompound tag);

    }
}