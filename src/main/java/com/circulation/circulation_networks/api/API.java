package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.handlers.PocketNodeRenderingHandler;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeType;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.HubChannelManager;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.registry.NodeTypes;
import com.circulation.circulation_networks.registry.PocketNodeItems;
import static com.circulation.circulation_networks.utils.WorldSideCompat.isClientWorld;
//~ mc_imports
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * 仅作为一个API方法汇总。
 * 这个类的方法不应该被重命名。
 * <p>
 * A collection of public-facing API methods.
 * Methods of this class should not be renamed.
 */
@SuppressWarnings("unused")
public final class API {

    //~ if >=1.20 ' World ' -> ' Level ' {
    //~ if >=1.20 ' TileEntity ' -> ' BlockEntity ' {
    //~ if >=1.20 '<TileEntity>' -> '<BlockEntity>' {

    // -------------------------------------------------------------------------
    // 节点查询 / Node queries
    // -------------------------------------------------------------------------

    /**
     * 根据坐标获取节点。可以获取到未被加载区块内的节点。
     * <p>
     * Returns the node at the given position. Works even for nodes in unloaded chunks.
     *
     * @param world 节点所在的世界 / the world the node resides in
     * @param pos   节点位置 / the position to look up
     * @return 该位置的节点，若不存在则为 {@code null} / the node at that position, or {@code null} if absent
     */
    @Nullable
    public static INode getNodeAt(@NotNull World world, @NotNull BlockPos pos) {
        if (isClientWorld(world)) {
            INode clientNode = getClientNodeAt(world, pos);
            if (clientNode != null) {
                return clientNode;
            }
        }
        return NetworkManager.INSTANCE.getNodeFromPos(world, pos);
    }

    @Nullable
    private static INode getClientNodeAt(@NotNull World world, @NotNull BlockPos pos) {
        //~ if >=1.20 'world.getTileEntity(pos)' -> 'world.getBlockEntity(pos)' {
        var blockEntity = world.getTileEntity(pos);
        //~}
        if (blockEntity instanceof INodeBlockEntity nodeBlockEntity) {
            return nodeBlockEntity.getNode();
        }
        return PocketNodeRenderingHandler.INSTANCE.getNode(world, pos);
    }

    /**
     * 返回当前所有处于活跃状态的节点。
     * <p>
     * Returns all currently active nodes.
     *
     * @return 全部活跃节点 / all active nodes
     */
    @NotNull
    public static ReferenceSet<INode> getAllNodes() {
        return NetworkManager.INSTANCE.getActiveNodes();
    }

    /**
     * 返回当前所有处于可用状态的网格。
     * <p>
     * Returns all currently active grids.
     *
     * @return 全部活跃网格 / all active grids
     */
    @NotNull
    public static Collection<IGrid> getAllGrids() {
        return NetworkManager.INSTANCE.getAllGrids();
    }

    /**
     * 获取链接范围覆盖指定位置所在区块的所有节点。
     * <p>
     * Returns all nodes whose link scope covers the chunk that contains the given position.
     *
     * @param world 节点所在的世界 / the world to search in
     * @param pos   被检查的位置 / the position to check
     * @return 可能可以链接该位置上节点的所有节点 / all nodes that may link to nodes at that position
     */
    @NotNull
    public static ReferenceSet<INode> getNodesCoveringPos(@NotNull World world, @NotNull BlockPos pos) {
        return NetworkManager.INSTANCE.getNodesCoveringPosition(world, pos);
    }

    /**
     * 获取链接范围覆盖指定区块的所有节点。
     * <p>
     * Returns all nodes whose link scope covers the given chunk.
     *
     * @param world  节点所在的世界 / the world to search in
     * @param chunkX 被检查的区块 X 坐标 / the chunk X coordinate
     * @param chunkZ 被检查的区块 Z 坐标 / the chunk Z coordinate
     * @return 可能可以链接该区块中节点的所有节点 / all nodes that may link to nodes in that chunk
     */
    @NotNull
    public static ReferenceSet<INode> getNodesCoveringChunk(@NotNull World world, int chunkX, int chunkZ) {
        return NetworkManager.INSTANCE.getNodesCoveringPosition(world, chunkX, chunkZ);
    }

    /**
     * 返回位于指定区块内的所有节点。
     * <p>
     * Returns all active nodes located inside the given chunk.
     *
     * @param world  节点所在的世界 / the world to search in
     * @param chunkX 节点所在区块的 X 坐标 / the chunk X coordinate
     * @param chunkZ 节点所在区块的 Z 坐标 / the chunk Z coordinate
     * @return 区块中所有的生效节点 / all active nodes inside that chunk
     */
    @NotNull
    public static ReferenceSet<INode> getNodesInChunk(@NotNull World world, int chunkX, int chunkZ) {
        return NetworkManager.INSTANCE.getNodesInChunk(world, chunkX, chunkZ);
    }

    // -------------------------------------------------------------------------
    // 能量节点 / Energy supply nodes
    // -------------------------------------------------------------------------

