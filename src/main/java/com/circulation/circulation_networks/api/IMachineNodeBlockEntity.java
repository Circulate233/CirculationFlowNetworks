package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.api.node.IMachineNode;
import org.jetbrains.annotations.NotNull;

public interface IMachineNodeBlockEntity extends INodeBlockEntity<IMachineNode> {

    /**
     * 返回此方块实体持有的能量处理器。
     * <p>实现类 <b>必须</b> 重写 {@link IEnergyHandler#recycle()}，
     * 使其成为空操作以防止对象被回收到池中。</p>
     */
    @NotNull
    IEnergyHandler getEnergyHandler();
}