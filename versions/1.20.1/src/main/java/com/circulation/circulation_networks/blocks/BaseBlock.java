package com.circulation.circulation_networks.blocks;

import it.unimi.dsi.fastutil.objects.ObjectLists;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.items.BaseItemTooltipModel;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.registry.CFNItems;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import com.circulation.circulation_networks.utils.CI18n;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("deprecation")
public abstract class BaseBlock extends Block implements EntityBlock {

    private String[] cachedAutoTooltipKeys;

    protected BaseBlock(Properties properties) {
        super(properties);
    }

    protected static BlockBehaviour.Properties metalProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 6.0f);
    }

    protected static BlockBehaviour.Properties metalPropertiesNoOcclusion() {
        return metalProperties().noOcclusion();
    }

    protected List<LocalizedComponent> buildTooltips(ItemStack stack) {
        if (cachedAutoTooltipKeys == null) {
            cachedAutoTooltipKeys = BaseItemTooltipModel.resolveTooltipKeys(getDescriptionId(), CI18n::hasKey);
        }
        List<LocalizedComponent> result = new ObjectArrayList<>();
        for (var key : cachedAutoTooltipKeys) {
            result.add(LocalizedComponent.of(key));
        }
        return result;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        for (var lc : buildTooltips(stack)) {
            tooltip.add(Component.literal(lc.get()));
        }
    }

    public abstract boolean hasGui();

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder builder) {
        return ObjectLists.singleton(new ItemStack(this));
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        if (!hasGui()) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MenuProvider menuProvider) {
            return menuProvider;
        }
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND
            && !player.isShiftKeyDown()
            && player.getItemInHand(hand).getItem() == CFNItems.circulationConfigurator
        ) {
            if (level.isClientSide()) {
                if (API.getNodeAt(level, pos) != null) {
                    return InteractionResult.sidedSuccess(true);
                }
            } else if (PocketNodeManager.INSTANCE.removePocketNode(level, pos, true)) {
                return InteractionResult.sidedSuccess(false);
            }
        }
        if (!hasGui() || hand != InteractionHand.MAIN_HAND) {
            return super.use(state, level, pos, player, hand, hit);
        }
        MenuProvider menuProvider = state.getMenuProvider(level, pos);
        if (menuProvider == null) {
            return super.use(state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, menuProvider, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return null;
    }
}
