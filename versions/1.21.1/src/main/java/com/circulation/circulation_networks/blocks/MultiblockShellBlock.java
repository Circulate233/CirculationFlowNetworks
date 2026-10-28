package com.circulation.circulation_networks.blocks;

import it.unimi.dsi.fastutil.objects.ObjectLists;
import com.circulation.circulation_networks.tiles.MultiblockShellBlockEntity;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.function.BiConsumer;

@SuppressWarnings("deprecation")
@ParametersAreNonnullByDefault
public class MultiblockShellBlock extends Block implements EntityBlock {

    public static final IClientBlockExtensions PARTICLE_CLIENT_EXTENSIONS = new IClientBlockExtensions() {
        @Override
        public boolean addHitEffects(BlockState state, Level level, HitResult target,
                                     ParticleEngine manager) {
            return true;
        }

        @Override
        public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos,
                                         ParticleEngine manager) {
            return true;
        }
    };

    public MultiblockShellBlock() {
        super(BlockBehaviour.Properties.of()
                                       .mapColor(MapColor.METAL)
                                       .strength(1.5F, 6.0F)
                                       .noOcclusion()
                                       .pushReaction(PushReaction.BLOCK)
                                       .isViewBlocking((s, l, p) -> false));
    }

    @Nullable
    private static BlockPos getOriginPos(BlockGetter level, BlockPos shellPos) {
        BlockEntity be = level.getBlockEntity(shellPos);
        if (be instanceof MultiblockShellBlockEntity shell && shell.canRedirect()) {
            return shell.getOriginPos();
        }
        return null;
    }

    @NotNull
    public static BlockPos resolveRedirectedPos(BlockGetter level, BlockPos pos) {
        BlockPos originPos = getOriginPos(level, pos);
        return originPos != null ? originPos : pos;
    }

    public static boolean canBeReplacedAt(Level level, BlockPos shellPos) {
        BlockPos origin = getOriginPos(level, shellPos);
        if (origin == null) {
            return false;
        }
        BlockState originState = level.getBlockState(origin);
        return originState.isAir() || originState.getBlock() instanceof MultiblockShellBlock;
    }

    private static @NotNull VoxelShape emptyShape() {
        return Shapes.empty();
    }

    private static @NotNull VoxelShape fullShape() {
        return Shapes.block();
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockShellBlockEntity(pos, state);
    }

    @Override
    @NotNull
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean addLandingEffects(BlockState state, ServerLevel level, BlockPos pos,
                                     BlockState stateAtPos, LivingEntity entity, int numberOfParticles) {
        return true;
    }

    @Override
    public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    @NotNull
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return ObjectLists.emptyList();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    @NotNull
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            BlockHitResult newHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), originPos, hit.isInside());
            return originState.useWithoutItem(level, player, newHit);
        }
        return InteractionResult.PASS;
    }

    @Override
    @NotNull
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
                                              Level level, BlockPos pos,
                                              Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            BlockHitResult newHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), originPos, hit.isInside());
            return originState.useItemOn(stack, level, player, hand, newHit);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos,
                                       Player player, boolean willHarvest, FluidState fluid) {
        if (willHarvest) {
            return true;
        }
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null && !level.isEmptyBlock(originPos)) {
            level.removeBlock(originPos, false);
        }
        return super.onDestroyedByPlayer(state, level, pos, player, false, fluid);
    }

    @Override
    @NotNull
    public BlockState playerWillDestroy(Level level, BlockPos pos,
                                        BlockState state, Player player) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            originState.getBlock().playerWillDestroy(level, originPos, originState, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos,
                              BlockState state, @Nullable BlockEntity be, ItemStack stack) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null && !level.isEmptyBlock(originPos)) {
            BlockState originState = level.getBlockState(originPos);
            BlockEntity originBE = level.getBlockEntity(originPos);
            originState.getBlock().playerDestroy(level, player, originPos, originState, originBE, stack);
        } else {
            super.playerDestroy(level, player, pos, state, be, stack);
        }
        level.removeBlock(pos, false);
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos,
                                   ItemStack stack, boolean dropExperience) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            originState.spawnAfterBreak(level, originPos, stack, dropExperience);
        }
        super.spawnAfterBreak(state, level, pos, stack, dropExperience);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (state.is(newState.getBlock())) return;
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null && !level.isEmptyBlock(originPos)) {
            level.removeBlock(originPos, false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos,
                                  Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null && !level.isEmptyBlock(originPos)) {
            BlockState originState = level.getBlockState(originPos);
            BlockEntity originBE = level.getBlockEntity(originPos);
            Block.dropResources(originState, level, originPos, originBE);
            level.removeBlock(originPos, false);
        }
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    @Override
    @NotNull
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            return originState.getBlock().getCloneItemStack(level, originPos, originState);
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player,
                                       BlockGetter level, BlockPos pos) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            return originState.getDestroyProgress(player, level, originPos);
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level,
                                        BlockPos pos, Explosion explosion) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            return originState.getBlock().getExplosionResistance(originState, level, originPos, explosion);
        }
        return super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            originState.handleNeighborChanged(level, originPos, neighborBlock, neighborPos, isMoving);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            return originState.getAnalogOutputSignal(level, originPos);
        }
        return 0;
    }

    @Override
    @NotNull
    protected VoxelShape getShape(BlockState state, BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return fullShape();
    }

    @Override
    @NotNull
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                           BlockPos pos, CollisionContext context) {
        return fullShape();
    }

    @Override
    @NotNull
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level,
                                        BlockPos pos, CollisionContext context) {
        return emptyShape();
    }

    @Override
    @NotNull
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return emptyShape();
    }

    @Override
    @NotNull
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return fullShape();
    }

    @Override
    @NotNull
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return fullShape();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return canBeReplacedAt(context.getLevel(), context.getClickedPos());
    }

}
