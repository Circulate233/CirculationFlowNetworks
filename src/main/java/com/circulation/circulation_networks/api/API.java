package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.registry.RegistryNodes;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Set;

/**
 * 仅作为一个API方法汇总
 * 这个类的方法不应该被重命名
 */
@SuppressWarnings("unused")
public final class API {

    /**
     * @param world 节点所在的世界
     * @param pos   节点位置
     * @return 可能存在的节点
     * 可以捕获到未被加载的区块的节点
     */
    @Nullable
    public static INode getNodeAt(@Nonnull World world, @Nonnull BlockPos pos) {
        return NetworkManager.INSTANCE.getNodeFromPos(world, pos);
    }

    /**
     * @return 全部可用状态的网络
     */
    @Nonnull
    public static Collection<IGrid> getAllGrids() {
        return NetworkManager.INSTANCE.getAllGrids();
    }

    /**
     * @param world 节点所在的世界
     * @param pos   被检查的位置
     * @return 可能可以链接这个位置上节点的所有节点
     */
    @Nonnull
    public static ReferenceSet<INode> getNodesCoveringPos(@Nonnull World world, @Nonnull BlockPos pos) {
        return NetworkManager.INSTANCE.getNodesCoveringPosition(world, pos);
    }

    /**
     * @param pos 目标位置
     * @return 可能为此位置的机器供能的所有节点
     */
    @Nonnull
    public static ReferenceSet<IEnergySupplyNode> getEnergyNodes(@Nonnull World world, @Nonnull BlockPos pos) {
        return EnergyMachineManager.INSTANCE.getEnergyNodes(world, pos);
    }

    /**
     * @param pos 目标位置
     * @return 可能为此位置的机器供能的所有节点
     */
    @Nonnull
    public static ReferenceSet<IEnergySupplyNode> getEnergyNodes(@Nonnull World world, @Nonnull ChunkPos pos) {
        return EnergyMachineManager.INSTANCE.getEnergyNodes(world, pos);
    }

    /**
     * @param world 节点所在的世界
     * @param pos   被检查的区块
     * @return 可能可以链接这个区块中节点的所有节点
     */
    @Nonnull
    public static ReferenceSet<INode> getNodesCoveringChunk(@Nonnull World world, @Nonnull ChunkPos pos) {
        return NetworkManager.INSTANCE.getNodesCoveringPosition(world, pos);
    }

    /**
     * @param world 节点所在的世界
     * @param chunk 节点所在的区块
     * @return 区块中所有的生效节点
     */
    @Nonnull
    public static ReferenceSet<INode> getNodesInChunk(@Nonnull World world, @Nonnull ChunkPos chunk) {
        return NetworkManager.INSTANCE.getNodesInChunk(world, chunk);
    }

    /**
     * 注册自定义的能量管理器
     * 只允许在postinit阶段前进行注册
     */
    public static void registerEnergyHandler(@Nonnull IEnergyHandlerManager manager) {
        RegistryEnergyHandler.registerEnergyHandler(manager);
    }

    /**
     * @param nodeClass 节点的class
     * @param function  从NBT中反序列化回节点的方法
     */
    public static void registerNode(@Nonnull Class<? extends INode> nodeClass, @Nonnull RegistryNodes.DeserializationNode function) {
        RegistryNodes.register(nodeClass, function);
    }

    /**
     * @param node 节点，不应该是机器节点
     * @return 节点所供能的所有设备
     * 注意：返回的设备可能包含带有IMachineNode的设备
     */
    @Nonnull
    public Set<TileEntity> getMachinesSuppliedBy(IEnergySupplyNode node) {
        return EnergyMachineManager.INSTANCE.getMachinesSuppliedBy(node);
    }


}