package com.circulation.circulation_networks.api;

import com.circulation.circulation_networks.manager.HandlerBindingPolicy;
import com.circulation.circulation_networks.manager.HandlerInvalidationSink;
import com.circulation.circulation_networks.registry.RegistryEnergyHandler;
import com.circulation.circulation_networks.network.nodes.HubNode;
//~ mc_imports
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public interface IEnergyHandler {

    /**
     * Creates and binds an item handler. The caller owns the returned handler
     * and must invoke {@link #unbindItem()} after its transfer pass completes.
     *
     * @param stack item stack that supplies the capability
     * @param hubMetadata optional hub configuration visible to the handler
     * @return a bound handler, or {@code null} when no manager accepts the stack
     */
    static @Nullable IEnergyHandler release(ItemStack stack, @Nullable HubNode.HubMetadata hubMetadata) {
        if (stack == null || stack.isEmpty()) return null;
        var m = RegistryEnergyHandler.getEnergyManager(stack);
        if (m == null) return null;
        var t = m.newItemInstance();
        t.bindItem(stack, hubMetadata);
        return t;
    }

    /**
     * Declares how the machine binding index owns this handler's block-entity
     * binding. The policy is fixed for the handler instance lifetime.
     *
     * @return the lifecycle policy used by {@code MachineBindingIndex}
     */
    HandlerBindingPolicy bindingPolicy();

    /**
     * Binds this handler to one block entity. The index supplies an
     * invalidation sink so capability listeners can revoke the binding without
     * polling or retaining a stale block entity reference.
     *
     * @param blockEntity bound energy block entity
     * @param invalidationSink callback to invoke when the binding becomes invalid
     */
    //~ if >=1.20 '(TileEntity ' -> '(BlockEntity ' {
    void bindBlockEntity(TileEntity blockEntity, HandlerInvalidationSink invalidationSink);
    //~}

    /**
     * Starts one server-tick interaction window for a bound block entity.
     * Implementations refresh volatile capability state here and must not
     * allocate a replacement binding.
     *
     * @param epoch monotonically increasing server-tick epoch
     */
    HandlerTickResult beginServerTick(long epoch);

    /**
     * Ends the current server-tick interaction window. This clears only
     * tick-local state; persistent block-entity references remain valid until
     * {@link #unbindBlockEntity()} is invoked.
     *
     * @param epoch monotonically increasing server-tick epoch
     */
    void endServerTick(long epoch);

    /**
     * Releases the current block-entity binding and every resource acquired by
     * {@link #bindBlockEntity(TileEntity, HandlerInvalidationSink)}.
     */
    void unbindBlockEntity();

    /**
     * Binds this handler to an item stack for a bounded item-transfer pass.
     * The owner must call {@link #unbindItem()} after the pass completes.
     *
     * @param itemStack bound item stack
     * @param hubMetadata optional hub configuration
     */
    void bindItem(ItemStack itemStack, @Nullable HubNode.HubMetadata hubMetadata);

    /**
     * Releases the item-stack binding created by {@link #bindItem(ItemStack, HubNode.HubMetadata)}.
     */
    void unbindItem();

    /**
     * Inserts energy into the currently bound endpoint.
     * A handler may accept less than {@code maxReceive}, including zero even
     * after reporting a positive receive budget. With
     * {@link HandlerBindingPolicy.PairMatching#NONE}, a zero result rejects
     * the physical account only for the current machine-transfer invocation;
     * it does not consume the sampled receive budget and may be retried by a
     * later invocation.
     *
     * @param maxReceive upper bound requested by the transfer algorithm
     * @param hubMetadata optional grid-specific hub configuration
     * @return actual accepted amount; never {@code null}
     */
    EnergyAmount receiveEnergy(EnergyAmount maxReceive, @Nullable HubNode.HubMetadata hubMetadata);

    /**
     * Extracts energy from the currently bound endpoint.
     *
     * @param maxExtract upper bound requested by the transfer algorithm
     * @param hubMetadata optional grid-specific hub configuration
     * @return actual extracted amount; never {@code null}
     */
    EnergyAmount extractEnergy(EnergyAmount maxExtract, @Nullable HubNode.HubMetadata hubMetadata);

    /**
     * Samples the endpoint's extraction budget for the active transfer epoch.
     *
     * @param hubMetadata optional grid-specific hub configuration
     * @return extractable amount; never {@code null}
     */
    EnergyAmount canExtractValue(@Nullable HubNode.HubMetadata hubMetadata);

    /**
     * Samples the endpoint's receive budget for the active transfer epoch.
     *
     * @param hubMetadata optional grid-specific hub configuration
     * @return receivable amount; never {@code null}
     */
    EnergyAmount canReceiveValue(@Nullable HubNode.HubMetadata hubMetadata);

    /**
     * Checks sender-side compatibility with a prospective receiver.
     *
     * @param receiveHandler prospective receiving backend
     * @param hubMetadata optional sender-grid hub configuration
     * @return whether this endpoint permits the pair
     */
    boolean canExtract(IEnergyHandler receiveHandler, @Nullable HubNode.HubMetadata hubMetadata);

    /**
     * Checks receiver-side compatibility with a prospective sender.
     *
     * @param sendHandler prospective sending backend
     * @param hubMetadata optional receiver-grid hub configuration
     * @return whether this endpoint permits the pair
     */
    boolean canReceive(IEnergyHandler sendHandler, @Nullable HubNode.HubMetadata hubMetadata);

    /**
     * Resolves the endpoint's current transfer role.
     *
     * @param hubMetadata optional grid-specific hub configuration
     * @return current transfer role; never {@code null}
     */
    EnergyType getType(@Nullable HubNode.HubMetadata hubMetadata);

    /** Transfer roles exposed to the routing indexes. */
    enum EnergyType {
        /** Endpoint may provide energy. */
        SEND,
        /** Endpoint may accept energy. */
        RECEIVE,
        /** Endpoint may both provide and accept energy. */
        STORAGE,
        /** Endpoint must not participate in transfer. */
        INVALID
    }
}
