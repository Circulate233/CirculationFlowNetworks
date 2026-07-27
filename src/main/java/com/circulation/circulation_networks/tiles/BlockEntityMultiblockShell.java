package com.circulation.circulation_networks.tiles;

import com.circulation.circulation_networks.registry.CFNBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockEntityMultiblockShell extends BlockEntity implements Nameable {

    @Nullable
    private BlockPos originPos;

    public BlockEntityMultiblockShell(BlockPos pos, BlockState state) {
        super(CFNBlockEntityTypes.MULTIBLOCK_SHELL, pos, state);
    }

    public static <T, C> void registerCapabilityProxy(RegisterCapabilitiesEvent event, BlockCapability<T, C> capability, Block shellBlock) {
        event.registerBlock(capability, (level, pos, state, be, context) -> {
            if (be instanceof BlockEntityMultiblockShell shell && shell.canRedirect()) {
                return level.getCapability(capability, shell.getOriginPos(), context);
            }
            return null;
        }, shellBlock);
    }

    @NotNull
    public BlockPos getOriginPos() {
        return originPos != null ? originPos : worldPosition;
    }

    public void setOriginPos(@NotNull BlockPos pos) {
        this.originPos = pos;
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    public boolean canRedirect() {
        return originPos != null && !originPos.equals(worldPosition);
    }

    @Nullable
    public BlockEntity getOriginBlockEntity() {
        if (canRedirect() && level != null && level.isLoaded(originPos)) {
            return level.getBlockEntity(originPos);
        }
        return null;
    }

    @Override
    protected void saveAdditional(@NotNull ValueOutput output) {
        super.saveAdditional(output);
        if (originPos != null) {
            output.putInt("OriginX", originPos.getX());
            output.putInt("OriginY", originPos.getY());
            output.putInt("OriginZ", originPos.getZ());
        }
    }

    // Nameable

    @Override
    protected void loadAdditional(@NotNull ValueInput input) {
        super.loadAdditional(input);
        if (input.getInt("OriginX").isPresent()) {
            originPos = new BlockPos(
                input.getIntOr("OriginX", 0),
                input.getIntOr("OriginY", 0),
                input.getIntOr("OriginZ", 0)
            );
        } else {
            originPos = null;
        }
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    @NotNull
    public CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    @NotNull
    public Component getName() {
        BlockEntity origin = getOriginBlockEntity();
        if (origin instanceof Nameable nameable) {
            return nameable.getName();
        }
        return Component.translatable("block.circulation_networks.multiblock_shell");
    }

    @Override
    public boolean hasCustomName() {
        BlockEntity origin = getOriginBlockEntity();
        if (origin instanceof Nameable nameable) {
            return nameable.hasCustomName();
        }
        return false;
    }

    @Override
    @Nullable
    public Component getCustomName() {
        BlockEntity origin = getOriginBlockEntity();
        if (origin instanceof Nameable nameable) {
            return nameable.getCustomName();
        }
        return null;
    }

    // Capability proxy — registered statically via RegisterCapabilitiesEvent

    @Override
    @NotNull
    public Component getDisplayName() {
        BlockEntity origin = getOriginBlockEntity();
        if (origin instanceof Nameable nameable) {
            return nameable.getDisplayName();
        }
        return getName();
    }
}
