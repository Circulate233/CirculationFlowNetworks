package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
//~ mc_imports
//? if <1.20 {
import com.github.bsideup.jabel.Desugar;
//?}
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.Nullable;

public interface IEnergyHandler {

    static @Nullable IEnergyHandler release(ItemStack stack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (stack == null || stack.isEmpty()) return null;
        var m = RegistryEnergyHandler.getEnergyManager(stack);
        if (m == null) return null;
        var t = m.newItemInstance();
        t.init(stack, hubMetadata);
        return t;
    }

    //? if <1.20
    @Desugar
    //~ if >=1.20 'TileEntity ' -> 'BlockEntity ' {
    record HandlerResolveContext(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata,
    //~}
                                 @Nullable IEnergyHandlerManager manager) {
    }

    //~ if >=1.20 '(TileEntity ' -> '(BlockEntity ' {
    default void asyncInit(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
    }
    //~}

    //~ if >=1.20 '(TileEntity ' -> '(BlockEntity ' {
    default boolean shouldRunAsyncInit(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata) {
    //~}
        return false;
    }

    //~ if >=1.20 '(TileEntity ' -> '(BlockEntity ' {
    void init(TileEntity tileEntity, @Nullable HubNode.HubMetadata hubMetadata);
    //~}

    void init(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata);

    default IEnergyHandler resolveMappedHandler(HandlerResolveContext context) {
        return this;
    }

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
