package com.circulation.circulation_networks.tiles;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
import com.circulation.circulation_networks.container.ContainerCirculationShielder;
import com.circulation.circulation_networks.handlers.CirculationShielderRenderingHandler;
import com.circulation.circulation_networks.manager.CirculationShielderManager;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.CFNMenuTypes;
import com.circulation.circulation_networks.utils.WorldResolveCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockEntityCirculationShielder extends BaseCFNBlockEntity implements ICirculationShielderBlockEntity, MenuProvider {

    private static final long ACTIVE_CACHE_INTERVAL_TICKS = 10L;
    private transient final BlockPos.MutableBlockPos min = new BlockPos.MutableBlockPos();
    private transient final BlockPos.MutableBlockPos max = new BlockPos.MutableBlockPos();
    private int scope = 0;
    private boolean redstoneMode = false;
    private boolean showingRange = false;
    private boolean cachedActive = false;
    private long cachedActiveTick = Long.MIN_VALUE;

    public BlockEntityCirculationShielder(BlockPos pos, BlockState state) {
        super(CFNBlockEntityTypes.CIRCULATION_SHIELDER, pos, state);
        setScope(scope);
    }

    public int getScope() {
        return scope;
    }

    public void setScope(int scope) {
        int maxScope = Math.max(0, getMaxScope());
        int clamped = Math.clamp(scope, 0, maxScope);
        this.min.set(this.getBlockPos().getX() - clamped, this.getBlockPos().getY() - clamped, this.getBlockPos().getZ() - clamped);
        this.max.set(this.getBlockPos().getX() + clamped, this.getBlockPos().getY() + clamped, this.getBlockPos().getZ() + clamped);
        this.scope = clamped;
        if (level != null && !level.isClientSide()) {
            CirculationShielderManager.INSTANCE.refreshActiveState(this, WorldResolveCompat.getDimensionId(level));
        }
    }

    @Override
    public int getMaxScope() {
        return CFNConfig.SHIELDER.maxScope;
    }

    public boolean isShowingRange() {
        return showingRange;
    }

    public void setShowingRange(boolean showingRange) {
        this.showingRange = showingRange;
    }

    @Override
    protected void saveAdditional(@NotNull ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("scope", this.scope);
        output.putBoolean("RedstoneMode", this.redstoneMode);
    }

    @Override
    protected void loadAdditional(@NotNull ValueInput input) {
        super.loadAdditional(input);
        setScope(input.getIntOr("scope", 0));
        this.redstoneMode = input.getBooleanOr("RedstoneMode", false);
        refreshActiveCache();
    }

    @Override
    public boolean checkScope(BlockPos pos) {
        return min.getX() <= pos.getX() && min.getY() <= pos.getY() && min.getZ() <= pos.getZ()
            && max.getX() >= pos.getX() && max.getY() >= pos.getY() && max.getZ() >= pos.getZ();
    }

    @Override
    public boolean isActive() {
        return cachedActive;
    }

    public boolean getRedstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(boolean mode) {
        this.redstoneMode = mode;
        setChanged();
        refreshActiveCache();
    }

    public boolean isReceivingRedstoneSignal() {
        return level != null && level.hasNeighborSignal(this.worldPosition);
    }

    @Override
    public void serverUpdate(Level world, BlockPos pos, BlockState state, BaseCFNBlockEntity blockEntity) {
        long gameTime = world.getGameTime();
        if (cachedActiveTick == Long.MIN_VALUE || gameTime - cachedActiveTick >= ACTIVE_CACHE_INTERVAL_TICKS) {
            refreshActiveCache();
        }
    }

    @Override
    public BlockPos getBEPos() {
        return getBlockPos();
    }

    public void onValidate() {
        if (level != null) {
            refreshActiveCache();
            if (level.isClientSide()) {
                clientRegister();
            } else {
                CirculationShielderManager.INSTANCE.register(this, WorldResolveCompat.getDimensionId(level));
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        onValidate();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        onValidate();
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            if (level.isClientSide()) {
                clientUnregister();
            } else {
                CirculationShielderManager.INSTANCE.unregister(this, WorldResolveCompat.getDimensionId(level));
            }
        }
        super.setRemoved();
    }

    private void clientRegister() {
        CirculationShielderRenderingHandler.INSTANCE.addShielder(this);
    }

    private void clientUnregister() {
        CirculationShielderRenderingHandler.INSTANCE.removeShielder(this);
    }

    private void refreshActiveCache() {
        boolean previousActive = cachedActive;
        boolean cachedPowered;
        if (level == null || level.isClientSide()) {
            cachedActive = false;
            cachedActiveTick = Long.MIN_VALUE;
            return;
        }
        cachedPowered = level.hasNeighborSignal(worldPosition);
        cachedActive = redstoneMode == cachedPowered;
        cachedActiveTick = level.getGameTime();
        if (cachedActive != previousActive) {
            CirculationShielderManager.INSTANCE.refreshActiveState(this, WorldResolveCompat.getDimensionId(level));
        }
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.circulation_networks.circulation_shielder");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new ContainerCirculationShielder(CFNMenuTypes.CIRCULATION_SHIELDER_MENU, containerId, player, this);
    }
}
