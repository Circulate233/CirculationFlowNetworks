package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.nbt.CompoundTag;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface IGrid {

    UUID getId();

    ReferenceSet<INode> getNodes();

    CompoundTag serialize();

    /**
     * 获取此网络的中枢节点 / Get the hub node of this network
     *
     * @return 中枢节点，不存在时返回 null
     */
    @Nullable
    default IHubNode getHubNode() {
        return null;
    }

    /**
     * 设置此网络的中枢节点 / Set the hub node of this network
     */
    default void setHubNode(@Nullable IHubNode hub) {
    }

    /**
     * 返回当前网络节点快照版本，用于 GUI 按脏状态决定是否需要重建同步数据。
     */
    default long getSnapshotVersion() {
        return 0L;
    }

    /**
     * 标记当前网络节点快照已变化。
     */
    default void markSnapshotDirty() {
    }

    /**
     * 运行期能量管线的每 tick 临时数据槽。挂在网格对象上是为了让服务端 tick 热路径按引用直接取用，
     * 绕开按网格身份哈希的 map 查找。不参与序列化。
     */
    @Nullable
    default EnergyMachineManager.GridTickData getEnergyTickData() {
        return null;
    }

    default void setEnergyTickData(@Nullable EnergyMachineManager.GridTickData data) {
    }
}
