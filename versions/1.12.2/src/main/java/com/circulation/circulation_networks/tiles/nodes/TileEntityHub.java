package com.circulation.circulation_networks.tiles.nodes;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.IHubNodeBlockEntity;
import com.circulation.circulation_networks.api.hub.IHubPlugin;
import com.circulation.circulation_networks.api.node.IHubNode;
import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.client.render.HubRenderLayout;
import com.circulation.circulation_networks.container.ContainerHub;
import com.circulation.circulation_networks.gui.GuiHub;
import com.circulation.circulation_networks.inventory.CFNInternalInventory;
import com.circulation.circulation_networks.inventory.CFNInternalInventoryHost;
import com.circulation.circulation_networks.inventory.CFNInventoryChangeOperation;
import com.circulation.circulation_networks.network.hub.HubCapabilitys;
import com.circulation.circulation_networks.network.hub.HubPluginCapability;
import com.circulation.circulation_networks.network.nodes.HubNode;
import com.circulation.circulation_networks.network.nodes.HubPluginStateTracker;
import com.circulation.circulation_networks.network.nodes.HubPluginSyncSupport;
import com.circulation.circulation_networks.packets.HubPluginSyncRequest;
import com.circulation.circulation_networks.registry.NodeTypes;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TileEntityHub extends BaseNodeTileEntity<IHubNode> implements IHubNodeBlockEntity, CFNInternalInventoryHost {

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
    private transient NBTTagCompound initNbt;
    private transient boolean initialPluginSyncRequested;

    private static boolean isUniquePluginCapability(@NotNull CFNInternalInventory inventory, int slot, @NotNull HubPluginCapability<?> capability) {
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
    public boolean hasGui() {
        return true;
    }

    @Override
    public @NotNull ContainerHub getContainer(@NotNull EntityPlayer player) {
        return new ContainerHub(player, getNode());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiContainer getGui(@NotNull EntityPlayer player) {
        return new GuiHub(player, getNode());
    }

    @Override
    public @NotNull NodeType<HubNode> getNodeType() {
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
        if (HubPluginSyncSupport.shouldRequestInitialSync(world != null && world.isRemote, initialPluginSyncRequested)) {
            initialPluginSyncRequested = true;
            CirculationFlowNetworks.sendToServer(new HubPluginSyncRequest(pos));
        }
    }

    @Override
    protected void onInvalidate() {
        if (getNode() != null && getNode().getGrid() != null) {
            getNode().getGrid().setHubNode(null);
        }
        super.onInvalidate();
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        super.writeToNBT(compound);
        plugins.writeToNBT(compound, "plugins");
        return compound;
    }

    @Override
    @Nullable
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    @NotNull
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public void onDataPacket(@NotNull NetworkManager net, @NotNull SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    @Override
    public void handleUpdateTag(@NotNull NBTTagCompound tag) {
        readFromNBT(tag);
    }

    @Override
    public final void readFromNBT(@NotNull NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        plugins.readFromNBT(nbt, "plugins");

        if (!init) {
            initNbt = nbt;
            init = true;
        } else {
            initializeHubPluginState();
        }
    }

    protected void onValidate() {
        super.onValidate();
        if (initNbt != null) {
            initializeHubPluginState();
            initNbt = null;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected void onClientValidate() {
        super.onClientValidate();
        if (initNbt != null) {
            initializeHubPluginState();
            initNbt = null;
        }
    }

    @Override
    public @NotNull AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(
            pos.add(HubRenderLayout.renderBoundsMinXZ(), HubRenderLayout.renderBoundsMinY(), HubRenderLayout.renderBoundsMinXZ()),
            pos.add(HubRenderLayout.renderBoundsMaxXZ(), HubRenderLayout.renderBoundsMaxY(), HubRenderLayout.renderBoundsMaxXZ())
        );
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 16384.0D;
    }

    @Override
    public void onChangeInventory(@NotNull CFNInternalInventory inventory, int slot, @NotNull CFNInventoryChangeOperation operation, @NotNull ItemStack oldStack, @NotNull ItemStack newStack) {
        if (init && world != null && !world.isRemote) {
            HubPluginStateTracker.saveAllPluginData(getNode(), plugins);
            HubPluginStateTracker.savePluginData(getNode(), oldStack);
            HubPluginStateTracker.syncInventoryChange(getNode(), oldStack, newStack);
            notifyPluginInventoryChanged();
        }
        markDirty();
    }

    public void applyPluginSnapshot(ItemStack[] snapshot) {
        for (int i = 0; i < plugins.getSlots(); i++) {
            ItemStack stack = i < snapshot.length ? snapshot[i] : ItemStack.EMPTY;
            plugins.setStackInSlot(i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        initializeHubPluginState();
        markDirty();
    }

    private void initializeHubPluginState() {
        HubPluginStateTracker.initializeFromInventory(getNode(), plugins);
    }

    private void notifyPluginInventoryChanged() {
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
    }
}
