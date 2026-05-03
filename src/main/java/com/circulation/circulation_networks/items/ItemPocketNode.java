package com.circulation.circulation_networks.items;

import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.manager.PocketNodeManager;
import com.circulation.circulation_networks.manager.PocketNodeManager.RegisterPocketNodeResult;
import com.circulation.circulation_networks.registry.NodeTypes;
import com.circulation.circulation_networks.tooltip.LocalizedComponent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ItemPocketNode extends BaseItem {

    private static final String TOOLTIP_ATTACH_ON_BLOCK = "tooltip.circulation_networks.pocket_node_attach_on_block";
    private static final String TOOLTIP_DETACH_WITH_CONFIGURATOR = "tooltip.circulation_networks.pocket_node_detach_with_configurator";
    private static final String TOOLTIP_RESCAN_NEARBY_ENERGY_DEVICES = "tooltip.circulation_networks.node_rescan";
    private final NodeType<?> nodeType;

    public ItemPocketNode(NodeType<?> nodeType, Properties properties) {
        super(properties);
        this.nodeType = nodeType;
    }

    private static void sendFeedback(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key));
    }

    @Override
    protected List<LocalizedComponent> buildTooltips(ItemStack stack) {
        var tooltips = new ObjectArrayList<>(super.buildTooltips(stack));
        tooltips.add(LocalizedComponent.of(TOOLTIP_ATTACH_ON_BLOCK));
        tooltips.add(LocalizedComponent.of(TOOLTIP_DETACH_WITH_CONFIGURATOR));
        if (nodeType == NodeTypes.PORT_NODE) {
            tooltips.add(LocalizedComponent.of(TOOLTIP_RESCAN_NEARBY_ENERGY_DEVICES));
        }
        return tooltips;
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        RegisterPocketNodeResult result = PocketNodeManager.INSTANCE.registerPocketNodeDetailed(
            context.getLevel(),
            context.getClickedPos(),
            nodeType,
            context.getClickedFace(),
            null
        );
        if (!result.isSuccess()) {
            if (result == RegisterPocketNodeResult.OCCUPIED) {
                sendFeedback(serverPlayer, "message.circulation_networks.pocket_node_occupied");
            } else if (result == RegisterPocketNodeResult.HUB_CONFLICT) {
                sendFeedback(serverPlayer, "message.circulation_networks.hub_conflict");
            }
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level worldIn, @NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }
}
