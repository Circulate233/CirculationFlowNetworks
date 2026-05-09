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

    EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata);

    enum EnergyType {
        SEND,
        RECEIVE,
        STORAGE,
        INVALID
    }
}