    /**
     * 返回供能范围覆盖指定位置所在区块的所有能量供应节点。
     * <p>
     * Returns all energy supply nodes whose energy scope covers the chunk containing the given position.
     *
     * @param world 目标世界 / the world to search in
     * @param pos   目标位置 / the position to query
     * @return 可能为此位置的机器供能的所有节点 / all nodes that may supply energy to machines at that position
     */
    @NotNull
    public static ReferenceSet<IEnergySupplyNode> getEnergyNodes(@NotNull World world, @NotNull BlockPos pos) {
        return EnergyMachineManager.INSTANCE.getEnergyNodes(world, pos);
    }

    /**
     * 返回供能范围覆盖指定区块的所有能量供应节点。
     * <p>
     * Returns all energy supply nodes whose energy scope covers the given chunk.
     *
     * @param world  目标世界 / the world to search in
     * @param chunkX 目标区块的 X 坐标 / the chunk X coordinate
     * @param chunkZ 目标区块的 Z 坐标 / the chunk Z coordinate
     * @return 可能为此区块供能的所有节点 / all nodes that may supply energy to machines in that chunk
     */
    @NotNull
    public static ReferenceSet<IEnergySupplyNode> getEnergyNodes(@NotNull World world, int chunkX, int chunkZ) {
        return EnergyMachineManager.INSTANCE.getEnergyNodes(world, chunkX, chunkZ);
    }

    /**
     * 返回供能范围覆盖指定区块的所有能量供应节点。
     * <p>
     * Returns all energy supply nodes whose energy scope covers the given chunk.
     *
     * @param world 目标世界 / the world to search in
     * @param pos   目标区块 / the chunk to query
     * @return 可能为此区块的机器供能的所有节点 / all nodes that may supply energy to machines in that chunk
     * @deprecated 使用 chunk 坐标或 BlockPos 重载代替
     * / use the chunk-coordinate or BlockPos overload instead
     */
    @NotNull
    @Deprecated
    public static ReferenceSet<IEnergySupplyNode> getEnergyNodes(@NotNull World world, @NotNull ChunkPos pos) {
        return EnergyMachineManager.INSTANCE.getEnergyNodes(world, pos.x, pos.z);
    }

    /**
     * 返回指定能量供应节点当前所链接的所有设备。
     * 注意：返回的设备可能包含带有 {@code IMachineNode} 的实体。
     * <p>
     * Returns all machines currently supplied by the given energy supply node.
     * Note: the returned set may include block entities that also carry an {@code IMachineNode}.
     *
     * @param node 节点，不应该是 {@code IMachineNode} / the supply node (should not be an IMachineNode)
     * @return 节点所供能的所有设备 / all machines supplied by this node
     */
    @NotNull
    public static Set<TileEntity> getMachinesSuppliedBy(@NotNull IEnergySupplyNode node) {
        return EnergyMachineManager.INSTANCE.getMachinesSuppliedBy(node);
    }

    // -------------------------------------------------------------------------
    // 中枢频道 / Hub channels
    // -------------------------------------------------------------------------

    /**
     * 返回指定频道 UUID 所关联的所有网格。
     * 中枢节点（{@code IHubNode}）通过相同的频道 UUID 跨网格共享能量。
     * <p>
     * Returns all grids associated with the given hub channel UUID.
     * Hub nodes ({@code IHubNode}) share energy across grids that share the same channel UUID.
     *
     * <p><b>仅服务端可用 / Server-side only.</b> 在客户端调用会导致 NPE。
     *
     * @param channelId 频道 UUID / the channel UUID
     * @return 属于该频道的所有网格，若频道不存在则为 {@code null}
     * / all grids in this channel, or {@code null} if the channel does not exist
     */
    @NotNull
    public static ReferenceSet<IGrid> getChannelGrids(@NotNull UUID channelId) {
        return HubChannelManager.INSTANCE.getChannelGrids(channelId);
    }

    // -------------------------------------------------------------------------
    // 能量类型判断 / Energy type checks
    // -------------------------------------------------------------------------

    /**
     * 判断指定方块实体是否位于能源全局黑名单中。
     * 黑名单中的方块实体不会被任何节点识别为能源容器。
     * <p>
     * Returns whether the given block entity is on the global energy blacklist.
     * Blacklisted block entities are never recognized as energy containers by any node.
     * <p>
     * Legacy method name retained for compatibility; on modern versions this applies to block entities.
     *
     * @param blockEntity 目标方块实体 / the block entity to check
     * @return 是否在黑名单中 / {@code true} if blacklisted
     */
    public static boolean isEnergyBlacklisted(@NotNull TileEntity blockEntity) {
        return RegistryEnergyHandler.isBlack(blockEntity);
    }

