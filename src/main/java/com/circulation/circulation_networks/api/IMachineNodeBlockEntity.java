package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.IMachineNode;
import org.jetbrains.annotations.NotNull;

public interface IMachineNodeBlockEntity extends INodeBlockEntity {

    @NotNull
    IMachineNode getNode();

    /**
     * 返回此方块实体持有的能量处理器。
     * <p>实现类需要保证 {@link IEnergyHandler#clear()} 能重置当前状态。</p>
     */
    @NotNull
    IEnergyHandler getEnergyHandler();
}
