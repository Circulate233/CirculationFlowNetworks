package com.circulation.circulation_networks.blocks;

import com.circulation.circulation_networks.tiles.BlockEntityMultiblockShell;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.redstone.Orientation;
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
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

@ParametersAreNonnullByDefault
public class MultiblockShellBlock extends Block implements EntityBlock {

    public static final IClientBlockExtensions PARTICLE_CLIENT_EXTENSIONS = new IClientBlockExtensions() {
        @Override
        public boolean addHitEffects(BlockState state, Level level, @Nullable HitResult target,
                                     ParticleEngine manager) {
            return true;
        }

        @Override
        public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos,
                                         ParticleEngine manager) {
            return true;
        }
    };
    private static final LongSet ORIGIN_REMOVAL_GUARD = new LongOpenHashSet();

    public MultiblockShellBlock(ResourceKey<Block> id) {
        super(BlockBehaviour.Properties.of()
                                       .setId(id)
                                       .mapColor(MapColor.METAL)
                                       .strength(1.5F, 6.0F)
                                       .noOcclusion()
                                       .pushReaction(PushReaction.BLOCK)
                                       .isViewBlocking((s, l, p) -> false));
    }

    @Nullable
    private static BlockPos getOriginPos(BlockGetter level, BlockPos shellPos) {
        BlockEntity be = level.getBlockEntity(shellPos);
        if (be instanceof BlockEntityMultiblockShell shell && shell.canRedirect()) {
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

    public static boolean isOriginRemovalGuarded(BlockPos originPos) {
        synchronized (ORIGIN_REMOVAL_GUARD) {
            return ORIGIN_REMOVAL_GUARD.contains(originPos.asLong());
        }
    }

    private static boolean enterOriginRemovalGuard(BlockPos originPos) {
        synchronized (ORIGIN_REMOVAL_GUARD) {
            return ORIGIN_REMOVAL_GUARD.add(originPos.asLong());
        }
    }

    private static void exitOriginRemovalGuard(BlockPos originPos) {
        synchronized (ORIGIN_REMOVAL_GUARD) {
            ORIGIN_REMOVAL_GUARD.remove(originPos.asLong());
        }
    }

    private static void redirectShellRemovalToOrigin(Level level, BlockPos shellPos) {
        if (level.isClientSide()) {
            return;
        }
        BlockPos originPos = getOriginPos(level, shellPos);
        if (originPos == null || isOriginRemovalGuarded(originPos)) {
            return;
        }
        if (!level.isLoaded(originPos) || level.getBlockState(originPos).isAir()) {
            return;
        }
        if (!enterOriginRemovalGuard(originPos)) {
            return;
        }
        try {
            level.destroyBlock(originPos, true, null);
        } finally {
            exitOriginRemovalGuard(originPos);
        }
    }

    private static boolean destroyOriginByPlayer(Level level, BlockPos originPos, Player player,
                                                 ItemStack toolStack, boolean willHarvest) {
        BlockState originState = level.getBlockState(originPos);
        if (originState.isAir()) {
            return false;
        }

        BlockEntity originBlockEntity = originState.hasBlockEntity() ? level.getBlockEntity(originPos) : null;
        Block originBlock = originState.getBlock();
        BlockState adjustedState = originBlock.playerWillDestroy(level, originPos, originState, player);
        boolean removed = adjustedState.onDestroyedByPlayer(level, originPos, player, toolStack,
            willHarvest, level.getFluidState(originPos));
        if (!removed) {
            return false;
        }

        adjustedState.getBlock().destroy(level, originPos, adjustedState);
        if (willHarvest) {
            originBlock.playerDestroy(level, player, originPos, adjustedState, originBlockEntity, toolStack);
        }
        return true;
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
        return new BlockEntityMultiblockShell(pos, state);
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
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    @NotNull
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.emptyList();
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state,
                                          Level level, BlockPos pos,
                                          Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            BlockHitResult newHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), originPos, hit.isInside());
            return originState.useItemOn(stack, level, player, hand, newHit);
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos,
                                       Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos == null) {
            return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
        }
        if (level.isClientSide()) {
            return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
        }
        if (isOriginRemovalGuarded(originPos)) {
            return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
        }
        if (!enterOriginRemovalGuard(originPos)) {
            return false;
        }
        try {
            return destroyOriginByPlayer(level, originPos, player, toolStack, willHarvest);
        } finally {
            exitOriginRemovalGuard(originPos);
        }
    }

    @Override
    @NotNull
    public BlockState playerWillDestroy(Level level, BlockPos pos,
                                        BlockState state, Player player) {
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos,
                              BlockState state, @Nullable BlockEntity be, ItemStack stack) {
        super.playerDestroy(level, player, pos, state, be, stack);
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
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos,
                                  Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    @Override
    @NotNull
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            return originState.getCloneItemStack(level, originPos, includeData);
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
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        redirectShellRemovalToOrigin(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, @Nullable Orientation orientation, boolean isMoving) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            originState.handleNeighborChanged(level, originPos, neighborBlock, orientation, isMoving);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        BlockPos originPos = getOriginPos(level, pos);
        if (originPos != null) {
            BlockState originState = level.getBlockState(originPos);
            return originState.getAnalogOutputSignal(level, originPos, direction);
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