    /**
     * 判断指定方块实体是否位于通用供应节点黑名单中。
     * 黑名单中的方块实体只能由覆写了 {@code isBlacklisted} 的专用节点建立连接，普通供应节点无法连接。
     * <p>
     * Returns whether the given block entity is on the supply-node blacklist.
     * Blacklisted block entities can only be connected by specialized nodes that override {@code isBlacklisted};
     * generic supply nodes cannot connect to them.
     * <p>
     * Legacy method name retained for compatibility; on modern versions this applies to block entities.
     *
     * @param blockEntity 目标方块实体 / the block entity to check
     * @return 是否在供应黑名单中 / {@code true} if on the supply blacklist
     */
    public static boolean isSupplyBlacklisted(@NotNull TileEntity blockEntity) {
        return RegistryEnergyHandler.isSupplyBlack(blockEntity);
    }

    /**
     * 判断指定物品堆是否为受能量处理器管理的能源物品。
     * <p>
     * Returns whether the given item stack is an energy item handled by a registered energy manager.
     *
     * @param stack 目标物品堆 / the item stack to check
     * @return 是否为能源物品 / {@code true} if the item is an energy item
     */
    public static boolean isEnergyItem(@NotNull ItemStack stack) {
        return RegistryEnergyHandler.isEnergyItemStack(stack);
    }

    /**
     * 判断指定方块实体是否为受能量处理器管理的能源容器。
     * <p>
     * Returns whether the given block entity is an energy container handled by a registered energy manager.
     * Legacy method name retained for compatibility; on modern versions this applies to block entities.
     *
     * @param blockEntity 目标方块实体 / the block entity to check
     * @return 是否为能源容器 / {@code true} if the block entity is an energy container
     */
    public static boolean isEnergyTileEntity(@NotNull TileEntity blockEntity) {
        return RegistryEnergyHandler.isEnergyTileEntity(blockEntity);
    }

    /**
     * 获取适用于指定方块实体的能量处理器管理器。
     * <p>
     * Returns the energy handler manager compatible with the given block entity,
     * or {@code null} if no registered manager applies.
     * <p>
     * Legacy method name retained for compatibility; on modern versions this applies to block entities.
     *
     * @param blockEntity 目标方块实体 / the block entity to query
     * @return 匹配的能量管理器，若无匹配则为 {@code null} / a matching manager, or {@code null} if none applies
     */
    @Nullable
    public static IEnergyHandlerManager getEnergyManager(@NotNull TileEntity blockEntity) {
        return RegistryEnergyHandler.getEnergyManager(blockEntity);
    }

    /**
     * 获取适用于指定物品堆的能量处理器管理器。
     * <p>
     * Returns the energy handler manager compatible with the given item stack,
     * or {@code null} if no registered manager applies.
     *
     * @param stack 目标物品堆 / the item stack to query
     * @return 匹配的能量管理器，若无匹配则为 {@code null} / a matching manager, or {@code null} if none applies
     */
    @Nullable
    public static IEnergyHandlerManager getEnergyManager(@NotNull ItemStack stack) {
        return RegistryEnergyHandler.getEnergyManager(stack);
    }

    // -------------------------------------------------------------------------
    // 注册 / Registration
    // -------------------------------------------------------------------------

    /**
     * 注册自定义的能量管理器。
     * 必须在 {@link RegistryEnergyHandler#lock()} 前完成注册。
     * 1.12.2 中对应 {@code postInit} 前，1.20.1 / 1.21.1 中对应 {@code FMLLoadCompleteEvent} 前。
     * <p>
     * Registers a custom energy handler manager.
     * Registration must complete before {@link RegistryEnergyHandler#lock()}.
     * That means before {@code postInit} on 1.12.2, and before {@code FMLLoadCompleteEvent} on 1.20.1 / 1.21.1.
     *
     * @param manager 要注册的能量管理器 / the manager to register
     */
    public static void registerEnergyHandler(@NotNull IEnergyHandlerManager manager) {
        RegistryEnergyHandler.registerEnergyHandler(manager);
    }

    /**
     * 注册自定义节点类型及其 NBT 反序列化函数和运行时创建函数。
     * <p>
     * Registers a custom node type together with its NBT deserialization function and runtime creator.
     *
     * @param nodeType 节点类型 / the node type to register
     * @param function 从 NBT 中反序列化回节点的方法 / function to deserialize a node from NBT
     * @param creator  运行时创建节点的方法 / function to create a new node at runtime
     */
    public static void registerNodeType(@NotNull NodeType<? extends INode> nodeType, @NotNull NodeDeserializer function, @NotNull NodeCreator creator) {
        NodeTypes.register(nodeType, function, creator);
    }

    /**
     * 注册自定义口袋节点物品映射。
     * 该映射用于口袋节点掉落、HUD 展示和客户端渲染堆栈回退。
     * <p>
     * Registers a custom pocket-node item mapping.
     * This mapping is used for pocket-node drops, HUD display, and client render-stack fallback.
     *
     * @param nodeType 节点类型 / the node type
     * @param item     对应的口袋节点物品 / the pocket-node item for that type
     */
    public static void registerPocketNodeItem(@NotNull NodeType<? extends INode> nodeType, @NotNull Item item) {
        PocketNodeItems.register(nodeType, item);
    }
    //~}
    //~}
    //~}
}
