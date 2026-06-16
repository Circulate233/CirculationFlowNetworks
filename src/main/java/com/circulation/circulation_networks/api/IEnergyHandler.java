package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public interface IEnergyHandler {

    static @org.jetbrains.annotations.Nullable IEnergyHandler release(ItemStack stack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (stack == null || stack.isEmpty()) return null;
        var m = RegistryEnergyHandler.getEnergyManager(stack);
        if (m == null) return null;
        return m.newItemInstance().init(stack, hubMetadata);
    }

    IEnergyHandler init(BlockEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata);

    IEnergyHandler init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata);

    void clear();

    EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata);

    EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata);

    EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata);

    EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata);

    boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata);

    boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata);

    /**
     * 是否需要对每一对 sender/receiver 精确调用 {@link #canExtract}/{@link #canReceive} 进行配对判定。
     *
     * <p>默认返回 {@code false}：传输逻辑会跳过逐对的 canExtract/canReceive 调用以节省开销。绝大多数
     * handler 的可收发性只取决于自身状态、与配对无关（等价于 {@link #canExtractValue}/
     * {@link #canReceiveValue} 是否大于 0），由传输逻辑已有的零值判定覆盖，无需逐对判定。</p>
     *
     * <p>若某个 handler 确实需要按 sender/receiver 配对做精确匹配（canExtract/canReceive 会用到对端
     * handler 参数），应覆盖此方法返回 {@code true}，传输时便会对涉及它的每一对调用 canExtract/canReceive。</p>
     */
    default boolean requiresPairMatch(@Nullable HubNode.HubMetadata hubMetadata) {
        return false;
    }

    EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata);

    enum EnergyType {
        SEND,
        RECEIVE,
        STORAGE,
        INVALID
    }
}

