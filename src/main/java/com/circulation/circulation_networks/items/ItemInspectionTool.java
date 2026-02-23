package com.circulation.circulation_networks.items;

import com.circulation.circulation_networks.CirculationFlowNetworks;
import com.circulation.circulation_networks.api.node.IChargingNode;
import com.circulation.circulation_networks.api.node.IEnergySupplyNode;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.packets.NodeNetworkRendering;
import com.circulation.circulation_networks.packets.SpoceRendering;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.List;

public class ItemInspectionTool extends BaseItem {

    private static final List<Mode> VALUES = new ObjectArrayList<>();

    public ItemInspectionTool() {
        super("inspection_tool");
    }

    @Override
    public @NotNull EnumActionResult onItemUse(@NotNull EntityPlayer player, @NotNull World worldIn, @NotNull BlockPos pos, @NotNull EnumHand hand, @NotNull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player instanceof EntityPlayerMP p) {
            INode node = NetworkManager.getNodeFromPos(worldIn, pos);
            if (node == null) return EnumActionResult.PASS;
            var mode = getMode(p.getHeldItemMainhand());
            return mode.sendPacket(p, node);
        }
        return super.onItemUse(player, worldIn, pos, hand, facing, hitX, hitY, hitZ);
    }

    public Mode getMode(ItemStack stack) {
        var nbt = stack.getTagCompound();
        if (nbt == null) return Mode.ALL;
        if (!nbt.hasKey("mode")) return Mode.ALL;
        return Mode.getModeFormID(nbt.getInteger("mode"));
    }

    public enum Mode {
        ALL,
        Spoce(((player, node) -> {
            double l = node.getLinkScope();
            double e = 0;
            double c = 0;
            if (node instanceof IEnergySupplyNode n) {
                e = n.getEnergyScope();
            }
            if (node instanceof IChargingNode n) {
                c = n.getChargingScope();
            }
            CirculationFlowNetworks.NET_CHANNEL.sendTo(new SpoceRendering(node.getPos(), l, e, c), player);
            return EnumActionResult.SUCCESS;
        })),
        Link(((player, node) -> {
            CirculationFlowNetworks.NET_CHANNEL.sendTo(new NodeNetworkRendering(player, node.getGrid()), player);
            NodeNetworkRendering.addPlayer(node.getGrid(), player);
            return EnumActionResult.SUCCESS;
        }));

        private final ModeRun run;

        Mode() {
            run = ((player, node) -> {
                for (Mode value : VALUES) {
                    value.sendPacket(player, node);
                }
                return EnumActionResult.SUCCESS;
            });
        }

        Mode(ModeRun run) {
            this.run = run;
            VALUES.add(this);
        }

        public static Mode getModeFormID(int id) {
            return values()[Math.floorMod(id, Mode.values().length)];
        }

        public EnumActionResult sendPacket(EntityPlayerMP player, INode node) {
            return run.send(player, node);
        }

        public boolean isMode(Mode mode) {
            return this == ALL || this.ordinal() == mode.ordinal();
        }

    }

    @FunctionalInterface
    public interface ModeRun {
        @Nonnull
        EnumActionResult send(@Nonnull EntityPlayerMP player, @Nonnull INode node);
    }
}