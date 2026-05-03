package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.IHubNodeBlockEntity;
import com.circulation.circulation_networks.api.hub.IHubPlugin;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.container.ContainerHub;
import com.circulation.circulation_networks.inventory.CFNInternalInventory;
import com.circulation.circulation_networks.inventory.CFNInternalInventoryHost;
import com.circulation.circulation_networks.inventory.CFNInventoryChangeOperation;
import com.circulation.circulation_networks.network.hub.HubCapabilitys;
import com.circulation.circulation_networks.network.hub.HubPluginCapability;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.network.nodes.HubPluginStateTracker;
import com.circulation.circulation_networks.network.nodes.HubPluginSyncSupport;
import com.circulation.circulation_networks.packets.HubPluginSyncRequest;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.CFNMenuTypes;
import com.circulation.circulation_networks.registry.NodeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockEntityHub extends BaseNodeBlockEntity<IHubNode> implements IHubNodeBlockEntity, CFNInternalInventoryHost, MenuProvider {

    private final CFNInternalInventory plugins = new CFNInternalInventory(this, 5, 1).setInputFilter((inventory, slot, itemStack) -> {
        if (!(itemStack.getItem() instanceof IHubPlugin plugin)) {
            return false;
        }

        HubPluginCapability<?> capability = plugin.getCapability();
        if (slot == 0) {
            if (capability != HubCapabilitys.CHANNEL_CAPABILITY) {
                return false;
            }
        } else if (capability == HubCapabilitys.CHANNEL_CAPABILITY) {
            return false;
        }

        return isUniquePluginCapability(inventory, slot, capability);
    });
    private boolean init;
    private transient boolean pendingPluginStateInit;
    private transient boolean initialPluginSyncRequested;

    public BlockEntityHub(@NotNull BlockPos pos, @NotNull BlockState state) {
        super(CFNBlockEntityTypes.HUB, pos, state);
    }

    static boolean isUniquePluginCapability(@NotNull CFNInternalInventory inventory, int slot, @NotNull HubPluginCapability<?> capability) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (i == slot) {
                continue;
            }

            ItemStack existing = inventory.getStackInSlot(i);
            if (existing.isEmpty() || !(existing.getItem() instanceof IHubPlugin plugin)) {
                continue;
            }

            if (plugin.getCapability() == capability) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @NotNull NodeType<? extends IHubNode> getNodeType() {
        return NodeTypes.HUB;
    }

    @Override
    protected void onNodeBound(@NotNull IHubNode node) {
        if (node instanceof HubNode hubNode) {
            hubNode.bindPlugins(getPlugins());
        }
        initializeHubPluginState();
        init = true;
    }

    public CFNInternalInventory getPlugins() {
        return plugins;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (HubPluginSyncSupport.shouldRequestInitialSync(level != null && level.isClientSide(), initialPluginSyncRequested)) {
            initialPluginSyncRequested = true;
            CirculationFlowNetworks.sendToServer(new HubPluginSyncRequest(worldPosition));
        }
    }

    @Override
    protected void saveAdditional(@NotNull ValueOutput output) {
        super.saveAdditional(output);
        output.putChild("plugins", plugins);
    }

    @Override
    protected void loadAdditional(@NotNull ValueInput input) {
        super.loadAdditional(input);
        input.child("plugins").ifPresent(plugins::deserialize);

        if (!init) {
            pendingPluginStateInit = true;
            init = true;
        } else {
            initializeHubPluginState();
        }
    }

    @Override
    protected void onServerValidate() {
        super.onServerValidate();
        if (pendingPluginStateInit) {
            initializeHubPluginState();
            pendingPluginStateInit = false;
        }
    }

    @Override
    protected void onClientValidate() {
        super.onClientValidate();
        if (pendingPluginStateInit) {
            initializeHubPluginState();
            pendingPluginStateInit = false;
        }
    }

    @Override
    public void onChangeInventory(@NotNull CFNInternalInventory inventory, int slot, @NotNull CFNInventoryChangeOperation operation, @NotNull ItemStack oldStack, @NotNull ItemStack newStack) {
        if (init && level != null && !level.isClientSide()) {
            HubPluginStateTracker.saveAllPluginData(getNode(), plugins);
            HubPluginStateTracker.savePluginData(getNode(), oldStack);
            HubPluginStateTracker.syncInventoryChange(getNode(), oldStack, newStack);
            notifyPluginInventoryChanged();
        }
        setChanged();
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.circulation_networks.hub");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        syncNodeAfterNetworkInit();
        return new ContainerHub(CFNMenuTypes.HUB_MENU, containerId, player, getNode());
    }

    public void applyPluginSnapshot(ItemStack[] snapshot) {
        for (int i = 0; i < plugins.getSlots(); i++) {
            ItemStack stack = i < snapshot.length ? snapshot[i] : ItemStack.EMPTY;
            plugins.setStackInSlot(i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        initializeHubPluginState();
        setChanged();
    }

    private void initializeHubPluginState() {
        HubPluginStateTracker.initializeFromInventory(getNode(), plugins);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    @NotNull
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.@NotNull Provider registries) {
        return saveCustomOnly(registries);
    }

    private void notifyPluginInventoryChanged() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, 3);
    }
}
