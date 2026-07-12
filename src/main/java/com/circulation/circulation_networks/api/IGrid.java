package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.manager.EnergyMachineManager;
import com.circulation.circulation_networks.manager.GridParticipantIndex;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
//~ mc_imports
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

@ApiStatus.NonExtendable
public interface IGrid {

    UUID getId();

    ReferenceSet<INode> getNodes();

    //~ if >=1.20 'NBTTagCompound ' -> 'CompoundTag ' {
    NBTTagCompound serialize();
    //~}

    /**
     * 获取此网络的中枢节点 / Get the hub node of this network
     *
     * @return 中枢节点，不存在时返回 null
     */
    @Nullable
    IHubNode getHubNode();

    /**
     * 设置此网络的中枢节点 / Set the hub node of this network
     */
    void setHubNode(@Nullable IHubNode hub);

    /**
     * 返回当前网络节点快照版本，用于 GUI 按脏状态决定是否需要重建同步数据。
     */
    long getSnapshotVersion();

    /**
     * 标记当前网络节点快照已变化。
     */
    void markSnapshotDirty();

    /**
     * 返回此网络的传输统计运行时对象。该对象随 grid 生命周期常驻，不参与序列化。
     */
    EnergyMachineManager.Interaction getInteraction();

    /**
     * 返回此网络常驻的能量参与者索引。该接口只接受持有直接 membership 的
     * participant，机器 tick 迁移完成后由机器传输 slot 使用。
     *
     * @return 不参与序列化且随 grid 生命周期常驻的参与者索引
     */
    GridParticipantIndex getParticipantIndex();
}
