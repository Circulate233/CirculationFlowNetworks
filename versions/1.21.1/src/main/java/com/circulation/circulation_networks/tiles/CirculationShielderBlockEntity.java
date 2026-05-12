package com.circulation.circulation_networks.tiles;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.api.ICirculationShielderBlockEntity;
import com.circulation.circulation_networks.container.ContainerCirculationShielder;
import com.circulation.circulation_networks.handlers.CirculationShielderRenderingHandler;
import com.circulation.circulation_networks.manager.CirculationShielderManager;
import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import com.circulation.circulation_networks.registry.CFNMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CirculationShielderBlockEntity extends BaseCFNBlockEntity implements ICirculationShielderBlockEntity, MenuProvider {

    private static final long ACTIVE_CACHE_INTERVAL_TICKS = 10L;

    private transient final BlockPos.MutableBlockPos min = new BlockPos.MutableBlockPos();
    private transient final BlockPos.MutableBlockPos max = new BlockPos.MutableBlockPos();
    private int scope = 0;
    private boolean redstoneMode = false;
    private boolean showingRange = false;
    private boolean cachedActive;
    private long cachedActiveTick = Long.MIN_VALUE;

    public CirculationShielderBlockEntity(BlockPos pos, BlockState state) {
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
    public void saveAdditional(@NotNull CompoundTag compound, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(compound, registries);
        compound.putInt("scope", this.scope);
        compound.putBoolean("RedstoneMode", this.redstoneMode);
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag compound, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(compound, registries);
        setScope(compound.getInt("scope"));
        this.redstoneMode = compound.getBoolean("RedstoneMode");
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

    private void refreshActiveCache() {
        if (level == null) {
            cachedActive = false;
            cachedActiveTick = Long.MIN_VALUE;
            return;
        }
        boolean oldActive = cachedActive;
        cachedActive = redstoneMode == level.hasNeighborSignal(worldPosition);
        cachedActiveTick = level.getGameTime();
        if (!level.isClientSide && oldActive != cachedActive) {
            CirculationShielderManager.INSTANCE.refreshActiveState(this, level.dimension().location().hashCode());
        }
    }

    public boolean isReceivingRedstoneSignal() {
        if (level == null) {
            return false;
        }
        return level.hasNeighborSignal(worldPosition);
    }

    public boolean getRedstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(boolean mode) {
        this.redstoneMode = mode;
        setChanged();
        refreshActiveCache();
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
            if (level.isClientSide) {
                clientRegister();
            } else {
                CirculationShielderManager.INSTANCE.register(this, level.dimension().location().hashCode());
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
            if (level.isClientSide) {
                clientUnregister();
            } else {
                CirculationShielderManager.INSTANCE.unregister(this, level.dimension().location().hashCode());
            }
        }
        super.setRemoved();
    }

    @OnlyIn(Dist.CLIENT)
    private void clientRegister() {
        CirculationShielderRenderingHandler.INSTANCE.addShielder(this);
    }

    @OnlyIn(Dist.CLIENT)
    private void clientUnregister() {
        CirculationShielderRenderingHandler.INSTANCE.removeShielder(this);
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
